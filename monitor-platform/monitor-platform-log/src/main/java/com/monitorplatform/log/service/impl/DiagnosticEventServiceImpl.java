package com.monitorplatform.log.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.monitorplatform.common.exception.BusinessException;
import com.monitorplatform.common.log.OperateLogWriter;
import com.monitorplatform.log.dto.DiagnosticEventQueryDTO;
import com.monitorplatform.log.dto.DiagnosticEventReportDTO;
import com.monitorplatform.log.entity.DiagnosticEventLog;
import com.monitorplatform.log.mapper.DiagnosticEventMapper;
import com.monitorplatform.log.service.DiagnosticEventService;
import com.monitorplatform.log.vo.ReportResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DiagnosticEventServiceImpl extends ServiceImpl<DiagnosticEventMapper, DiagnosticEventLog>
        implements DiagnosticEventService {

    @Value("${log.report-token:}")
    private String configuredReportToken;

    @Value("${log.dedup-window-seconds:60}")
    private long dedupWindowSeconds;

    @Value("${log.retention-days:90}")
    private long retentionDays;

    @Autowired(required = false)
    private OperateLogWriter operateLogWriter;

    private static final String EVENT_CLIENT_CONTENT_SIGNED = "CLIENT_CONTENT_SIGNED";
    private static final String EVENT_CLIENT_PUBLISH_PERMIT_SIGNED = "CLIENT_PUBLISH_PERMIT_SIGNED";
    private static final String EVENT_CLIENT_PACKAGE_VERIFIED = "CLIENT_PACKAGE_VERIFIED";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResultVO report(DiagnosticEventReportDTO dto, String reportToken) {
        validateReportToken(reportToken);
        validateReportDTO(dto);

        LocalDateTime now = LocalDateTime.now();
        DiagnosticEventLog duplicated = findDuplicated(dto, now);
        if (duplicated != null) {
            LambdaUpdateWrapper<DiagnosticEventLog> updateWrapper = new LambdaUpdateWrapper<DiagnosticEventLog>()
                    .eq(DiagnosticEventLog::getId, duplicated.getId())
                    .set(DiagnosticEventLog::getLastRepeatTime, now)
                    .set(DiagnosticEventLog::getUpdateTime, now)
                    .setSql("repeat_count = repeat_count + 1");
            update(updateWrapper);
            return new ReportResultVO(duplicated.getEventId(), true);
        }

        DiagnosticEventLog entity = buildEntity(dto, now);
        save(entity);
        bridgeToOperationLog(entity);
        return new ReportResultVO(entity.getEventId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReportResultVO> batchReport(List<DiagnosticEventReportDTO> dtoList, String reportToken) {
        validateReportToken(reportToken);
        if (CollectionUtils.isEmpty(dtoList)) {
            throw new BusinessException(400, "events cannot be empty");
        }
        if (dtoList.size() > 100) {
            throw new BusinessException(400, "batch size cannot exceed 100");
        }
        List<ReportResultVO> resultList = new ArrayList<ReportResultVO>(dtoList.size());
        for (DiagnosticEventReportDTO dto : dtoList) {
            resultList.add(reportWithoutTokenCheck(dto));
        }
        return resultList;
    }

    private ReportResultVO reportWithoutTokenCheck(DiagnosticEventReportDTO dto) {
        validateReportDTO(dto);
        LocalDateTime now = LocalDateTime.now();
        DiagnosticEventLog duplicated = findDuplicated(dto, now);
        if (duplicated != null) {
            LambdaUpdateWrapper<DiagnosticEventLog> updateWrapper = new LambdaUpdateWrapper<DiagnosticEventLog>()
                    .eq(DiagnosticEventLog::getId, duplicated.getId())
                    .set(DiagnosticEventLog::getLastRepeatTime, now)
                    .set(DiagnosticEventLog::getUpdateTime, now)
                    .setSql("repeat_count = repeat_count + 1");
            update(updateWrapper);
            return new ReportResultVO(duplicated.getEventId(), true);
        }
        DiagnosticEventLog entity = buildEntity(dto, now);
        save(entity);
        bridgeToOperationLog(entity);
        return new ReportResultVO(entity.getEventId(), false);
    }

    @Override
    public IPage<DiagnosticEventLog> page(DiagnosticEventQueryDTO queryDTO) {
        DiagnosticEventQueryDTO query = queryDTO == null ? new DiagnosticEventQueryDTO() : queryDTO;
        Page<DiagnosticEventLog> page = PageSupport.page(query);
        LambdaQueryWrapper<DiagnosticEventLog> wrapper = new LambdaQueryWrapper<DiagnosticEventLog>()
                .ge(query.getStartTime() != null, DiagnosticEventLog::getEventTime, query.getStartTime())
                .le(query.getEndTime() != null, DiagnosticEventLog::getEventTime, query.getEndTime())
                .eq(StringUtils.hasText(query.getEventType()), DiagnosticEventLog::getEventType, query.getEventType())
                .eq(StringUtils.hasText(query.getEventLevel()), DiagnosticEventLog::getEventLevel, query.getEventLevel())
                .eq(StringUtils.hasText(query.getStage()), DiagnosticEventLog::getStage, query.getStage())
                .eq(StringUtils.hasText(query.getServiceName()), DiagnosticEventLog::getServiceName, query.getServiceName())
                .eq(StringUtils.hasText(query.getTraceId()), DiagnosticEventLog::getTraceId, query.getTraceId())
                .eq(query.getChainId() != null, DiagnosticEventLog::getChainId, query.getChainId())
                .eq(StringUtils.hasText(query.getContentId()), DiagnosticEventLog::getContentId, query.getContentId())
                .eq(StringUtils.hasText(query.getSourceIp()), DiagnosticEventLog::getSourceIp, query.getSourceIp())
                .eq(StringUtils.hasText(query.getBoardIp()), DiagnosticEventLog::getBoardIp, query.getBoardIp())
                .eq(StringUtils.hasText(query.getOperatorId()), DiagnosticEventLog::getOperatorId, query.getOperatorId())
                .like(StringUtils.hasText(query.getOperatorName()), DiagnosticEventLog::getOperatorName, query.getOperatorName())
                .eq(StringUtils.hasText(query.getUkeyId()), DiagnosticEventLog::getUkeyId, query.getUkeyId())
                .eq(StringUtils.hasText(query.getCertSerialNo()), DiagnosticEventLog::getCertSerialNo, query.getCertSerialNo())
                .eq(StringUtils.hasText(query.getResultStatus()), DiagnosticEventLog::getResultStatus, query.getResultStatus())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(DiagnosticEventLog::getSummary, query.getKeyword())
                        .or()
                        .like(DiagnosticEventLog::getErrorMessage, query.getKeyword())
                        .or()
                        .like(DiagnosticEventLog::getContentId, query.getKeyword())
                        .or()
                        .like(DiagnosticEventLog::getTraceId, query.getKeyword()))
                .orderByDesc(DiagnosticEventLog::getEventTime)
                .orderByDesc(DiagnosticEventLog::getId);
        return page(page, wrapper);
    }

    @Override
    public int cleanupExpired() {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
        int deleted = baseMapper.delete(new LambdaQueryWrapper<DiagnosticEventLog>()
                .lt(DiagnosticEventLog::getEventTime, beforeTime));
        if (deleted > 0) {
            log.info("diagnostic event cleanup executed, beforeTime={}, deleted={}", beforeTime, deleted);
        }
        return deleted;
    }

    private void validateReportToken(String reportToken) {
        if (!StringUtils.hasText(configuredReportToken)) {
            return;
        }
        if (!configuredReportToken.equals(reportToken)) {
            throw new BusinessException(401, "invalid log report token");
        }
    }

    private void validateReportDTO(DiagnosticEventReportDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "event cannot be empty");
        }
        if (!StringUtils.hasText(dto.getEventType())) {
            throw new BusinessException(400, "eventType cannot be empty");
        }
        if (StringUtils.hasText(dto.getDetailJson())) {
            try {
                com.alibaba.fastjson2.JSON.parse(dto.getDetailJson());
            } catch (Exception e) {
                throw new BusinessException(400, "detailJson must be valid json");
            }
        }
    }

    private DiagnosticEventLog findDuplicated(DiagnosticEventReportDTO dto, LocalDateTime now) {
        if (!StringUtils.hasText(dto.getDedupKey()) || dedupWindowSeconds <= 0) {
            return null;
        }
        LocalDateTime windowStart = now.minusSeconds(dedupWindowSeconds);
        return getOne(new LambdaQueryWrapper<DiagnosticEventLog>()
                .eq(DiagnosticEventLog::getDedupKey, dto.getDedupKey())
                .ge(DiagnosticEventLog::getLastRepeatTime, windowStart)
                .orderByDesc(DiagnosticEventLog::getLastRepeatTime)
                .last("LIMIT 1"), false);
    }

    private DiagnosticEventLog buildEntity(DiagnosticEventReportDTO dto, LocalDateTime now) {
        DiagnosticEventLog entity = new DiagnosticEventLog();
        BeanUtils.copyProperties(dto, entity);
        entity.setEventId(UUID.randomUUID().toString().replace("-", ""));
        entity.setEventTime(dto.getEventTime() == null ? now : dto.getEventTime());
        entity.setEventLevel(defaultText(dto.getEventLevel(), "info"));
        entity.setResultStatus(defaultText(dto.getResultStatus(), "success"));
        entity.setRepeatCount(1);
        entity.setLastRepeatTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void bridgeToOperationLog(DiagnosticEventLog entity) {
        if (operateLogWriter == null || !StringUtils.hasText(entity.getEventType())) {
            return;
        }
        String eventType = entity.getEventType();
        Map<String, Object> detailMap = parseDetailJson(entity.getDetailJson());

        if (EVENT_CLIENT_CONTENT_SIGNED.equals(eventType)) {
            bridgeClientContentSigned(entity, detailMap);
        } else if (EVENT_CLIENT_PUBLISH_PERMIT_SIGNED.equals(eventType)) {
            bridgeClientPermitSigned(entity, detailMap);
        } else if (EVENT_CLIENT_PACKAGE_VERIFIED.equals(eventType)) {
            bridgeClientPackageVerified(entity, detailMap);
        }
    }

    private void bridgeClientContentSigned(DiagnosticEventLog entity, Map<String, Object> detailMap) {
        String actorName = firstNonBlank(entity.getOperatorName(), entity.getOperatorId(), entity.getSourceIp());
        String actObj = nonEmpty(detailMap, "fileHash", entity.getContentId(), entity.getRefId());
        String clientIp = firstNonBlank(entity.getClientIp(), entity.getSourceIp());
        Map<String, Object> detail = new LinkedHashMap<>();
        putIfPresent(detail, "requestId", entity.getTraceId());
        putIfPresent(detail, "packageName", detailMap, "packageName");
        putIfPresent(detail, "fileName", detailMap, "fileName");
        putIfPresent(detail, "fileHash", detailMap, "fileHash");
        putIfPresent(detail, "fileSize", detailMap, "fileSize");
        putIfPresent(detail, "signatureProvider", detailMap, "signatureProvider");
        putIfPresent(detail, "keyId", detailMap, "keyId");
        putIfPresent(detail, "auditDecision", detailMap, "auditDecision");
        boolean success = "success".equalsIgnoreCase(entity.getResultStatus());
        if (!success && StringUtils.hasText(entity.getErrorMessage())) {
            detail.put("error", entity.getErrorMessage());
        }
        operateLogWriter.writeClientSign("客户端签名发布包", actorName, actObj, clientIp, success, detail);
    }

    private void bridgeClientPermitSigned(DiagnosticEventLog entity, Map<String, Object> detailMap) {
        String actorName = firstNonBlank(entity.getOperatorName(), entity.getOperatorId(), entity.getSourceIp());
        String actObj = nonEmpty(detailMap, "playlistDigest", entity.getContentId(), entity.getRefId());
        String clientIp = firstNonBlank(entity.getClientIp(), entity.getSourceIp());
        Map<String, Object> detail = new LinkedHashMap<>();
        putIfPresent(detail, "jti", detailMap, "jti");
        putIfPresent(detail, "requestId", entity.getTraceId());
        putIfPresent(detail, "playlistDigest", detailMap, "playlistDigest");
        putIfPresent(detail, "operatorId", entity.getOperatorId());
        putIfPresent(detail, "target", detailMap, "target");
        putIfPresent(detail, "expireSeconds", detailMap, "expireSeconds");
        boolean success = "success".equalsIgnoreCase(entity.getResultStatus());
        if (!success && StringUtils.hasText(entity.getErrorMessage())) {
            detail.put("error", entity.getErrorMessage());
        }
        operateLogWriter.writeClientSign("客户端签发发布许可", actorName, actObj, clientIp, success, detail);
    }

    private void bridgeClientPackageVerified(DiagnosticEventLog entity, Map<String, Object> detailMap) {
        String actorName = firstNonBlank(entity.getOperatorName(), entity.getOperatorId(), entity.getSourceIp());
        String actObj = nonEmpty(detailMap, "fileHash", entity.getContentId(), entity.getRefId());
        String clientIp = firstNonBlank(entity.getClientIp(), entity.getSourceIp());
        Map<String, Object> detail = new LinkedHashMap<>();
        putIfPresent(detail, "packageName", detailMap, "packageName");
        putIfPresent(detail, "payloadName", detailMap, "payloadName");
        putIfPresent(detail, "fileHash", detailMap, "fileHash");
        putIfPresent(detail, "status", detailMap, "status");
        putIfPresent(detail, "reason", detailMap, "reason");
        boolean success = "success".equalsIgnoreCase(entity.getResultStatus());
        if (!success && StringUtils.hasText(entity.getErrorMessage())) {
            detail.put("error", entity.getErrorMessage());
        }
        operateLogWriter.writeClientVerify(actorName, actObj, clientIp, success, detail);
    }

    private Map<String, Object> parseDetailJson(String detailJson) {
        if (!StringUtils.hasText(detailJson)) {
            return null;
        }
        try {
            return JSON.parseObject(detailJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String nonEmpty(Map<String, Object> detailMap, String key, String... fallbacks) {
        if (detailMap != null) {
            Object value = detailMap.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return firstNonBlank(fallbacks);
    }

    private void putIfPresent(Map<String, Object> target, String key, Map<String, Object> source, String sourceKey) {
        if (source != null) {
            Object value = source.get(sourceKey);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                target.put(key, String.valueOf(value).trim());
            }
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
