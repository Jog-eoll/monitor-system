package com.monitorplatform.content.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.entity.dto.AlarmReceiveRequestDTO;
import com.monitorplatform.content.entity.dto.DetectionRecordQueryDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import com.monitorplatform.content.feign.AlarmFeignClient;
import com.monitorplatform.content.mapper.ContentMonitorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class ContentDetectionService {

    @Autowired
    private QwenApiService qwenApiService;

    @Autowired
    private LocalAuditService localAuditService;

    @Autowired
    private RuleConfigService ruleConfigService;

    @Autowired
    private ContentMonitorMapper contentMonitorMapper;

    @Autowired
    private AlarmFeignClient alarmFeignClient;

    @Autowired
    @Lazy
    private ContentDetectionService self;

    @Value("${audit.mode:qwen}")
    private String auditMode;

    @Value("${content-detection.dedupe-window-seconds:300}")
    private long dedupeWindowSeconds;

    @Value("${content-detection.local-violation-min-confidence:0.75}")
    private double localViolationMinConfidence;

    private final ConcurrentMap<String, Object> dedupeLocks = new ConcurrentHashMap<>();

    public ContentMonitor detectContent(QwenDetectionRequestDTO request) {
        log.info("start detect content, businessId={}, deviceId={}", request.getBusinessId(), request.getDeviceId());

        String contentType = (request.getContentType() != null && !request.getContentType().isEmpty())
                ? request.getContentType() : "image";
        DetectionRecordResolution resolution = getOrCreatePendingRecord(request, contentType);
        ContentMonitor record = resolution.record;
        if (!resolution.created) {
            return record;
        }

        if ("video".equals(contentType)) {
            self.asyncDetectAndUpdate(record, request);
            return record;
        }

        try {
            QwenDetectionResultDTO apiResult;
            if ("local".equalsIgnoreCase(auditMode)) {
                if ("text".equals(contentType)) {
                    apiResult = localAuditService.auditText(request.getScreenshotBase64(),
                            request.getBusinessId(), request.getDeviceId());
                } else {
                    apiResult = localAuditService.auditImage(request);
                }
            } else {
                apiResult = qwenApiService.detectContent(request);
            }

            applyDetectionResult(record, apiResult);
            if (Integer.valueOf(1).equals(record.getIsViolation())) {
                triggerAlarm(record, request, apiResult);
            }
            return record;
        } catch (Exception e) {
            log.error("detect failed, keep pending, businessId={}", request.getBusinessId(), e);
            record.setStatus("pending");
            record.setErrorMessage(e.getMessage());
            record.setUpdateTime(LocalDateTime.now());
            contentMonitorMapper.updateById(record);
            return record;
        }
    }

    private DetectionRecordResolution getOrCreatePendingRecord(QwenDetectionRequestDTO request, String contentType) {
        String dedupeKey = buildDedupeKey(request, contentType);
        if (dedupeKey == null) {
            ContentMonitor existing = findExistingRecord(request, contentType);
            return existing != null
                    ? DetectionRecordResolution.existing(existing)
                    : DetectionRecordResolution.created(createPendingRecord(request, contentType));
        }

        Object lock = dedupeLocks.computeIfAbsent(dedupeKey, key -> new Object());
        try {
            synchronized (lock) {
                ContentMonitor existing = findExistingRecord(request, contentType);
                return existing != null
                        ? DetectionRecordResolution.existing(existing)
                        : DetectionRecordResolution.created(createPendingRecord(request, contentType));
            }
        } finally {
            dedupeLocks.remove(dedupeKey, lock);
        }
    }

    private ContentMonitor findExistingRecord(QwenDetectionRequestDTO request, String contentType) {
        ContentMonitor existsRecord = contentMonitorMapper.selectByContentId(request.getBusinessId());
        if (existsRecord != null) {
            log.warn("record already exists, businessId={}", request.getBusinessId());
            return existsRecord;
        }

        ContentMonitor duplicateRecord = findRecentDuplicateByMinioPath(request, contentType);
        if (duplicateRecord != null) {
            log.warn("duplicate minioPath detect ignored, businessId={}, existingContentId={}, minioPath={}, windowSeconds={}",
                    request.getBusinessId(), duplicateRecord.getContentId(), request.getMinioPath(), dedupeWindowSeconds);
            return duplicateRecord;
        }
        return null;
    }

    private ContentMonitor findRecentDuplicateByMinioPath(QwenDetectionRequestDTO request, String contentType) {
        if (dedupeWindowSeconds <= 0 || StrUtil.isBlank(request.getMinioPath())) {
            return null;
        }
        ContentMonitor duplicate = contentMonitorMapper.selectLatestByMinioPathAndType(request.getMinioPath(), contentType);
        if (duplicate == null) {
            return null;
        }
        LocalDateTime referenceTime = duplicate.getCreateTime() != null ? duplicate.getCreateTime() : duplicate.getReceiveTime();
        if (referenceTime == null) {
            return duplicate;
        }
        long ageSeconds = Math.abs(Duration.between(referenceTime, LocalDateTime.now()).getSeconds());
        return ageSeconds <= dedupeWindowSeconds ? duplicate : null;
    }

    private String buildDedupeKey(QwenDetectionRequestDTO request, String contentType) {
        if (StrUtil.isBlank(request.getMinioPath())) {
            return null;
        }
        return contentType + ":" + request.getMinioPath().trim();
    }

    private ContentMonitor createPendingRecord(QwenDetectionRequestDTO request, String contentType) {
        ContentMonitor record = new ContentMonitor();
        record.setContentId(request.getBusinessId());
        record.setGatewayId(request.getDeviceId());
        record.setContentType(contentType);
        if (request.getMinioPath() != null && !request.getMinioPath().isEmpty()) {
            record.setMinioPath(request.getMinioPath());
            record.setData(null);
        } else {
            record.setData(request.getScreenshotBase64());
        }
        record.setFileName(request.getFileName());
        record.setSourceIp(request.getSourceIp());
        record.setDescription(request.getDescription() != null ? request.getDescription()
                : request.getDeviceName() + ("video".equals(contentType) ? " 视频" : " 截图")
                + request.getCaptureTime());
        record.setChainId(request.getChainId());
        record.setBoardIp(request.getBoardIp());
        record.setBoardPort(request.getBoardPort());
        record.setPlayBatchId(StrUtil.isBlank(request.getPlayBatchId()) ? null : request.getPlayBatchId().trim());
        record.setPlayBatchSeq(request.getPlayBatchSeq());
        record.setPlayBatchSize(request.getPlayBatchSize());
        record.setPublishRequestId(StrUtil.isBlank(request.getPublishRequestId()) ? null : request.getPublishRequestId().trim());
        record.setStatus("pending");
        record.setIsViolation(0);
        record.setReceiveTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        contentMonitorMapper.insert(record);
        return record;
    }

    private static class DetectionRecordResolution {
        private final ContentMonitor record;
        private final boolean created;

        private DetectionRecordResolution(ContentMonitor record, boolean created) {
            this.record = record;
            this.created = created;
        }

        private static DetectionRecordResolution created(ContentMonitor record) {
            return new DetectionRecordResolution(record, true);
        }

        private static DetectionRecordResolution existing(ContentMonitor record) {
            return new DetectionRecordResolution(record, false);
        }
    }

    @Async
    public void asyncDetectAndUpdate(ContentMonitor record, QwenDetectionRequestDTO request) {
        try {
            QwenDetectionResultDTO apiResult;
            if ("local".equalsIgnoreCase(auditMode)) {
                apiResult = localAuditService.auditVideo(request);
            } else {
                apiResult = qwenApiService.detectContent(request);
            }

            applyDetectionResult(record, apiResult);
            if (Integer.valueOf(1).equals(record.getIsViolation())) {
                triggerAlarm(record, request, apiResult);
            }
        } catch (Exception e) {
            log.error("async detect failed, businessId={}", record.getContentId(), e);
            record.setStatus("pending");
            record.setErrorMessage("视频检测失败: " + e.getMessage());
            record.setUpdateTime(LocalDateTime.now());
            contentMonitorMapper.updateById(record);
        }
    }

    private void applyDetectionResult(ContentMonitor record, QwenDetectionResultDTO apiResult) {
        if (apiResult == null) {
            record.setIsViolation(null);
            record.setStatus("pending");
            record.setReason("detection result is null");
            record.setRecognitionTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            contentMonitorMapper.updateById(record);
            return;
        }
        record.setRequestId(apiResult.getRequestId());
        record.setViolationType(apiResult.getViolationType());
        record.setReason(apiResult.getReason());
        double confidence = 0.0;
        if (apiResult.getConfidence() != null) {
            confidence = apiResult.getConfidence() / 100.0;
            record.setConfidence(confidence);
        }
        if ("violation".equals(apiResult.getDetectionResult())) {
            if (shouldDowngradeLocalLowConfidence(record, confidence)) {
                record.setIsViolation(0);
                record.setStatus("normal");
                record.setViolationType("none");
                record.setReason(buildLowConfidenceReason(apiResult.getReason(), confidence));
                log.warn("downgrade local low-confidence violation, businessId={}, contentType={}, confidence={}, threshold={}",
                        record.getContentId(), record.getContentType(), confidence, localViolationMinConfidence);
            } else {
                record.setIsViolation(1);
                record.setStatus("violation");
            }
        } else if ("pending".equals(apiResult.getDetectionResult())) {
            record.setIsViolation(null);
            record.setStatus("pending");
        } else {
            record.setIsViolation(0);
            record.setStatus("normal");
        }
        record.setRecognitionTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        contentMonitorMapper.updateById(record);
    }

    private boolean shouldDowngradeLocalLowConfidence(ContentMonitor record, double confidence) {
        if (!"local".equalsIgnoreCase(auditMode)) {
            return false;
        }
        if (localViolationMinConfidence <= 0 || confidence <= 0) {
            return false;
        }
        String contentType = record.getContentType();
        if (!"image".equals(contentType) && !"video".equals(contentType)) {
            return false;
        }
        return confidence < localViolationMinConfidence;
    }

    private String buildLowConfidenceReason(String originalReason, double confidence) {
        String prefix = String.format(Locale.ROOT,
                "local audit low confidence downgraded, confidence=%.2f, threshold=%.2f",
                confidence, localViolationMinConfidence);
        if (StrUtil.isBlank(originalReason)) {
            return prefix;
        }
        return prefix + "; originalReason=" + originalReason;
    }

    public Page<ContentMonitor> pageRecords(DetectionRecordQueryDTO dto) {
        Page<ContentMonitor> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<ContentMonitor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(dto.getDeviceId()), ContentMonitor::getGatewayId, dto.getDeviceId())
                .eq(StrUtil.isNotBlank(dto.getViolationType()), ContentMonitor::getViolationType, dto.getViolationType())
                .eq(StrUtil.isNotBlank(dto.getStatus()), ContentMonitor::getStatus, dto.getStatus())
                .ge(StrUtil.isNotBlank(dto.getStartTime()), ContentMonitor::getRecognitionTime, dto.getStartTime())
                .le(StrUtil.isNotBlank(dto.getEndTime()), ContentMonitor::getRecognitionTime, dto.getEndTime());
        if ("violation".equals(dto.getDetectionResult())) {
            wrapper.eq(ContentMonitor::getIsViolation, 1);
        } else if ("compliant".equals(dto.getDetectionResult())) {
            wrapper.eq(ContentMonitor::getIsViolation, 0);
        }
        wrapper.orderByDesc(ContentMonitor::getRecognitionTime);
        return contentMonitorMapper.selectPage(page, wrapper);
    }

    public DetectionStatisticsDTO getStatistics() {
        DetectionStatisticsDTO dto = new DetectionStatisticsDTO();
        dto.setTodayTotal(contentMonitorMapper.countToday());
        dto.setTodayViolation(contentMonitorMapper.countViolationToday());
        dto.setTodayCompliant(contentMonitorMapper.countCompliantToday());
        if (dto.getTodayTotal() != null && dto.getTodayTotal() > 0) {
            double violationRate = (dto.getTodayViolation() * 100.0) / dto.getTodayTotal();
            dto.setViolationRate(Math.round(violationRate * 100.0) / 100.0);
        } else {
            dto.setViolationRate(0.0);
        }
        return dto;
    }

    public void syncRuleConfig() {
        List<RuleConfigService.DetectionRuleVO> rules = ruleConfigService.getEnabledDetectionRules();
        List<RuleConfigService.SensitiveKeywordVO> keywords = ruleConfigService.getEnabledKeywords();
        log.info("sync rules done, rules={}, keywords={}", rules.size(), keywords.size());
    }

    private void triggerAlarm(ContentMonitor record, QwenDetectionRequestDTO request, QwenDetectionResultDTO apiResult) {
        try {
            AlarmReceiveRequestDTO alarmReq = new AlarmReceiveRequestDTO();
            alarmReq.setAlarmType(mapViolationTypeToAlarmType(record.getViolationType()));
            alarmReq.setAlarmLevel(calculateAlarmLevel(record, apiResult));
            alarmReq.setChainId(record.getChainId());
            alarmReq.setDeviceId(record.getGatewayId());
            alarmReq.setDeviceName(request.getDeviceName());
            alarmReq.setContentId(record.getContentId());
            alarmReq.setViolationType(getViolationTypeName(record.getViolationType()));
            alarmReq.setViolationDetail(String.format(
                    "检测到违规内容，类型：%s，置信度：%.0f%%，依据：%s",
                    getViolationTypeName(record.getViolationType()),
                    record.getConfidence() != null ? record.getConfidence() * 100 : 0,
                    record.getReason()
            ));
            alarmReq.setAlarmTime(LocalDateTime.now().toString());
            alarmReq.setBoardIp(record.getBoardIp());
            alarmReq.setBoardPort(record.getBoardPort());

            CommonServiceResponseVO<Object> response = alarmFeignClient.receiveAlarm(alarmReq);
            if (response == null || response.getCode() == null || response.getCode() != 200) {
                log.warn("trigger alarm returned non-200, businessId={}, code={}",
                        record.getContentId(), response == null ? null : response.getCode());
            }
        } catch (Exception e) {
            log.error("trigger alarm failed, businessId={}", record.getContentId(), e);
        }
    }

    private String mapViolationTypeToAlarmType(String violationType) {
        if ("pornography".equals(violationType)) {
            return "content_violation_pornography";
        }
        if ("violence".equals(violationType)) {
            return "content_violation_violence";
        }
        if ("sensitive".equals(violationType)) {
            return "content_violation_sensitive";
        }
        return "content_violation_other";
    }

    private String calculateAlarmLevel(ContentMonitor record, QwenDetectionResultDTO apiResult) {
        String mappedLevel = mapLocalViolationLevel(apiResult != null ? apiResult.getViolationLevel() : null);
        if (mappedLevel != null) {
            return mappedLevel;
        }
        String violationType = record.getViolationType();
        double conf = record.getConfidence() != null ? record.getConfidence() : 0.0;
        if ("pornography".equals(violationType) || "violence".equals(violationType)) {
            return conf >= 0.9 ? "critical" : "serious";
        }
        if ("sensitive".equals(violationType)) {
            return conf >= 0.9 ? "serious" : "general";
        }
        return "general";
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

    private String getViolationTypeName(String violationType) {
        if ("pornography".equals(violationType)) {
            return "色情内容";
        }
        if ("violence".equals(violationType)) {
            return "暴力内容";
        }
        if ("sensitive".equals(violationType)) {
            return "敏感信息";
        }
        return "其他违规";
    }

    public static class DetectionStatisticsDTO {
        private Long todayTotal;
        private Long todayViolation;
        private Long todayCompliant;
        private Double violationRate;

        public Long getTodayTotal() {
            return todayTotal;
        }

        public void setTodayTotal(Long todayTotal) {
            this.todayTotal = todayTotal;
        }

        public Long getTodayViolation() {
            return todayViolation;
        }

        public void setTodayViolation(Long todayViolation) {
            this.todayViolation = todayViolation;
        }

        public Long getTodayCompliant() {
            return todayCompliant;
        }

        public void setTodayCompliant(Long todayCompliant) {
            this.todayCompliant = todayCompliant;
        }

        public Double getViolationRate() {
            return violationRate;
        }

        public void setViolationRate(Double violationRate) {
            this.violationRate = violationRate;
        }
    }
}
