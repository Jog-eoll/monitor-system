package com.monitorplatform.content.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.entity.dto.AlarmReceiveRequestDTO;
import com.monitorplatform.content.entity.dto.ContentReceiveDTO;
import com.monitorplatform.content.entity.dto.InfoBoardBatchQueryDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import com.monitorplatform.content.entity.dto.RecognitionResultDTO;
import com.monitorplatform.content.entity.vo.AlarmRecordVO;
import com.monitorplatform.content.entity.vo.BoardMonitorCardVO;
import com.monitorplatform.content.entity.vo.BoardMonitorPageVO;
import com.monitorplatform.content.entity.vo.BoardGatewayDataVO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import com.monitorplatform.content.entity.vo.ContentSummaryVO;
import com.monitorplatform.content.entity.vo.DeviceInfoVO;
import com.monitorplatform.content.entity.vo.DevicePageDataVO;
import com.monitorplatform.content.entity.vo.ScreenStatusVO;
import com.monitorplatform.content.feign.AlarmFeignClient;
import com.monitorplatform.content.feign.DeviceFeignClient;
import com.monitorplatform.content.feign.ForwardFeignClient;
import com.monitorplatform.content.mapper.ContentMonitorMapper;
import com.monitorplatform.content.service.ContentMonitorService;
import com.monitorplatform.content.service.ContentRecognitionService;
import com.monitorplatform.content.service.LocalAuditService;
import com.monitorplatform.content.service.QwenApiService;
import com.monitorplatform.common.websocket.WebSocketPushConstants;
import com.monitorplatform.common.websocket.WebSocketPushPublisher;
import com.monitorplatform.common.log.OperateLogWriter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ContentMonitorServiceImpl implements ContentMonitorService {

    @Resource
    private ContentMonitorMapper contentMonitorMapper;

    @Resource
    private ContentRecognitionService recognitionService;

    @Resource
    private LocalAuditService localAuditService;

    @Resource
    private QwenApiService qwenApiService;

    @Resource
    private DeviceFeignClient deviceFeignClient;

    @Resource
    private AlarmFeignClient alarmFeignClient;

    @Resource
    private ForwardFeignClient forwardFeignClient;

    @Resource
    @Lazy
    private ContentMonitorServiceImpl selfProxy;

    @Resource
    private WebSocketPushPublisher webSocketPushPublisher;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private OperateLogWriter operateLogWriter;

    private static final String SCREEN_STATUS_KEY_PREFIX = "screen:status:";
    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${minio.bucket:monitor-content}")
    private String minioBucket;

    @Value("${monitor.gateway.url:http://192.168.1.25:9520}")
    private String gatewayUrl;

    @Value("${audit.mode:qwen}")
    private String auditMode;

    @Override
    public ContentMonitor receiveContent(ContentReceiveDTO dto) {
        log.info("receive content, contentId={}, type={}, gateway={}",
                dto.getContentId(), dto.getContentType(), dto.getGatewayId());

        String contentType = dto.getContentType();
        if (!"image".equals(contentType) && !"text".equals(contentType) && !"video".equals(contentType)) {
            log.warn("ignore unsupported content type, contentId={}, type={}", dto.getContentId(), contentType);
            ContentMonitor dummy = new ContentMonitor();
            dummy.setContentId(dto.getContentId());
            dummy.setContentType(contentType);
            dummy.setStatus("ignored");
            return dummy;
        }

        ContentMonitor record = new ContentMonitor();
        record.setContentId(dto.getContentId());
        record.setGatewayId(dto.getGatewayId());
        record.setChainId(dto.getChainId());
        record.setContentType(dto.getContentType());
        if (dto.getMinioPath() != null && !dto.getMinioPath().isEmpty()) {
            record.setMinioPath(dto.getMinioPath());
            record.setData(null);
        } else {
            record.setData(dto.getData());
        }
        record.setFileName(dto.getFileName());
        record.setDescription(dto.getDescription());
        record.setThumbnail(dto.getThumbnail());
        record.setSourceIp(dto.getSourceIp());
        record.setBoardIp(dto.getBoardIp());
        record.setBoardPort(dto.getBoardPort());
        record.setPlayBatchId(normalizeBlank(dto.getPlayBatchId()));
        record.setPlayBatchSeq(dto.getPlayBatchSeq());
        record.setPlayBatchSize(dto.getPlayBatchSize());
        record.setStatus("pending");
        record.setIsViolation(0);
        record.setReceiveTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        contentMonitorMapper.insert(record);
        selfProxy.asyncRecognize(record);
        writePublishOperationLog(record, true);
        return record;
    }

    @Async
    public void asyncRecognize(ContentMonitor record) {
        try {
            log.info("async recognize start, contentId={}, type={}", record.getContentId(), record.getContentType());

            if ("video".equals(record.getContentType())) {
                handleVideoRecognition(record);
                return;
            }

            RecognitionResultDTO result = recognitionService.recognize(
                    record.getContentId(),
                    record.getContentType(),
                    record.getData()
            );

            updateRecognitionResult(record.getContentId(), result);
            if (Boolean.TRUE.equals(result.getIsViolation())) {
                pushAlarm(record, result);
            }
        } catch (Exception e) {
            log.error("async recognize failed, contentId={}", record.getContentId(), e);
        }
    }

    private void handleVideoRecognition(ContentMonitor record) {
        try {
            QwenDetectionRequestDTO videoRequest = new QwenDetectionRequestDTO();
            videoRequest.setBusinessId(record.getContentId());
            videoRequest.setDeviceId(record.getGatewayId());
            videoRequest.setMinioPath(record.getMinioPath());
            videoRequest.setContentType("video");
            videoRequest.setFileName(record.getFileName());

            QwenDetectionResultDTO apiResult;
            if ("local".equalsIgnoreCase(auditMode)) {
                apiResult = localAuditService.auditVideo(videoRequest);
            } else {
                apiResult = qwenApiService.detectContent(videoRequest);
            }

            RecognitionResultDTO result = new RecognitionResultDTO();
            result.setContentId(record.getContentId());
            if (apiResult == null) {
                result.setIsViolation(null);
                result.setViolationType("other");
                result.setConfidence(0.0);
            } else {
                result.setIsViolation(toRecognitionViolation(apiResult.getDetectionResult()));
                result.setViolationType(apiResult.getViolationType());
                result.setAuditResult(apiResult.getAuditResult());
                result.setViolationLevel(apiResult.getViolationLevel());
                result.setConfidence(apiResult.getConfidence() != null ? apiResult.getConfidence() / 100.0 : 0.0);
            }

            updateRecognitionResult(record.getContentId(), result);
            if (Boolean.TRUE.equals(result.getIsViolation())) {
                pushAlarm(record, result);
            }
        } catch (Exception e) {
            log.error("video recognize failed, contentId={}", record.getContentId(), e);
        }
    }

    private Boolean toRecognitionViolation(String detectionResult) {
        if ("violation".equals(detectionResult)) {
            return true;
        }
        if ("pending".equals(detectionResult)) {
            return null;
        }
        return false;
    }

    private void pushAlarm(ContentMonitor record, RecognitionResultDTO result) {
        AlarmPushVO pushVO = new AlarmPushVO();
        pushVO.setType("VIOLATION_ALARM");
        pushVO.setContentId(record.getContentId());
        pushVO.setContentType(record.getContentType());
        pushVO.setGatewayId(record.getGatewayId());
        pushVO.setViolationType(result.getViolationType());
        pushVO.setKeywords(result.getKeywords());
        pushVO.setConfidence(result.getConfidence());
        pushVO.setThumbnail(record.getThumbnail());
        pushVO.setTime(System.currentTimeMillis());

        webSocketPushPublisher.publish(
                WebSocketPushConstants.TOPIC_ALARM,
                WebSocketPushConstants.TYPE_VIOLATION_ALARM,
                JSON.toJSONString(pushVO));
        createAlarmRecord(record, result);
    }

    private void createAlarmRecord(ContentMonitor record, RecognitionResultDTO result) {
        try {
            String keywordText = result.getKeywords() != null ? String.join(",", result.getKeywords()) : "";
            AlarmReceiveRequestDTO req = new AlarmReceiveRequestDTO();
            req.setAlarmType("内容违规");
            req.setAlarmLevel(calculateAlarmLevel(result));
            req.setChainId(record.getChainId());
            req.setBoardIp(record.getBoardIp());
            req.setBoardPort(record.getBoardPort());
            req.setDeviceId(record.getBoardIp() != null && !record.getBoardIp().isEmpty() ? record.getBoardIp() : record.getGatewayId());
            req.setDeviceName(record.getBoardIp() != null && !record.getBoardIp().isEmpty()
                    ? ("情报板-" + record.getBoardIp())
                    : ("网关-" + record.getGatewayId()));
            req.setContentId(record.getContentId());
            req.setViolationType(toChineseViolationType(result.getViolationType()));
            req.setViolationDetail("检测到敏感词: " + keywordText + ", 置信度: " + result.getConfidence());
            req.setAlarmTime(LocalDateTime.now().toString());

            CommonServiceResponseVO<Object> resp = alarmFeignClient.receiveAlarm(req);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200) {
                log.warn("create alarm record returned non-200, contentId={}, code={}",
                        record.getContentId(), resp == null ? null : resp.getCode());
            }
        } catch (Exception e) {
            log.error("create alarm record failed, contentId={}", record.getContentId(), e);
        }
    }

    private String calculateAlarmLevel(RecognitionResultDTO result) {
        String mappedLevel = mapLocalViolationLevel(result.getViolationLevel());
        if (mappedLevel != null) {
            return mappedLevel;
        }
        return result.getConfidence() != null && result.getConfidence() >= 0.9 ? "serious" : "general";
    }

    private String mapLocalViolationLevel(String violationLevel) {
        if (violationLevel == null || violationLevel.trim().isEmpty() || "无".equals(violationLevel.trim())) {
            return null;
        }
        switch (violationLevel.trim()) {
            case "高":
                return "critical";
            case "中":
                return "serious";
            case "低":
                return "general";
            default:
                return null;
        }
    }

    @Override
    public ContentMonitor getByContentId(String contentId) {
        return contentMonitorMapper.selectByContentId(contentId);
    }

    @Override
    public List<ContentMonitor> getRecentList(int limit, int offset) {
        return contentMonitorMapper.selectRecentWithPage(limit, offset);
    }

    @Override
    public int getTotalCount() {
        return contentMonitorMapper.selectTotalCount();
    }

    @Override
    public List<ContentMonitor> getViolationList(int limit) {
        return contentMonitorMapper.selectViolations(limit);
    }

    @Override
    public void stopDisplay(String contentId, String operator) {
        contentMonitorMapper.updateStatus(contentId, "stopped", operator);
        sendControlCommand(contentId, "STOP_DISPLAY", operator);
    }

    @Override
    public void resumeDisplay(String contentId, String operator) {
        ContentMonitor record = contentMonitorMapper.selectByContentId(contentId);
        String newStatus = (record != null && Integer.valueOf(1).equals(record.getIsViolation())) ? "violation" : "normal";
        contentMonitorMapper.updateStatus(contentId, newStatus, operator);
        sendControlCommand(contentId, "RESUME_DISPLAY", operator);
    }

    private void sendControlCommand(String contentId, String action, String operator) {
        try {
            GatewayControlCommandDTO req = new GatewayControlCommandDTO();
            req.setContentId(contentId);
            req.setAction(action);
            req.setOperator(operator);

            HttpResponse response = HttpRequest.post(gatewayUrl + "/content/control")
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(req))
                    .timeout(10000)
                    .execute();
            log.info("send control command done, contentId={}, action={}, status={}",
                    contentId, action, response.getStatus());
        } catch (Exception e) {
            log.error("send control command failed, contentId={}, action={}", contentId, action, e);
        }
    }

    @Override
    public void updateRecognitionResult(String contentId, RecognitionResultDTO result) {
        if (result == null) {
            result = new RecognitionResultDTO();
            result.setContentId(contentId);
            result.setIsViolation(null);
            result.setConfidence(0.0);
        }
        Boolean violation = result.getIsViolation();
        Integer isViolation = null;
        String status = "pending";
        if (Boolean.TRUE.equals(violation)) {
            isViolation = 1;
            status = "violation";
        } else if (Boolean.FALSE.equals(violation)) {
            isViolation = 0;
            status = "normal";
        }
        String keywords = result.getKeywords() != null ? String.join(",", result.getKeywords()) : null;
        contentMonitorMapper.updateRecognitionResult(
                contentId,
                isViolation,
                result.getViolationType(),
                result.getConfidence(),
                keywords,
                status
        );
    }

    @Override
    public void deleteContent(String contentId) {
        ContentMonitor record = contentMonitorMapper.selectByContentId(contentId);
        if (record != null && record.getId() != null) {
            contentMonitorMapper.deleteById(record.getId());
        }
    }

    @Override
    public int deleteBatch(List<String> contentIds) {
        int count = 0;
        if (contentIds == null) {
            return 0;
        }
        for (String contentId : contentIds) {
            ContentMonitor record = contentMonitorMapper.selectByContentId(contentId);
            if (record != null && record.getId() != null) {
                contentMonitorMapper.deleteById(record.getId());
                count++;
            }
        }
        return count;
    }

    @Override
    public int clearAll() {
        Long total = contentMonitorMapper.selectCount(null);
        contentMonitorMapper.delete(null);
        return total == null ? 0 : total.intValue();
    }

    @Override
    public int deleteByCondition(String status, Integer isViolation) {
        QueryWrapper<ContentMonitor> wrapper = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (isViolation != null) {
            wrapper.eq("is_violation", isViolation);
        }
        Long count = contentMonitorMapper.selectCount(wrapper);
        contentMonitorMapper.delete(wrapper);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public BoardMonitorPageVO getBoardMonitorList(int pageNum, int pageSize, String ipKeyword) {
        return getBoardMonitorList(pageNum, pageSize, ipKeyword, null);
    }

    @Override
    public BoardMonitorPageVO getBoardMonitorList(int pageNum, int pageSize, String ipKeyword, List<String> cardIdList) {
        List<String> cardIds = normalizeCardIdList(cardIdList);
        if (!cardIds.isEmpty()) {
            return getBoardMonitorListByCardIds(cardIds);
        }

        DevicePageDataVO devicePage = fetchInfoBoardsFromDevice(pageNum, pageSize, ipKeyword);
        List<DeviceInfoVO> boards = devicePage.getRecords();
        if (boards == null || boards.isEmpty()) {
            BoardMonitorPageVO empty = new BoardMonitorPageVO();
            empty.setRecords(Collections.emptyList());
            empty.setTotal(0L);
            empty.setCurrent(pageNum);
            empty.setSize(pageSize);
            empty.setPages(0);
            return empty;
        }

        Map<String, AlarmRecordVO> alarmByContentId = buildAlarmByContentId(fetchPendingAlarms());
        List<BoardMonitorCardVO> cards = buildBoardMonitorCards(boards, alarmByContentId);

        BoardMonitorPageVO pageVO = new BoardMonitorPageVO();
        pageVO.setRecords(cards);
        pageVO.setTotal(devicePage.getTotal() == null ? 0L : devicePage.getTotal());
        pageVO.setCurrent(pageNum);
        pageVO.setSize(pageSize);
        long total = pageVO.getTotal() == null ? 0L : pageVO.getTotal();
        pageVO.setPages(pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize));
        return pageVO;
    }

    private BoardMonitorPageVO getBoardMonitorListByCardIds(List<String> cardIds) {
        List<DeviceInfoVO> boards = fetchInfoBoardsByCardIds(cardIds);
        Map<String, AlarmRecordVO> alarmByContentId = buildAlarmByContentId(fetchPendingAlarms());
        List<BoardMonitorCardVO> cards = buildBoardMonitorCards(boards, alarmByContentId);

        BoardMonitorPageVO pageVO = new BoardMonitorPageVO();
        pageVO.setRecords(cards);
        long total = cards.size();
        pageVO.setTotal(total);
        pageVO.setCurrent(1);
        pageVO.setSize((int) total);
        pageVO.setPages(total > 0 ? 1 : 0);
        return pageVO;
    }

    @Override
    public ContentSummaryVO getLatestContentByBoard(String boardIp, Integer boardPort) {
        if (boardIp == null || boardIp.trim().isEmpty()) {
            return null;
        }
        try {
            ContentMonitor latest = null;
            if (boardPort != null) {
                latest = contentMonitorMapper.selectLatestByBoard(boardIp, boardPort);
            }
            if (latest == null) {
                latest = contentMonitorMapper.selectLatestByBoardIp(boardIp);
            }
            if (latest == null) {
                Long chainId = fetchChainIdByBoardIp(boardIp);
                if (chainId != null) {
                    latest = contentMonitorMapper.selectLatestByChainId(chainId);
                }
            }
            return buildContentSummary(latest);
        } catch (Exception e) {
            log.warn("get latest content by board failed, boardIp={}, boardPort={}", boardIp, boardPort, e);
            return null;
        }
    }

    private DevicePageDataVO fetchInfoBoardsFromDevice(int pageNum, int pageSize, String ipKeyword) {
        try {
            CommonServiceResponseVO<DevicePageDataVO> resp =
                    deviceFeignClient.pageInfoBoards("info_board", pageNum, pageSize, ipKeyword);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
                return emptyDevicePage(pageNum, pageSize);
            }
            DevicePageDataVO data = resp.getData();
            if (data.getRecords() == null) {
                data.setRecords(Collections.emptyList());
            }
            if (data.getTotal() == null) {
                data.setTotal(0L);
            }
            return data;
        } catch (Exception e) {
            log.error("fetch info boards failed", e);
            return emptyDevicePage(pageNum, pageSize);
        }
    }

    private List<AlarmRecordVO> fetchPendingAlarms() {
        try {
            CommonServiceResponseVO<List<AlarmRecordVO>> resp = alarmFeignClient.getPendingList(200);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
                return Collections.emptyList();
            }
            return resp.getData();
        } catch (Exception e) {
            log.error("fetch pending alarms failed", e);
            return Collections.emptyList();
        }
    }

    private List<String> normalizeCardIdList(List<String> cardIdList) {
        if (cardIdList == null || cardIdList.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> cardIds = new LinkedHashSet<>();
        for (String cardId : cardIdList) {
            String normalized = normalizeBlank(cardId);
            if (normalized != null) {
                cardIds.add(normalized);
            }
        }
        return new ArrayList<>(cardIds);
    }

    private List<DeviceInfoVO> fetchInfoBoardsByCardIds(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            InfoBoardBatchQueryDTO request = new InfoBoardBatchQueryDTO();
            request.setDeviceIds(cardIds);
            CommonServiceResponseVO<List<DeviceInfoVO>> resp = deviceFeignClient.listInfoBoardsByDeviceIds(request);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
                return Collections.emptyList();
            }

            Map<String, DeviceInfoVO> boardByCardId = new LinkedHashMap<>();
            for (DeviceInfoVO board : resp.getData()) {
                if (board == null) {
                    continue;
                }
                String cardId = normalizeBlank(board.getDeviceId());
                if (cardId != null) {
                    boardByCardId.putIfAbsent(cardId, board);
                }
            }

            List<DeviceInfoVO> orderedBoards = new ArrayList<>();
            for (String cardId : cardIds) {
                DeviceInfoVO board = boardByCardId.get(cardId);
                if (board != null) {
                    orderedBoards.add(board);
                }
            }
            return orderedBoards;
        } catch (Exception e) {
            log.error("fetch info boards by card ids failed, cardIds={}", cardIds, e);
            return Collections.emptyList();
        }
    }

    private Map<String, AlarmRecordVO> buildAlarmByContentId(List<AlarmRecordVO> pendingAlarms) {
        Map<String, AlarmRecordVO> alarmByContentId = new HashMap<>();
        if (pendingAlarms == null || pendingAlarms.isEmpty()) {
            return alarmByContentId;
        }
        for (AlarmRecordVO alarm : pendingAlarms) {
            if (alarm == null) {
                continue;
            }
            String alarmContentId = normalizeContentId(alarm.getContentId());
            if (alarmContentId != null) {
                alarmByContentId.putIfAbsent(alarmContentId, alarm);
            }
        }
        return alarmByContentId;
    }

    private List<BoardMonitorCardVO> buildBoardMonitorCards(List<DeviceInfoVO> boards,
                                                            Map<String, AlarmRecordVO> alarmByContentId) {
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }
        List<BoardMonitorCardVO> cards = new ArrayList<>();
        for (DeviceInfoVO board : boards) {
            BoardMonitorCardVO card = buildBoardMonitorCard(board, alarmByContentId);
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    private BoardMonitorCardVO buildBoardMonitorCard(DeviceInfoVO board,
                                                     Map<String, AlarmRecordVO> alarmByContentId) {
        if (board == null) {
            return null;
        }
        BoardMonitorCardVO card = new BoardMonitorCardVO();
        card.setCardId(board.getDeviceId());
        card.setDeviceInfo(board);

        String boardIp = board.getIpAddress();
        Integer boardPort = board.getPort();
        Long chainId = fetchChainIdByBoardIp(boardIp);
        List<ContentMonitor> latestContents = new ArrayList<>();

        ContentMonitor latest = null;
        if (boardIp != null && boardPort != null) {
            latest = contentMonitorMapper.selectLatestByBoard(boardIp, boardPort);
        }
        if (latest != null) {
            latestContents = selectDisplayContents(latest);
            chainId = firstNotNull(chainId, latest.getChainId());
        } else {
            if (chainId != null) {
                latest = contentMonitorMapper.selectLatestByChainId(chainId);
                if (latest != null) {
                    latestContents = selectDisplayContents(latest);
                }
            } else if (boardIp != null) {
                latest = contentMonitorMapper.selectLatestByBoardIp(boardIp);
                if (latest != null) {
                    latestContents = selectDisplayContents(latest);
                    chainId = firstNotNull(chainId, latest.getChainId());
                }
            }
        }

        AlarmRecordVO activeAlarm = null;
        String latestContentId = latest == null ? null : normalizeContentId(latest.getContentId());
        if (latestContentId != null && alarmByContentId != null) {
            activeAlarm = alarmByContentId.get(latestContentId);
        }

        List<ContentSummaryVO> contentSummaryList = new ArrayList<>();
        for (ContentMonitor cm : latestContents) {
            ContentSummaryVO summary = buildContentSummary(cm);
            if (summary != null) {
                contentSummaryList.add(summary);
            }
        }

        card.setLatestContents(contentSummaryList.isEmpty() ? null : contentSummaryList);
        card.setActiveAlarm(activeAlarm);
        card.setChainId(chainId);
        card.setScreenStatus(getScreenStatus(boardIp));
        return card;
    }

    private String normalizeContentId(String contentId) {
        if (contentId == null) {
            return null;
        }
        String value = contentId.trim();
        return value.isEmpty() ? null : value;
    }

    private List<ContentMonitor> selectDisplayContents(ContentMonitor latest) {
        if (latest == null) {
            return Collections.emptyList();
        }
        String playBatchId = normalizeBlank(latest.getPlayBatchId());
        if (playBatchId == null) {
            List<ContentMonitor> single = new ArrayList<>(1);
            single.add(latest);
            return single;
        }
        List<ContentMonitor> batchContents = contentMonitorMapper.selectBatchByPlayBatchId(playBatchId);
        if (batchContents == null || batchContents.isEmpty()) {
            List<ContentMonitor> single = new ArrayList<>(1);
            single.add(latest);
            return single;
        }
        return batchContents;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long firstNotNull(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
    }

    private ScreenStatusVO getScreenStatus(String boardIp) {
        if (boardIp == null || boardIp.isEmpty()) {
            return buildDefaultScreenStatus();
        }
        try {
            String val = stringRedisTemplate.opsForValue().get(SCREEN_STATUS_KEY_PREFIX + boardIp);
            if (val == null || val.isEmpty()) {
                return buildDefaultScreenStatus();
            }
            ScreenStatusVO status = JSON.parseObject(val, ScreenStatusVO.class);
            if (status == null || status.getStatus() == null) {
                return buildDefaultScreenStatus();
            }
            return status;
        } catch (Exception e) {
            log.warn("read screen status failed, boardIp={}", boardIp, e);
            return buildDefaultScreenStatus();
        }
    }

    private ScreenStatusVO buildDefaultScreenStatus() {
        ScreenStatusVO status = new ScreenStatusVO();
        status.setStatus("NORMAL");
        return status;
    }

    private Long fetchChainIdByBoardIp(String boardIp) {
        String queryBoardIp = normalizeBlank(boardIp);
        if (queryBoardIp == null) {
            return null;
        }
        try {
            CommonServiceResponseVO<BoardGatewayDataVO> resp = forwardFeignClient.getGatewayByBoardIp(queryBoardIp);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
                return null;
            }
            return resp.getData().getChainId();
        } catch (Exception e) {
            log.warn("fetch chain id by board ip failed, boardIp={}", queryBoardIp, e);
            return null;
        }
    }

    private ContentSummaryVO buildContentSummary(ContentMonitor content) {
        if (content == null) {
            return null;
        }
        ContentSummaryVO summary = new ContentSummaryVO();
        summary.setContentId(content.getContentId());
        summary.setContentType(content.getContentType());
        summary.setStatus(content.getStatus());
        summary.setIsViolation(content.getIsViolation());
        summary.setViolationType(toChineseViolationType(content.getViolationType()));
        summary.setDescription(content.getDescription());
        summary.setReceiveTime(content.getReceiveTime());
        summary.setFileName(content.getFileName());
        summary.setPlayBatchId(content.getPlayBatchId());
        summary.setPlayBatchSeq(content.getPlayBatchSeq());
        summary.setPlayBatchSize(content.getPlayBatchSize());

        if ("image".equals(content.getContentType()) || "video".equals(content.getContentType())) {
            summary.setMinioPath(content.getMinioPath());
            String fileUrl = null;
            if (content.getMinioPath() != null && !content.getMinioPath().isEmpty()) {
                fileUrl = minioEndpoint + "/" + minioBucket + "/" + content.getMinioPath();
            }
            summary.setFileUrl(fileUrl);
            summary.setThumbnail(content.getThumbnail() != null ? content.getThumbnail() : content.getData());
        } else {
            String data = content.getData();
            summary.setTextPreview(data != null && data.length() > 200 ? data.substring(0, 200) + "..." : data);
        }
        return summary;
    }

    private String toChineseViolationType(String violationType) {
        if (violationType == null || "none".equals(violationType)) {
            return "无";
        }
        switch (violationType) {
            case "pornography":
                return "色情内容";
            case "violence":
                return "暴力内容";
            case "sensitive":
                return "敏感信息";
            case "other":
                return "其他违规";
            default:
                return violationType;
        }
    }

    private DevicePageDataVO emptyDevicePage(int pageNum, int pageSize) {
        DevicePageDataVO page = new DevicePageDataVO();
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        page.setCurrent((long) pageNum);
        page.setSize((long) pageSize);
        page.setPages(0L);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> deleteViolationContentByBoard(String boardIp, Integer boardPort) {
        List<String> fileNames = contentMonitorMapper.selectViolationFileNamesByBoard(boardIp, boardPort);
        contentMonitorMapper.deleteViolationByBoard(boardIp, boardPort);
        return fileNames;
    }

    @Override
    public void insertDefaultImageRecord(String boardIp, Integer boardPort, String minioPath, String operator) {
        try {
            ContentMonitor record = new ContentMonitor();
            record.setContentId("DEFAULT-" + boardIp.replace(".", "") + "-" + System.currentTimeMillis());
            record.setBoardIp(boardIp);
            record.setBoardPort(boardPort);
            record.setContentType("image");
            record.setFileName("default.jpg");
            record.setMinioPath(minioPath);
            record.setDescription("默认底图 [" + boardIp + ":" + boardPort + "] 内容恢复后自动写入");
            record.setStatus("normal");
            record.setIsViolation(0);
            record.setHandleBy(operator);
            record.setHandleTime(LocalDateTime.now());
            record.setReceiveTime(LocalDateTime.now());
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            contentMonitorMapper.insert(record);
        } catch (Exception e) {
            log.warn("insert default image record failed, boardIp={}, boardPort={}", boardIp, boardPort, e);
        }
    }

    @Data
    private static class GatewayControlCommandDTO {
        private String contentId;
        private String action;
        private String operator;
    }

    @Data
    private static class AlarmPushVO {
        private String type;
        private String contentId;
        private String contentType;
        private String gatewayId;
        private String violationType;
        private List<String> keywords;
        private Double confidence;
        private String thumbnail;
        private Long time;
    }

    private void writePublishOperationLog(ContentMonitor record, boolean success) {
        if (operateLogWriter == null || record == null) {
            return;
        }
        try {
            operateLogWriter.writePublishContent(
                    record.getGatewayId(),
                    record.getSourceIp(),
                    record.getBoardIp(),
                    record.getBoardPort(),
                    record.getContentId(),
                    record.getFileName(),
                    record.getChainId(),
                    success,
                    null);
        } catch (Exception e) {
            log.warn("[publish-log] write operation log failed, contentId={}, error={}",
                    record.getContentId(), e.getMessage());
        }
    }
}
