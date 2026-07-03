package com.monitorplatform.alarm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.entity.dto.*;
import com.monitorplatform.alarm.entity.vo.AlarmTodayStatsVO;
import com.monitorplatform.alarm.entity.vo.GatewayActionResultVO;
import com.monitorplatform.alarm.entity.vo.WeeklyAlarmChartVO;
import com.monitorplatform.alarm.entity.vo.WeeklySeriesVO;
import com.monitorplatform.alarm.feign.DeviceFeignClient;
import com.monitorplatform.alarm.feign.ForwardFeignClient;
import com.monitorplatform.alarm.mapper.AlarmMapper;
import com.monitorplatform.alarm.service.AlarmService;
import com.monitorplatform.alarm.websocket.AlarmPushService;
import com.monitorplatform.common.log.DiagnosticLogReport;
import com.monitorplatform.common.log.DiagnosticLogReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 告警服务实现
 */
@Slf4j
@Service
public class AlarmServiceImpl implements AlarmService {
    
    @Resource
    private AlarmMapper alarmMapper;
    
    @Resource
    private RestTemplate restTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ForwardFeignClient forwardFeignClient;

    @Resource
    private DeviceFeignClient deviceFeignClient;

    @Resource
    private AlarmPushService alarmPushService;

    @Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    /** Redis key 前缀：screen:status:{infoBoardIp} -> JSON{"status","operator","screenTime"} */
    private static final String SCREEN_STATUS_KEY_PREFIX = "screen:status:";
    /** 黑屏状态 TTL（24小时自动过期，防止异常不清） */
    private static final long SCREEN_STATUS_TTL_HOURS = 24;
    
    @Value("${monitor.device.url:http://localhost:8082}")
    private String deviceServiceUrl;

    @Value("${monitor.forward.url:http://localhost:8067}")
    private String forwardServiceUrl;

    @Value("${monitor.content.url:http://monitor-content:8065}")
    private String contentServiceUrl;

    @Value("${terminal.gateway.port:8093}")
    private Integer terminalGatewayDefaultPort;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${minio.bucket-name:monitor-content}")
    private String minioBucketName;

    @Value("${alarm.auto-black-screen.enabled:true}")
    private boolean autoBlackScreenEnabled;

    /** 情报板默认端口 */
    private static final int INFO_BOARD_DEFAULT_PORT = 9520;

    /**
     * 黑屏指令 HEX（JetFileII 第二种格式：主命令=0x04，子命令=0x01）
     * 平包序号固定为 0x0100，实际验证情报板不检校
     */
    private static final String BLACK_SCREEN_HEX = "55a325c00000000001011c0004010000";

    /**
     * 停止黑屏指令 HEX（JetFileII 第二种格式：主命令=0x04，子命令=0x02）
     * 通过 Wireshark 抓包 Sigma Play "停止黑屏" 操作获取
     */
    private static final String STOP_BLACK_SCREEN_HEX = "55a70f00000000000101070004020000";

    /**
     * 删除全部文件指令 HEX（JetFileII 第一种通信格式）
     * 格式: SOH(01) + Z(5A) + 地址"00"(3030) + STX(02) + 命令码'E'(45) + 参数'A'(41) + EOT(04)
     * 情报板收到后清空所有已存储的播放文件（Text/String/Picture/Playlist/Runtime Table）
     * 清空后情报板无内容可播，屏幕显示为空白状态
     */
    private static final String DELETE_ALL_FILES_HEX = "015a303002454104";
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 兼容前端传入 ISO 8601 格式（yyyy-MM-dd'T'HH:mm:ss） */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 解析时间字符串，兼容两种格式：
     * - "yyyy-MM-dd HH:mm:ss"（标准格式）
     * - "yyyy-MM-dd'T'HH:mm:ss"（ISO 8601）
     */
    private LocalDateTime parseDateTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        // 替换 'T' 分隔符为空格，统一使用标准格式解析
        String normalized = timeStr.replace('T', ' ');
        return LocalDateTime.parse(normalized, FORMATTER);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmRecord receiveAlarm(AlarmReceiveDTO dto) {
        AlarmRecord record = new AlarmRecord();
        BeanUtil.copyProperties(dto, record);
        
        // 设置默认值
        if (record.getAlarmLevel() == null || record.getAlarmLevel().isEmpty()) {
            record.setAlarmLevel("general");
        }
        record.setHandleStatus("pending");
        record.setCreateTime(LocalDateTime.now());

        // 冗余存储违规内容快照（contentType + contentData/contentFileUrl）
        fillContentSnapshot(record);

        alarmMapper.insert(record);
        reportAlarmCreated(record);
        log.info("接收告警: type={}, level={}, deviceId={}, contentType={}", 
                record.getAlarmType(), record.getAlarmLevel(), record.getDeviceId(), record.getContentType());

        // 联动更新情报板状态为"告警"（异步不阻塞主流程）
        updateInfoBoardStatusByChainId(record.getChainId(), "告警");

        // WebSocket 推送告警标志，通知前端刷新待处理列表
        alarmPushService.pushAlarmFlag();

        // 自动切断模式：告警入库后只发送黑屏指令，保留告警待人工判断。
        registerAutoBlackScreenAfterCommit(record);

        return record;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleAlarm(AlarmHandleDTO dto) {
        AlarmRecord record = alarmMapper.selectById(dto.getAlarmId());
        if (record == null) {
            throw new RuntimeException("告警记录不存在");
        }
        
        if ("processed".equals(record.getHandleStatus())) {
            throw new RuntimeException("告警已处理，请勿重复处理");
        }
        
        record.setHandleStatus("processed");
        // 保留原始告警级别，不在处理时降级（历史报表按级别筛选需要原始值）
        record.setHandleOperator(dto.getHandleOperator());
        record.setHandleTime(LocalDateTime.now());
        record.setHandleRemark(dto.getHandleRemark());
        
        int updated = alarmMapper.updateById(record);
        if (updated > 0) {
            reportAlarmHandled(record);
        }
        log.info("处理告警: id={}, operator={}", dto.getAlarmId(), dto.getHandleOperator());

        // 处理完告警后，检查该链路下是否还有未处理告警，无则恢复情报板为"在线"
        if (updated > 0 && record.getChainId() != null) {
            Long pendingCount = alarmMapper.countPendingByChainId(record.getChainId());
            if (pendingCount == null || pendingCount == 0) {
                updateInfoBoardStatusByChainId(record.getChainId(), "在线");
            } else {
                log.info("链路 {} 仍有 {} 条未处理告警，情报板保持告警状态", record.getChainId(), pendingCount);
            }
        }

        // WebSocket 推送告警标志，通知前端刷新待处理列表
        if (updated > 0) {
            alarmPushService.pushAlarmFlag();
        }

        return updated > 0;
    }
    
    private void reportAlarmCreated(AlarmRecord record) {
        if (record == null) {
            return;
        }
        DiagnosticLogReport report = new DiagnosticLogReport();
        report.setEventType("ALARM_CREATED");
        report.setEventLevel(toDiagnosticLevel(record.getAlarmLevel()));
        report.setStage("alarm");
        report.setServiceName("monitor-platform-alarm");
        report.setChainId(record.getChainId());
        report.setContentId(record.getContentId());
        report.setBoardIp(record.getBoardIp());
        report.setBoardPort(record.getBoardPort());
        report.setResultStatus("fail");
        report.setSummary("alarm created: " + safeText(record.getAlarmType()));
        report.setErrorMessage(record.getViolationDetail());
        report.setRefTable("alarm_record");
        report.setRefId(record.getId() == null ? null : String.valueOf(record.getId()));
        report.setDedupKey("ALARM_CREATED:" + record.getId());
        diagnosticLogReporter.reportAsync(report);
    }

    private void reportAlarmHandled(AlarmRecord record) {
        if (record == null) {
            return;
        }
        DiagnosticLogReport report = new DiagnosticLogReport();
        report.setEventType("ALARM_HANDLED");
        report.setEventLevel("info");
        report.setStage("alarm");
        report.setServiceName("monitor-platform-alarm");
        report.setChainId(record.getChainId());
        report.setContentId(record.getContentId());
        report.setBoardIp(record.getBoardIp());
        report.setBoardPort(record.getBoardPort());
        report.setOperatorName(record.getHandleOperator());
        report.setResultStatus("success");
        report.setSummary("alarm handled by " + safeText(record.getHandleOperator()));
        report.setErrorMessage(record.getHandleRemark());
        report.setRefTable("alarm_record");
        report.setRefId(record.getId() == null ? null : String.valueOf(record.getId()));
        report.setDedupKey("ALARM_HANDLED:" + record.getId());
        diagnosticLogReporter.reportAsync(report);
    }

    private String toDiagnosticLevel(String alarmLevel) {
        if ("critical".equalsIgnoreCase(alarmLevel) || "serious".equalsIgnoreCase(alarmLevel)) {
            return "error";
        }
        return "warn";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Page<AlarmRecord> pageQuery(AlarmQueryDTO dto) {
        Page<AlarmRecord> page = new Page<>(dto.getPageNum(), dto.getPageSize());

        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();

        if (dto.getAlarmType() != null && !dto.getAlarmType().isEmpty()) {
            // 支持前缀匹配：如传入 "content_violation" 可匹配
            // "content_violation_violence" / "content_violation_sensitive" 等细粒度子类型
            wrapper.likeRight(AlarmRecord::getAlarmType, dto.getAlarmType());
        }

        if (dto.getAlarmLevel() != null && !dto.getAlarmLevel().isEmpty()) {
            wrapper.eq(AlarmRecord::getAlarmLevel, dto.getAlarmLevel());
        }

        if (dto.getDeviceId() != null && !dto.getDeviceId().isEmpty()) {
            wrapper.eq(AlarmRecord::getDeviceId, dto.getDeviceId());
        }

        if (dto.getInfoBoardIp() != null && !dto.getInfoBoardIp().isEmpty()) {
            wrapper.eq(AlarmRecord::getBoardIp, dto.getInfoBoardIp());
        }

        if (dto.getHandleStatus() != null && !dto.getHandleStatus().isEmpty()) {
            wrapper.eq(AlarmRecord::getHandleStatus, dto.getHandleStatus());
        }

        if (dto.getStartTime() != null && !dto.getStartTime().isEmpty()) {
            LocalDateTime startTime = parseDateTime(dto.getStartTime());
            wrapper.ge(AlarmRecord::getAlarmTime, startTime);
        }

        if (dto.getEndTime() != null && !dto.getEndTime().isEmpty()) {
            LocalDateTime endTime = parseDateTime(dto.getEndTime());
            wrapper.le(AlarmRecord::getAlarmTime, endTime);
        }

        wrapper.orderByDesc(AlarmRecord::getAlarmTime);

        Page<AlarmRecord> result = alarmMapper.selectPage(page, wrapper);

        // 填充情报板IP、端口及经纬度
        result.getRecords().forEach(record -> {
            fillInfoBoardIp(record);
            fillLocation(record);
        });

        return result;
    }

    @Override
    public AlarmRecord getById(Long id) {
        AlarmRecord record = alarmMapper.selectById(id);
        if (record != null) {
            fillInfoBoardIp(record);
            fillLocation(record);
            fillContentInfo(record);
        }
        return record;
    }

    /**
     * 根据 contentId 从 content 服务查询违规内容详情
     * - contentType=text：填充 contentData（文本内容）
     * - contentType=image/video：填充 contentFileUrl（MinIO 访问URL）
     */
    private void fillContentInfo(AlarmRecord record) {
        if (record.getContentId() == null || record.getContentId().isEmpty()) {
            return;
        }
        try {
            String url = contentServiceUrl + "/content/detail/" + record.getContentId();
            log.debug("查询违规内容: GET {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Boolean.TRUE.equals(body.get("success"))) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    if (data != null) {
                        String contentType = (String) data.get("contentType");
                        record.setContentType(contentType);
                        if ("text".equals(contentType)) {
                            record.setContentData((String) data.get("data"));
                        } else if ("image".equals(contentType) || "video".equals(contentType)) {
                            // 优先使用 minioPath 拼接 URL，其次使用 fileUrl
                            String fileUrl = (String) data.get("fileUrl");
                            String minioPath = (String) data.get("minioPath");
                            if (minioPath != null && !minioPath.isEmpty()) {
                                record.setContentFileUrl(minioEndpoint + "/" + minioBucketName + "/" + minioPath);
                            } else if (fileUrl != null && !fileUrl.isEmpty()) {
                                record.setContentFileUrl(fileUrl);
                            }
                        }
                        log.debug("查询违规内容成功: contentId={}, contentType={}", record.getContentId(), contentType);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询违规内容失败: contentId={}, error={}", record.getContentId(), e.getMessage());
        }
    }

    /**
     * 告警上报时冗余存储违规内容快照到告警表
     * 从 content 服务查询内容详情，按 contentType 填充：
     * - text：contentData（文本内容）
     * - image/video：contentFileUrl（MinIO 完整访问URL）
     * 失败时仅记录日志，不影响告警入库
     */
    @SuppressWarnings("unchecked")
    private void fillContentSnapshot(AlarmRecord record) {
        if (record.getContentId() == null || record.getContentId().isEmpty()) {
            return;
        }
        try {
            String url = contentServiceUrl + "/content/detail/" + record.getContentId();
            log.debug("快照填充-查询违规内容: GET {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return;
            }
            Map<String, Object> body = response.getBody();
            if (!Boolean.TRUE.equals(body.get("success"))) {
                return;
            }
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) {
                return;
            }

            String contentType = (String) data.get("contentType");
            record.setContentType(contentType);

            if ("text".equals(contentType)) {
                // 文本内容直接存储
                record.setContentData((String) data.get("data"));
            } else if ("image".equals(contentType) || "video".equals(contentType)) {
                // 图片/视频：拼接 MinIO 完整URL存储
                String minioPath = (String) data.get("minioPath");
                if (minioPath != null && !minioPath.isEmpty()) {
                    record.setContentFileUrl(minioEndpoint + "/" + minioBucketName + "/" + minioPath);
                }
            }

            log.info("快照填充成功: contentId={}, contentType={}", record.getContentId(), contentType);
        } catch (Exception e) {
            log.warn("快照填充失败（不影响告警入库）: contentId={}, error={}", record.getContentId(), e.getMessage());
        }
    }

    /**
     * 填充情报板IP和端口
     * 优先使用持久化的 boardIp/boardPort，无值时通过 chainId 查询 forward 服务获取
     */
    private void fillInfoBoardIp(AlarmRecord record) {
        // 优先使用已持久化的 boardIp/boardPort（新数据已携带）
        if (record.getBoardIp() != null) {
            record.setInfoBoardIp(record.getBoardIp());
            record.setInfoBoardPort(record.getBoardPort());
            return;
        }
        // fallback: 历史数据通过 forward 服务查询
        if (record.getChainId() == null) {
            return;
        }
        try {
            String url = forwardServiceUrl + "/chain/info-board?chainId=" + record.getChainId();
            log.debug("查询情报板信息: GET {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Integer.valueOf(200).equals(body.get("code"))) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    if (data != null) {
                        String infoBoardIp = (String) data.get("infoBoardIp");
                        Integer infoBoardPort = (Integer) data.get("infoBoardPort");
                        record.setInfoBoardIp(infoBoardIp);
                        record.setInfoBoardPort(infoBoardPort);
                        log.debug("查询情报板信息成功: chainId={}, infoBoardIp={}, infoBoardPort={}",
                                record.getChainId(), infoBoardIp, infoBoardPort);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询情报板信息失败: chainId={}, error={}", record.getChainId(), e.getMessage());
        }
    }

    /**
     * 填充经纬度：通过 chainId → Forward 查情报板IP → Device 查经纬度
     */
    @SuppressWarnings("unchecked")
    private void fillLocation(AlarmRecord record) {
        if (record.getChainId() == null) {
            return;
        }
        try {
            Map<String, Object> forwardResp = forwardFeignClient.getInfoBoardByChainId(record.getChainId());
            if (forwardResp != null && Integer.valueOf(200).equals(forwardResp.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) forwardResp.get("data");
                if (data != null) {
                    String infoBoardIp = (String) data.get("infoBoardIp");
                    if (infoBoardIp != null && !infoBoardIp.isEmpty()) {
                        Map<String, Object> locationResp = deviceFeignClient.getLocationByIp(infoBoardIp);
                        if (locationResp != null && Integer.valueOf(200).equals(locationResp.get("code"))) {
                            Map<String, Object> locationData = (Map<String, Object>) locationResp.get("data");
                            if (locationData != null) {
                                Object lon = locationData.get("longitude");
                                Object lat = locationData.get("latitude");
                                record.setLongitude(lon != null ? lon.toString() : null);
                                record.setLatitude(lat != null ? lat.toString() : null);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询经纬度失败: chainId={}, error={}", record.getChainId(), e.getMessage());
        }
    }

    /**
     * 通过 chainId → Forward Feign → 获取情报板 IP → Device Feign → 更新情报板状态
     * 失败时仅记录日志，不影响主流程
     *
     * @param chainId 链路 ID
     * @param status  目标状态（"告警" / "在线"）
     */
    @SuppressWarnings("unchecked")
    private void updateInfoBoardStatusByChainId(Long chainId, String status) {
        if (chainId == null) {
            return;
        }
        try {
            // Step 1: 通过 forward 服务查询情报板 IP
            Map<String, Object> forwardResp = forwardFeignClient.getInfoBoardByChainId(chainId);
            if (forwardResp == null || !Integer.valueOf(200).equals(forwardResp.get("code"))) {
                log.warn("查询情报板IP失败（forward返回异常）: chainId={}", chainId);
                return;
            }
            Map<String, Object> data = (Map<String, Object>) forwardResp.get("data");
            if (data == null) {
                log.warn("查询情报板IP失败（data为空）: chainId={}", chainId);
                return;
            }
            String infoBoardIp = (String) data.get("infoBoardIp");
            if (infoBoardIp == null || infoBoardIp.isEmpty()) {
                log.warn("情报板IP为空: chainId={}", chainId);
                return;
            }

            // Step 2: 通过 device 服务更新情报板状态
            deviceFeignClient.updateStatusByIp(infoBoardIp, status);
            log.info("情报板状态联动更新成功: chainId={}, ip={}, status={}", chainId, infoBoardIp, status);

        } catch (Exception e) {
            log.warn("情报板状态联动更新失败（不影响主流程）: chainId={}, status={}, error={}",
                    chainId, status, e.getMessage());
        }
    }
    
    @Override
    public List<AlarmRecord> getRecentList(Integer limit) {
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AlarmRecord::getAlarmTime);
        wrapper.last("LIMIT " + limit);
        return alarmMapper.selectList(wrapper);
    }
    
    @Override
    public List<AlarmRecord> getPendingList(Integer limit) {
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmRecord::getHandleStatus, "pending");
        wrapper.orderByDesc(AlarmRecord::getAlarmTime);
        wrapper.last("LIMIT " + limit);
        return alarmMapper.selectList(wrapper);
    }
    
    @Override
    public AlarmStatisticsDTO statistics() {
        AlarmStatisticsDTO dto = new AlarmStatisticsDTO();

        // 总数
        dto.setTotalCount(alarmMapper.selectCount(null));

        // 待处理/已处理
        dto.setPendingCount(alarmMapper.countPending());
        dto.setProcessedCount(alarmMapper.countProcessed());

        // 严重/重要级别
        dto.setCriticalCount(alarmMapper.countCritical());
        dto.setSeriousCount(alarmMapper.countSerious());

        // 按类型统计
        List<Map<String, Object>> typeList = alarmMapper.countByType();
        for (Map<String, Object> map : typeList) {
            String type = (String) map.get("alarm_type");
            Long count = (Long) map.get("count");
            dto.getTypeCount().put(type, count);
        }

        // 按级别统计
        List<Map<String, Object>> levelList = alarmMapper.countByLevel();
        for (Map<String, Object> map : levelList) {
            String level = (String) map.get("alarm_level");
            Long count = (Long) map.get("count");
            dto.getLevelCount().put(level, count);
        }

        return dto;
    }
    
    @Override
    public void exportExcel(HttpServletResponse response, AlarmQueryDTO dto) {
        try {
            // 查询数据
            dto.setPageSize(10000); // 导出最多1万条
            Page<AlarmRecord> page = pageQuery(dto);
            List<AlarmRecord> list = page.getRecords();
            
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("告警记录_" + DateUtil.today(), "UTF-8");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            // 导出
            EasyExcel.write(response.getOutputStream(), AlarmRecord.class)
                    .sheet("告警记录")
                    .doWrite(list);
                    
            log.info("导出告警记录: count={}", list.size());
            
        } catch (IOException e) {
            log.error("导出告警记录失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        int deleted = alarmMapper.deleteById(id);
        log.info("删除告警: id={}", id);
        return deleted > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBatch(List<Long> ids) {
        int count = alarmMapper.deleteBatchIds(ids);
        log.info("批量删除告警: count={}", count);
        return count;
    }
    
    @Override
    public DashboardOverviewDTO getDashboardOverview() {
        DashboardOverviewDTO dto = new DashboardOverviewDTO();
        
        // 总报警数（今日）
        Long todayTotal = alarmMapper.countToday();
        Long yesterdayTotal = alarmMapper.countYesterday();
        dto.setTotalAlarms(todayTotal);
        dto.setTotalChangePercent(calculateChangePercent(todayTotal, yesterdayTotal));
        
        // 紧急告警数（critical + serious）
        Long urgentToday = alarmMapper.countUrgentToday();
        Long urgentYesterday = alarmMapper.countUrgentYesterday();
        dto.setUrgentAlarms(urgentToday);
        dto.setUrgentChangePercent(calculateChangePercent(urgentToday, urgentYesterday));
        
        // 已处理数
        Long processedToday = alarmMapper.countProcessedToday();
        Long processedYesterday = alarmMapper.countProcessedYesterday();
        dto.setProcessedAlarms(processedToday);
        dto.setProcessedChangePercent(calculateChangePercent(processedToday, processedYesterday));
        
        // 在线设备数（调用设备服务）
        Long onlineDevices = getOnlineDevicesCount();
        dto.setOnlineDevices(onlineDevices);
        dto.setOnlineDevicesChangePercent(0.0); // 暂不计算变化
        
        return dto;
    }
    
    /**
     * 计算变化百分比
     */
    private Double calculateChangePercent(Long today, Long yesterday) {
        if (yesterday == null || yesterday == 0) {
            return today != null && today > 0 ? 100.0 : 0.0;
        }
        if (today == null) {
            return -100.0;
        }
        return ((today - yesterday) * 100.0) / yesterday;
    }
    
    /**
     * 调用设备服务获取在线设备数
     */
    private Long getOnlineDevicesCount() {
        try {
            String url = deviceServiceUrl + "/device/unified/statistics";
            String response = HttpRequest.get(url).timeout(3000).execute().body();
            JSONObject json = JSON.parseObject(response);
            if (json != null && json.getInteger("code") == 200) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    return data.getLong("totalOnline");
                }
            }
        } catch (Exception e) {
            log.warn("调用设备服务失败: {}", e.getMessage());
        }
        return 0L;
    }

    private void registerAutoBlackScreenAfterCommit(AlarmRecord record) {
        if (!autoBlackScreenEnabled) {
            log.debug("自动黑屏已禁用，跳过: alarmId={}", record == null ? null : record.getId());
            return;
        }
        if (record == null || record.getId() == null) {
            log.warn("自动黑屏缺少告警记录 ID，跳过");
            return;
        }

        Runnable task = () -> CompletableFuture.runAsync(() -> sendAutoBlackScreen(record.getId()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void sendAutoBlackScreen(Long alarmId) {
        try {
            AlarmRecord alarm = alarmMapper.selectById(alarmId);
            if (alarm == null) {
                log.warn("自动黑屏失败，告警记录不存在: alarmId={}", alarmId);
                return;
            }

            DisconnectGatewayDTO dto = buildAutoBlackScreenRequest(alarm);
            if (dto == null) {
                return;
            }

            GatewayActionResultVO result = sendAutoBlackScreenCommand(dto);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                log.info("告警触发自动黑屏成功: alarmId={}, boardIp={}, contentId={}",
                        alarmId, dto.getInfoBoardIp(), dto.getContentId());
            } else {
                log.warn("告警触发自动黑屏失败: alarmId={}, boardIp={}, message={}",
                        alarmId, dto.getInfoBoardIp(), result.getMessage());
            }
        } catch (Exception e) {
            log.error("告警触发自动黑屏异常: alarmId={}", alarmId, e);
        }
    }

    private DisconnectGatewayDTO buildAutoBlackScreenRequest(AlarmRecord alarm) {
        fillInfoBoardIp(alarm);
        String infoBoardIp = firstNotBlank(alarm.getBoardIp(), alarm.getInfoBoardIp());
        if (infoBoardIp == null) {
            log.warn("自动黑屏缺少情报板 IP，跳过: alarmId={}, chainId={}, contentId={}",
                    alarm.getId(), alarm.getChainId(), alarm.getContentId());
            return null;
        }

        Integer infoBoardPort = alarm.getBoardPort() != null ? alarm.getBoardPort() : alarm.getInfoBoardPort();
        DisconnectGatewayDTO dto = new DisconnectGatewayDTO();
        dto.setInfoBoardIp(infoBoardIp);
        dto.setInfoBoardPort(infoBoardPort);
        dto.setAlarmId(alarm.getId());
        dto.setContentId(alarm.getContentId());
        dto.setChainId(alarm.getChainId() == null ? null : String.valueOf(alarm.getChainId()));
        dto.setOperator("system-auto");
        dto.setReason("告警触发自动黑屏");
        return dto;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayActionResultVO sendAutoBlackScreenCommand(DisconnectGatewayDTO dto) {
        String triggerSource = "AUTO_ALARM";
        GatewayActionResultVO result = new GatewayActionResultVO();
        if (dto == null || dto.getInfoBoardIp() == null || dto.getInfoBoardIp().trim().isEmpty()) {
            result.setSuccess(false);
            result.setMessage("情报板 IP 为空");
            return result;
        }

        String infoBoardIp = dto.getInfoBoardIp().trim();
        int infoBoardPort = dto.getInfoBoardPort() != null ? dto.getInfoBoardPort() : INFO_BOARD_DEFAULT_PORT;
        Long chainId = resolveChainId(dto);

        try {
            String queryUrl = forwardServiceUrl + "/chain/gateway-by-board?boardIp=" + URLEncoder.encode(infoBoardIp, "UTF-8");
            log.info("{} 查询终端网关: GET {}", triggerSource, queryUrl);
            ResponseEntity<Map> gwResponse = restTemplate.getForEntity(queryUrl, Map.class);
            if (!gwResponse.getStatusCode().is2xxSuccessful() || gwResponse.getBody() == null) {
                result.setSuccess(false);
                result.setMessage("查询终端网关信息失败");
                return result;
            }

            Map<String, Object> gwBody = gwResponse.getBody();
            if (!Integer.valueOf(200).equals(gwBody.get("code"))) {
                result.setSuccess(false);
                result.setMessage("查询终端网关失败: " + gwBody.get("message"));
                return result;
            }

            Map<String, Object> gwData = (Map<String, Object>) gwBody.get("data");
            if (gwData == null || !Boolean.TRUE.equals(gwData.get("success"))) {
                result.setSuccess(false);
                result.setMessage(gwData != null ? String.valueOf(gwData.get("message")) : "未找到对应终端网关");
                return result;
            }

            String terminalGatewayIp = (String) gwData.get("terminalGatewayIp");
            if (chainId == null && gwData.get("chainId") != null) {
                chainId = parseLongQuietly(gwData.get("chainId"));
            }
            if (terminalGatewayIp == null || terminalGatewayIp.trim().isEmpty()) {
                result.setSuccess(false);
                result.setMessage("终端网关 IP 为空");
                return result;
            }

            String sendUrl = String.format("http://%s:%d/udp-proxy/send-command",
                    terminalGatewayIp, terminalGatewayDefaultPort);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("targetIp", infoBoardIp);
            requestBody.put("targetPort", infoBoardPort);
            requestBody.put("hexData", BLACK_SCREEN_HEX);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(sendUrl, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                result.setSuccess(false);
                result.setMessage("终端网关返回非成功状态");
                return result;
            }

            result.setSuccess(true);
            result.setAlarmId(dto.getAlarmId());
            result.setChainId(chainId);
            result.setInfoBoardIp(infoBoardIp);
            result.setInfoBoardPort(infoBoardPort);
            result.setTerminalGatewayIp(terminalGatewayIp);
            result.setMessage("黑屏指令已发送");
            return result;
        } catch (Exception e) {
            log.error("{} 发送黑屏指令异常: alarmId={}, boardIp={}", triggerSource, dto.getAlarmId(), infoBoardIp, e);
            result.setSuccess(false);
            result.setMessage("发送黑屏指令异常: " + e.getMessage());
            return result;
        }
    }

    private Long resolveChainId(DisconnectGatewayDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getAlarmId() != null && dto.getAlarmId() > 0) {
            AlarmRecord alarmRecord = alarmMapper.selectById(dto.getAlarmId());
            if (alarmRecord != null) {
                return alarmRecord.getChainId();
            }
        }
        return parseLongQuietly(dto.getChainId());
    }

    private Long parseLongQuietly(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNotBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }
    
    @Override
    public GatewayActionResultVO disconnectGatewayConnection(DisconnectGatewayDTO dto) {
        GatewayActionResultVO result = new GatewayActionResultVO();

        log.info("==========================================");
        log.info("【应急处置】向情报板发送黑屏指令");
        log.info("情报板 IP: {}", dto.getInfoBoardIp());
        log.info("告警 ID: {}", dto.getAlarmId());
        log.info("操作人: {}", dto.getOperator());
        log.info("原因: {}", dto.getReason());
        log.info("==========================================");

        try {
            String infoBoardIp = dto.getInfoBoardIp();
            int infoBoardPort = dto.getInfoBoardPort() != null ? dto.getInfoBoardPort() : INFO_BOARD_DEFAULT_PORT;

            // ==================== Step 1: 解析 chainId（仅用于响应回显，不做拦截） ====================
            Long chainId = null;
            if (dto.getAlarmId() != null && dto.getAlarmId() > 0) {
                AlarmRecord alarmRecord = alarmMapper.selectById(dto.getAlarmId());
                if (alarmRecord != null) {
                    chainId = alarmRecord.getChainId();
                } else {
                    log.warn("alarmId={} 对应告警记录不存在，忽略并继续发送黑屏指令", dto.getAlarmId());
                }
            } else if (dto.getChainId() != null) {
                try { chainId = Long.valueOf(dto.getChainId()); } catch (NumberFormatException ignore) {}
            }

            // ==================== Step 2: 查询终端网关 IP ====================
            String queryUrl = forwardServiceUrl + "/chain/gateway-by-board?boardIp=" + infoBoardIp;
            log.info("查询终端网关: GET {}", queryUrl);

            ResponseEntity<Map> gwResponse = restTemplate.getForEntity(queryUrl, Map.class);
            if (!gwResponse.getStatusCode().is2xxSuccessful() || gwResponse.getBody() == null) {
                result.setSuccess(false);
                result.setMessage("查询终端网关信息失败");
                return result;
            }

            Map<String, Object> gwBody = gwResponse.getBody();
            if (!Integer.valueOf(200).equals(gwBody.get("code"))) {
                result.setSuccess(false);
                result.setMessage("查询终端网关失败: " + gwBody.get("message"));
                return result;
            }

            Map<String, Object> gwData = (Map<String, Object>) gwBody.get("data");
            if (gwData == null || !Boolean.TRUE.equals(gwData.get("success"))) {
                result.setSuccess(false);
                result.setMessage(gwData != null ? String.valueOf(gwData.get("message")) : "未找到对应终端网关");
                return result;
            }

            String terminalGatewayIp = (String) gwData.get("terminalGatewayIp");
            Integer terminalGatewayPort = terminalGatewayDefaultPort;

            // 从网关查询结果中获取 chainId（兜底：当请求未传 alarmId/chainId 时仍可用于状态联动）
            if (chainId == null && gwData.get("chainId") != null) {
                try { chainId = Long.valueOf(gwData.get("chainId").toString()); } catch (NumberFormatException ignore) {}
            }

            if (terminalGatewayIp == null || terminalGatewayIp.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("终端网关 IP 为空");
                return result;
            }

            log.info("终端网关: {}:{}", terminalGatewayIp, terminalGatewayPort);
            log.info("目标情报板: {}:{}", infoBoardIp, infoBoardPort);

            // ==================== Step 3: 发送黑屏指令（核心，不可被阻断） ====================
            String sendUrl = String.format("http://%s:%d/udp-proxy/send-command",
                    terminalGatewayIp, terminalGatewayPort);
            log.info("调用终端网关黑屏接口: POST {}", sendUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("targetIp", infoBoardIp);
            requestBody.put("targetPort", infoBoardPort);
            requestBody.put("hexData", BLACK_SCREEN_HEX);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(sendUrl, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                result.setSuccess(true);
                result.setAlarmId(dto.getAlarmId());
                result.setChainId(chainId);
                result.setInfoBoardIp(infoBoardIp);
                result.setInfoBoardPort(infoBoardPort);
                result.setTerminalGatewayIp(terminalGatewayIp);
                result.setMessage("黑屏指令已发送");
                log.info("✅ 黑屏指令发送成功");

                // 写入黑屏状态到 Redis
                writeScreenStatus(infoBoardIp, "BLACK", dto.getOperator());

                boolean cleanupViolationContent = false;
                AlarmRecord matchedDisconnectAlarm = null;
                // ==================== Step 3.5: 仅当前内容属于待处理告警时，删除大屏违规文件 ====================
                try {
                    AlarmRecord matchedAlarm = resolvePendingAlarmForDisconnect(dto, infoBoardIp, infoBoardPort);
                    if (matchedAlarm != null && isCurrentBoardContentMatched(matchedAlarm, infoBoardIp, infoBoardPort, dto.getContentId())) {
                        matchedDisconnectAlarm = matchedAlarm;
                        cleanupViolationContent = true;
                        String violationContentId = matchedAlarm.getContentId();
                        String violationFileName = queryFileNameFromContent(violationContentId);
                        if (violationFileName != null && !violationFileName.isEmpty()) {
                            String boardFilePath = "D:\\P\\" + violationFileName;
                            log.info("✅ 找到当前告警违规文件: alarmId={}, contentId={}, fileName={}, 情报板路径={}",
                                    matchedAlarm.getId(), violationContentId, violationFileName, boardFilePath);

                            // 发送 JetFileII Type3 格式删除指令（通过 Wireshark 抓包 Sigma Play 验证）
                            // 格式: 55 A3 [chk2B] 0000 0000 0101 [ser2B] 07 06 04 00 [filepath] 00 00
                            // mainCmd=0x07(文件管理) subCmd=0x06(删除文件) param=0x04(图片类型)
                            String deleteFileHex = buildDeleteFileHex(boardFilePath);
                            if (deleteFileHex != null) {
                                Map<String, Object> delFileBody = new HashMap<>();
                                delFileBody.put("targetIp", infoBoardIp);
                                delFileBody.put("targetPort", infoBoardPort);
                                delFileBody.put("hexData", deleteFileHex);

                                HttpEntity<Map<String, Object>> delFileEntity = new HttpEntity<>(delFileBody, headers);
                                ResponseEntity<Map> delFileResp = restTemplate.postForEntity(sendUrl, delFileEntity, Map.class);
                                log.info("删除文件指令已发送(Type3): targetFile={}, hex={}, response={}",
                                        boardFilePath, deleteFileHex, delFileResp.getBody());
                            } else {
                                log.warn("构建删除指令失败，跳过文件删除: {}", boardFilePath);
                            }

                            result.setViolationFile(boardFilePath);
                            result.setCleanupViolationContent(true);
                        } else {
                            log.info("未查到违规文件名: contentId={}", violationContentId);
                        }
                    } else {
                        result.setCleanupViolationContent(false);
                    }
                } catch (Exception e) {
                    log.warn("查找/删除违规文件异常（不影响黑屏流程）: {}", e.getMessage());
                }

                // ==================== Step 4: 处置告警（按 alarmId 或按 boardIp 批量处置） ====================
                try {
                    String handleRemark = "应急黑屏: " + (dto.getReason() != null ? dto.getReason() : "黑屏处置");
                    if (dto.getAlarmId() != null && dto.getAlarmId() > 0) {
                        AlarmHandleDTO handleDTO = new AlarmHandleDTO();
                        handleDTO.setAlarmId(dto.getAlarmId());
                        handleDTO.setHandleOperator(dto.getOperator());
                        handleDTO.setHandleRemark(handleRemark);
                        boolean handleSuccess = handleAlarm(handleDTO);
                        log.info("✅ 指定告警处置{}: alarmId={}", handleSuccess ? "成功" : "失败(可能已处置)", dto.getAlarmId());
                    } else if (matchedDisconnectAlarm != null) {
                        matchedDisconnectAlarm.setHandleStatus("processed");
                        matchedDisconnectAlarm.setHandleOperator(dto.getOperator());
                        matchedDisconnectAlarm.setHandleTime(LocalDateTime.now());
                        matchedDisconnectAlarm.setHandleRemark(handleRemark);
                        alarmMapper.updateById(matchedDisconnectAlarm);
                        if (chainId == null) chainId = matchedDisconnectAlarm.getChainId();
                        if (chainId != null) {
                            Long remaining = alarmMapper.countPendingByChainId(chainId);
                            if (remaining == null || remaining == 0) {
                                updateInfoBoardStatusByChainId(chainId, "在线");
                            }
                        }
                        alarmPushService.pushAlarmFlag();
                        log.info("✅ 当前图片匹配告警，已处置对应告警: alarmId={}, contentId={}",
                                matchedDisconnectAlarm.getId(), matchedDisconnectAlarm.getContentId());
                    } else {
                        log.info("未匹配到当前图片对应的待处理告警，不批量处置历史告警: boardIp={}, contentId={}",
                                infoBoardIp, dto.getContentId());
                    }
                } catch (Exception e) {
                    log.warn("告警处置失败（不影响黑屏结果）: {}", e.getMessage());
                }

                // ==================== Step 5: 仅匹配当前告警内容时，清理违规内容记录 ====================
                if (cleanupViolationContent) {
                    try {
                        String deleteViolationUrl = contentServiceUrl + "/content/delete-violation-by-board";
                        Map<String, Object> deleteBody = new HashMap<>();
                        deleteBody.put("boardIp", infoBoardIp);
                        deleteBody.put("boardPort", infoBoardPort);
                        HttpEntity<Map<String, Object>> deleteEntity = new HttpEntity<>(deleteBody, headers);
                        restTemplate.postForEntity(deleteViolationUrl, deleteEntity, Map.class);
                        log.info("✅ 当前内容匹配待处理告警，违规内容记录已清理: boardIp={}", infoBoardIp);
                    } catch (Exception e) {
                        log.warn("清理违规内容记录失败（不影响主流程）: {}", e.getMessage());
                    }
                } else {
                    log.info("当前图片不属于待处理告警内容，跳过大屏文件删除和违规内容记录清理: boardIp={}, contentId={}",
                            infoBoardIp, dto.getContentId());
                }

                // ==================== Step 6: 直接更新设备状态为"在线" ====================
                try {
                    deviceFeignClient.updateStatusByIp(infoBoardIp, "在线");
                    log.info("✅ 设备状态已更新为在线: boardIp={}", infoBoardIp);
                } catch (Exception e) {
                    log.warn("更新设备状态失败（不影响主流程）: {}", e.getMessage());
                }
            } else {
                result.setSuccess(false);
                result.setMessage("终端网关返回非成功状态");
            }

            return result;

        } catch (Exception e) {
            log.error("应急处置异常", e);
            result.setSuccess(false);
            result.setMessage("操作异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 向 Redis 写入情报板黑屏状态
     * key: screen:status:{infoBoardIp}
     * value: JSON {"status":"BLACK/NORMAL","operator":"xxx","screenTime":"yyyy-MM-dd HH:mm:ss"}
     * TTL: 24 小时自动过期
     */
    private void writeScreenStatus(String infoBoardIp, String status, String operator) {
        try {
            Map<String, String> info = new HashMap<>();
            info.put("status", status);
            info.put("operator", operator != null ? operator : "");
            info.put("screenTime", LocalDateTime.now().format(FORMATTER));
            String key = SCREEN_STATUS_KEY_PREFIX + infoBoardIp;
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(info),
                    SCREEN_STATUS_TTL_HOURS, TimeUnit.HOURS);
            log.info("✅ 黑屏状态已写入 Redis: key={}, status={}", key, status);
        } catch (Exception e) {
            log.warn("写入黑屏状态到 Redis 失败（不影响主流程）: infoBoardIp={}, error={}", infoBoardIp, e.getMessage());
        }
    }

    /**
     * 递归判断节点列表中是否包含指定情报板 IP
     */
    private boolean containsInfoBoard(List<Map<String, Object>> nodes, String infoBoardIp) {
        if (nodes == null || nodes.isEmpty()) return false;
        for (Map<String, Object> node : nodes) {
            if ("info_board".equals(node.get("deviceType"))
                    && infoBoardIp.equals(node.get("ipAddress"))) {
                return true;
            }
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (containsInfoBoard(children, infoBoardIp)) return true;
        }
        return false;
    }

    @Override
    public GatewayActionResultVO resumeGatewayConnection(DisconnectGatewayDTO dto) {
        GatewayActionResultVO result = new GatewayActionResultVO();

        log.info("==========================================");
        log.info("\u3010\u5e94\u6025\u6062\u590d\u3011\u5411\u60c5\u62a5\u677f\u53d1\u9001\u505c\u6b62\u9ed1\u5c4f\u6307\u4ee4");
        log.info("\u60c5\u62a5\u677f IP: {}", dto.getInfoBoardIp());
        log.info("\u64cd\u4f5c\u4eba: {}", dto.getOperator());
        log.info("\u539f\u56e0: {}", dto.getReason());
        log.info("==========================================");

        try {
            String infoBoardIp = dto.getInfoBoardIp();
            int infoBoardPort = dto.getInfoBoardPort() != null ? dto.getInfoBoardPort() : INFO_BOARD_DEFAULT_PORT;

            // Step 1: 可选 chainId 归属验证
            Long chainId = null;
            if (dto.getAlarmId() != null && dto.getAlarmId() > 0) {
                AlarmRecord alarmRecord = alarmMapper.selectById(dto.getAlarmId());
                if (alarmRecord != null) chainId = alarmRecord.getChainId();
            } else if (dto.getChainId() != null) {
                try { chainId = Long.valueOf(dto.getChainId()); } catch (NumberFormatException ignore) {}
            }

            if (chainId != null) {
                log.info("\u9a8c\u8bc1\u60c5\u62a5\u677f\u5f52\u5c5e\u94fe\u8def ID: {}", chainId);
                String treeUrl = forwardServiceUrl + "/chain/tree/" + chainId;
                ResponseEntity<Map> treeResponse = restTemplate.getForEntity(treeUrl, Map.class);
                if (treeResponse.getStatusCode().is2xxSuccessful() && treeResponse.getBody() != null
                        && Integer.valueOf(200).equals(treeResponse.getBody().get("code"))) {
                    Map<String, Object> chainData = (Map<String, Object>) treeResponse.getBody().get("data");
                    if (chainData != null) {
                        List<Map<String, Object>> nodes = (List<Map<String, Object>>) chainData.get("nodes");
                        if (!containsInfoBoard(nodes, infoBoardIp)) {
                            result.setSuccess(false);
                            result.setMessage("\u60c5\u62a5\u677f IP [" + infoBoardIp + "] \u4e0d\u5c5e\u4e8e\u94fe\u8def " + chainId + "\uff0c\u62d2\u7edd\u6062\u590d");
                            return result;
                        }
                    }
                }
                log.info("\u9a8c\u8bc1\u901a\u8fc7: \u60c5\u62a5\u677f IP={} \u5c5e\u4e8e\u94fe\u8def ID={}", infoBoardIp, chainId);
            } else {
                log.warn("\u672a\u63d0\u4f9b chainId\uff0c\u8df3\u8fc7\u94fe\u8def\u5f52\u5c5e\u9a8c\u8bc1\uff0c\u76f4\u63a5\u53d1\u9001\u505c\u6b62\u9ed1\u5c4f\u6307\u4ee4: boardIp={}", infoBoardIp);
            }

            // Step 2: \u67e5\u8be2\u7ec8\u7aef\u7f51\u5173 IP
            String queryUrl = forwardServiceUrl + "/chain/gateway-by-board?boardIp=" + infoBoardIp;
            log.info("\u67e5\u8be2\u7ec8\u7aef\u7f51\u5173: GET {}", queryUrl);
            ResponseEntity<Map> gwResponse = restTemplate.getForEntity(queryUrl, Map.class);
            if (!gwResponse.getStatusCode().is2xxSuccessful() || gwResponse.getBody() == null) {
                result.setSuccess(false);
                result.setMessage("\u67e5\u8be2\u7ec8\u7aef\u7f51\u5173\u4fe1\u606f\u5931\u8d25");
                return result;
            }
            Map<String, Object> gwBody = gwResponse.getBody();
            if (!Integer.valueOf(200).equals(gwBody.get("code"))) {
                result.setSuccess(false);
                result.setMessage("\u67e5\u8be2\u7ec8\u7aef\u7f51\u5173\u5931\u8d25: " + gwBody.get("message"));
                return result;
            }
            Map<String, Object> gwData = (Map<String, Object>) gwBody.get("data");
            if (gwData == null || !Boolean.TRUE.equals(gwData.get("success"))) {
                result.setSuccess(false);
                result.setMessage(gwData != null ? String.valueOf(gwData.get("message")) : "\u672a\u627e\u5230\u5bf9\u5e94\u7ec8\u7aef\u7f51\u5173");
                return result;
            }
            String terminalGatewayIp = (String) gwData.get("terminalGatewayIp");
            if (terminalGatewayIp == null || terminalGatewayIp.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("\u7ec8\u7aef\u7f51\u5173 IP \u4e3a\u7a7a");
                return result;
            }

            // Step 3: \u53d1\u9001\u505c\u6b62\u9ed1\u5c4f\u6307\u4ee4
            String sendUrl = String.format("http://%s:%d/udp-proxy/send-command",
                    terminalGatewayIp, terminalGatewayDefaultPort);
            log.info("\u8c03\u7528\u7ec8\u7aef\u7f51\u5173\u505c\u6b62\u9ed1\u5c4f\u63a5\u53e3: POST {}", sendUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("targetIp", infoBoardIp);
            requestBody.put("targetPort", infoBoardPort);
            requestBody.put("hexData", STOP_BLACK_SCREEN_HEX);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(sendUrl, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ 停止黑屏指令发送成功");

                // Step 4: 发送删除全部文件指令，清掉情报板上正在播放的违规内容
                // 停止黑屏后情报板会恢复显示之前的内容（违规图片），
                // 此指令清空情报板存储的所有播放文件，使其不再显示违规内容
                try {
                    Map<String, Object> deleteBody = new HashMap<>();
                    deleteBody.put("targetIp", infoBoardIp);
                    deleteBody.put("targetPort", infoBoardPort);
                    deleteBody.put("hexData", DELETE_ALL_FILES_HEX);

                    HttpEntity<Map<String, Object>> deleteEntity = new HttpEntity<>(deleteBody, headers);
                    ResponseEntity<Map> deleteResponse = restTemplate.postForEntity(sendUrl, deleteEntity, Map.class);
                    if (deleteResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("✅ 删除全部文件指令发送成功，情报板播放内容已清除");
                    } else {
                        log.warn("删除全部文件指令发送返回非成功状态");
                    }
                } catch (Exception e) {
                    log.warn("删除全部文件指令发送异常（不影响恢复流程）: {}", e.getMessage());
                }

                // ==================== Step 5: 清理违规内容 + 写入默认底图记录（平台展示同步） ====================
                log.info("==================== 开始执行恢复流程 Step 5 ====================");
                log.info("contentServiceUrl={}, boardIp={}, boardPort={}", contentServiceUrl, infoBoardIp, infoBoardPort);
                try {
                    String deleteViolationUrl = contentServiceUrl + "/content/delete-violation-by-board";
                    log.info("调用清理违规内容: POST {}", deleteViolationUrl);
                    Map<String, Object> delBody = new HashMap<>();
                    delBody.put("boardIp", infoBoardIp);
                    delBody.put("boardPort", infoBoardPort);
                    HttpEntity<Map<String, Object>> delEntity = new HttpEntity<>(delBody, headers);
                    ResponseEntity<Map> delResp = restTemplate.postForEntity(deleteViolationUrl, delEntity, Map.class);
                    log.info("✅ 恢复流程-违规内容记录已清理: boardIp={}, response={}", infoBoardIp, delResp.getBody());
                } catch (Exception e) {
                    log.warn("恢复流程-清理违规内容记录失败: {}", e.getMessage(), e);
                }
                try {
                    String insertDefaultUrl = contentServiceUrl + "/content/insert-default-image";
                    log.info("调用写入默认底图: POST {}", insertDefaultUrl);
                    Map<String, Object> defaultBody = new HashMap<>();
                    defaultBody.put("boardIp", infoBoardIp);
                    defaultBody.put("boardPort", infoBoardPort);
                    defaultBody.put("minioPath", "images/default.jpg");
                    defaultBody.put("operator", dto.getOperator());
                    HttpEntity<Map<String, Object>> defaultEntity = new HttpEntity<>(defaultBody, headers);
                    ResponseEntity<Map> defResp = restTemplate.postForEntity(insertDefaultUrl, defaultEntity, Map.class);
                    log.info("✅ 恢复流程-默认底图记录已写入: boardIp={}, response={}", infoBoardIp, defResp.getBody());
                } catch (Exception e) {
                    log.warn("恢复流程-写入默认底图记录失败: {}", e.getMessage(), e);
                }

                result.setSuccess(true);
                result.setChainId(chainId);
                result.setInfoBoardIp(infoBoardIp);
                result.setInfoBoardPort(infoBoardPort);
                result.setTerminalGatewayIp(terminalGatewayIp);
                result.setMessage("停止黑屏并清除播放内容指令已发送到情报板");
                log.info("✅ 恢复流程完成: 停止黑屏 + 删除全部文件");

                // 写入恢复状态到 Redis
                writeScreenStatus(infoBoardIp, "NORMAL", dto.getOperator());
            } else {
                result.setSuccess(false);
                result.setMessage("\u7ec8\u7aef\u7f51\u5173\u8fd4\u56de\u975e\u6210\u529f\u72b6\u6001");
            }
            return result;

        } catch (Exception e) {
            log.error("\u5e94\u6025\u6062\u590d\u5f02\u5e38", e);
            result.setSuccess(false);
            result.setMessage("\u64cd\u4f5c\u5f02\u5e38: " + e.getMessage());
            return result;
        }
    }

    @Override
    public WeeklyAlarmChartVO getWeeklyAlarmChart() {
        List<Map<String, Object>> rawList = alarmMapper.countWeeklyByLevel();

        List<String> days = Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日");
        //  1=周日,2=周一,...,7=周六 -> 映射到 days 下标(0=周一..6=周日)
        int[] dayMapping = {6, 0, 1, 2, 3, 4, 5};

        // 收集所有出现的 level（保持顺序）
        Set<String> levelSet = new LinkedHashSet<>();
        rawList.forEach(row -> levelSet.add((String) row.get("alarm_level")));

        // level -> 每天的数量数组
        Map<String, long[]> levelData = new LinkedHashMap<>();
        for (String level : levelSet) {
            levelData.put(level, new long[7]);
        }

        for (Map<String, Object> row : rawList) {
            int dow = ((Number) row.get("day_of_week")).intValue();
            String level = (String) row.get("alarm_level");
            long count = ((Number) row.get("count")).longValue();
            int idx = dayMapping[dow - 1];
            levelData.get(level)[idx] += count;
        }

        // 组装 series
        List<WeeklySeriesVO> series = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : levelData.entrySet()) {
            WeeklySeriesVO s = new WeeklySeriesVO();
            s.setLevel(entry.getKey());
            List<Long> counts = new ArrayList<>();
            for (long v : entry.getValue()) counts.add(v);
            s.setCounts(counts);
            series.add(s);
        }

        WeeklyAlarmChartVO result = new WeeklyAlarmChartVO();
        result.setDays(days);
        result.setSeries(series);
        return result;
    }

    @Override
    public boolean hasPendingAlarmByIp(String ip) {
        try {
            // alarm_record 表无 info_board_ip 列，需通过 forward 服务查 chainId 再统计
            Map<String, Object> resp = forwardFeignClient.getGatewayByBoardIp(ip);
            if (resp == null || !Integer.valueOf(200).equals(resp.get("code"))) {
                log.warn("[hasPendingAlarmByIp] forward 查询链路失败: ip={}", ip);
                return false;
            }
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            if (data == null) return false;
            Object chainIdObj = data.get("chainId");
            if (chainIdObj == null) return false;
            Long chainId = Long.valueOf(chainIdObj.toString());
            Long count = alarmMapper.countPendingByChainId(chainId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("[hasPendingAlarmByIp] 查询失败，默认无告警: ip={}, error={}", ip, e.getMessage());
            return false;
        }
    }

    @Override
    public AlarmTodayStatsVO getTodayStats() {
        long total      = nvl(alarmMapper.countToday());
        long handled    = nvl(alarmMapper.countHandledToday());
        long falseAlarm = nvl(alarmMapper.countFalseAlarmToday());
        long pending    = nvl(alarmMapper.countPendingToday());
        AlarmTodayStatsVO stats = new AlarmTodayStatsVO();
        stats.setTotal(total);
        stats.setHandled(handled);
        stats.setFalseAlarm(falseAlarm);
        stats.setPending(pending);
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markFalseAlarm(FalseAlarmDTO dto) {
        AlarmRecord record = alarmMapper.selectById(dto.getAlarmId());
        if (record == null) {
            throw new RuntimeException("告警记录不存在");
        }
        if (!"pending".equals(record.getHandleStatus())) {
            throw new RuntimeException("该告警已处理，请勿重复操作");
        }

        record.setHandleStatus("false_alarm");
        // 保留原始告警级别，误报标记不降级（历史报表按级别筛选需要原始值）
        record.setHandleOperator(dto.getOperator());
        record.setHandleTime(LocalDateTime.now());
        record.setHandleRemark(dto.getReason());

        int updated = alarmMapper.updateById(record);
        log.info("标记误报: id={}, operator={}", dto.getAlarmId(), dto.getOperator());

        // 情报板联动：查链路是否还有 pending 告警，无则恢复在线
        if (updated > 0 && record.getChainId() != null) {
            Long pendingCount = alarmMapper.countPendingByChainId(record.getChainId());
            if (pendingCount == null || pendingCount == 0) {
                updateInfoBoardStatusByChainId(record.getChainId(), "在线");
            } else {
                log.info("链路 {} 仍有 {} 条未处理告警，情报板保持告警状态", record.getChainId(), pendingCount);
            }
        }

        // WebSocket 推送，通知前端刷新列表和统计数字
        if (updated > 0) {
            alarmPushService.pushAlarmFlag();
        }

        return updated > 0;
    }

    private AlarmRecord resolvePendingAlarmForDisconnect(DisconnectGatewayDTO dto, String infoBoardIp, Integer infoBoardPort) {
        if (dto == null) {
            return null;
        }
        if (dto.getAlarmId() != null && dto.getAlarmId() > 0) {
            AlarmRecord alarm = alarmMapper.selectById(dto.getAlarmId());
            if (isPendingContentAlarm(alarm, infoBoardIp, infoBoardPort, null)) {
                return alarm;
            }
            log.info("指定 alarmId 未匹配当前情报板待处理内容告警，跳过文件删除: alarmId={}, boardIp={}, boardPort={}",
                    dto.getAlarmId(), infoBoardIp, infoBoardPort);
            return null;
        }

        String contentId = normalizeContentId(dto.getContentId());
        if (contentId == null) {
            contentId = queryLatestContentIdByBoard(infoBoardIp, infoBoardPort);
        }
        if (contentId == null) {
            log.info("未获取到当前图片 contentId，无法证明当前图片属于告警内容，跳过文件删除: boardIp={}, boardPort={}",
                    infoBoardIp, infoBoardPort);
            return null;
        }

        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmRecord::getBoardIp, infoBoardIp)
                .eq(AlarmRecord::getHandleStatus, "pending")
                .eq(AlarmRecord::getContentId, contentId)
                .orderByDesc(AlarmRecord::getAlarmTime)
                .last("LIMIT 1");
        if (infoBoardPort != null) {
            wrapper.eq(AlarmRecord::getBoardPort, infoBoardPort);
        }
        AlarmRecord alarm = alarmMapper.selectOne(wrapper);
        if (alarm == null) {
            log.info("当前 contentId 无待处理告警，跳过文件删除: boardIp={}, boardPort={}, contentId={}",
                    infoBoardIp, infoBoardPort, contentId);
        }
        return alarm;
    }

    private boolean isPendingContentAlarm(AlarmRecord alarm, String infoBoardIp, Integer infoBoardPort, String contentId) {
        if (alarm == null) {
            return false;
        }
        if (!"pending".equals(alarm.getHandleStatus())) {
            return false;
        }
        if (!Objects.equals(alarm.getBoardIp(), infoBoardIp)) {
            return false;
        }
        if (infoBoardPort != null && alarm.getBoardPort() != null && !Objects.equals(alarm.getBoardPort(), infoBoardPort)) {
            return false;
        }
        String alarmContentId = normalizeContentId(alarm.getContentId());
        if (alarmContentId == null) {
            return false;
        }
        String currentContentId = normalizeContentId(contentId);
        return currentContentId == null || alarmContentId.equals(currentContentId);
    }

    private boolean isCurrentBoardContentMatched(AlarmRecord alarm, String infoBoardIp, Integer infoBoardPort, String requestContentId) {
        String alarmContentId = normalizeContentId(alarm != null ? alarm.getContentId() : null);
        if (alarmContentId == null) {
            return false;
        }

        String explicitContentId = normalizeContentId(requestContentId);
        if (explicitContentId != null) {
            boolean matched = alarmContentId.equals(explicitContentId);
            if (!matched) {
                log.info("请求 contentId 与待处理告警不一致，跳过文件删除: alarmId={}, alarmContentId={}, requestContentId={}",
                        alarm.getId(), alarmContentId, explicitContentId);
            }
            return matched;
        }

        String latestContentId = queryLatestContentIdByBoard(infoBoardIp, infoBoardPort);
        boolean matched = alarmContentId.equals(latestContentId);
        if (!matched) {
            log.info("当前情报板最新内容不属于待处理告警，跳过文件删除: alarmId={}, alarmContentId={}, latestContentId={}",
                    alarm.getId(), alarmContentId, latestContentId);
        }
        return matched;
    }

    @SuppressWarnings("unchecked")
    private String queryLatestContentIdByBoard(String boardIp, Integer boardPort) {
        try {
            String url = contentServiceUrl + "/content/latest-by-board?boardIp=" + URLEncoder.encode(boardIp, "UTF-8");
            if (boardPort != null) {
                url += "&boardPort=" + boardPort;
            }
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("查询情报板最新内容返回非成功状态: boardIp={}, boardPort={}", boardIp, boardPort);
                return null;
            }
            Object dataObj = response.getBody().get("data");
            if (!(dataObj instanceof Map)) {
                return null;
            }
            Object contentId = ((Map<String, Object>) dataObj).get("contentId");
            return normalizeContentId(contentId != null ? contentId.toString() : null);
        } catch (Exception e) {
            log.warn("查询情报板最新内容失败: boardIp={}, boardPort={}, error={}", boardIp, boardPort, e.getMessage());
            return null;
        }
    }

    private String normalizeContentId(String contentId) {
        if (contentId == null) {
            return null;
        }
        String value = contentId.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 从 content 服务查询指定 contentId 的文件名
     * 调用 GET /content/detail/{contentId}，从返回的 data.fileName 中提取文件名
     *
     * @param contentId 内容记录 ID
     * @return 文件名（如 "bao4.jpg"），查询失败或无结果时返回 null
     */
    @SuppressWarnings("unchecked")
    private String queryFileNameFromContent(String contentId) {
        try {
            String url = contentServiceUrl + "/content/detail/" + contentId;
            log.info("查询违规内容文件名: GET {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Boolean.TRUE.equals(body.get("success"))) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    if (data != null) {
                        String fileName = (String) data.get("fileName");
                        log.info("查询到文件名: contentId={}, fileName={}", contentId, fileName);
                        return fileName;
                    }
                }
            }
            log.warn("查询内容文件名未获取到结果: contentId={}", contentId);
        } catch (Exception e) {
            log.warn("查询内容文件名失败: contentId={}, error={}", contentId, e.getMessage());
        }
        return null;
    }

    /**
     * 构建 JetFileII Type3 格式的文件删除指令 HEX

     * @param filePath 情报板磁盘路径，如 "D:\P\bao2.jpg"
     * @return HEX 字符串，构建失败返回 null
     */
    private String buildDeleteFileHex(String filePath) {
        try {
            byte[] pathBytes = filePath.getBytes("GBK");

            // argLen 按协议要求: 参数区 = argLen * 4 字节，路径+null 需对齐到 4 字节边界
            // Wireshark 抓包验证: "D:\P\bao4.jpg"(13字节) → argLen=4 → 参数区16字节 → 3字节null填充
            int argBytes = ((pathBytes.length + 1 + 3) / 4) * 4;  // ceil to 4-byte boundary
            int argLen = argBytes / 4;
            int totalLen = 16 + argBytes;  // 固定头部 16 + 参数区
            byte[] packet = new byte[totalLen]; // Java 默认初始化为 0x00

            packet[0] = 0x55;              // sync
            packet[1] = (byte) 0xa7;       // SUM 和校验模式
            // packet[2-3]: 校验和，下方计算后回填
            // packet[4-5] = 0x00          // dataLen
            // packet[6-7] = 0x00          // sourceAddr
            packet[8] = 0x01;              // dest GG
            packet[9] = 0x01;              // dest UU
            // packet[10-11] = 0x00        // serial
            packet[12] = 0x07;             // mainCmd: 文件管理
            packet[13] = 0x06;             // subCmd: 删除文件
            packet[14] = (byte) argLen;    // argLen: 参数区4字节组数
            // packet[15] = 0x00           // flag

            // 写入文件路径到参数区（byte 16 起），剩余自动为 0x00（null 填充对齐）
            System.arraycopy(pathBytes, 0, packet, 16, pathBytes.length);

            // 计算 SUM 和校验（范围: 字节 4 到末尾，简单字节求和）
            int sum = 0;
            for (int i = 4; i < totalLen; i++) {
                sum += (packet[i] & 0xFF);
            }
            // 小端序写入字节 2-3
            packet[2] = (byte) (sum & 0xFF);
            packet[3] = (byte) ((sum >> 8) & 0xFF);

            // 转 HEX 字符串
            StringBuilder sb = new StringBuilder(totalLen * 2);
            for (byte b : packet) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            String hex = sb.toString();
            log.info("构建删除文件指令(SUM模式): filePath={}, sum=0x{}, hexLen={}, hex={}",
                    filePath, String.format("%04X", sum), totalLen, hex);
            return hex;
        } catch (Exception e) {
            log.warn("构建删除文件指令失败: filePath={}, error={}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * CRC-16 MODBUS 校验算法
     * 多项式: 0xA001, 初始值: 0xFFFF
     * 参考: NovaFrameParser.calculateCrc16()
     */
    private static int calculateCrc16Modbus(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /** null 安全转 long */
    private long nvl(Long v) { return v == null ? 0L : v; }
}
    
