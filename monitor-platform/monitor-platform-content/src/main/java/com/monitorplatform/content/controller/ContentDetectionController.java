package com.monitorplatform.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.entity.dto.DetectionRecordQueryDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import com.monitorplatform.content.service.ContentDetectionService;
import com.monitorplatform.content.service.LocalAuditService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/content/detection")
public class ContentDetectionController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ContentDetectionService detectionService;

    @Autowired
    private LocalAuditService localAuditService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private com.monitorplatform.common.util.MinioUtil minioUtil;

    @Value("${minio.bucket-name:monitor-content}")
    private String minioBucket;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${pre-audit.enabled:true}")
    private boolean preAuditEnabled;

    @Value("${pre-audit.bucket-name:client-preaudit-content}")
    private String preAuditBucket;

    @Value("${pre-audit.max-file-size-mb:500}")
    private int preAuditMaxFileSizeMb;

    @Value("${pre-audit.sync-timeout-ms:120000}")
    private long preAuditSyncTimeoutMs;

    @PostMapping("/detect")
    public Result<ContentMonitor> detect(@RequestBody QwenDetectionRequestDTO request) {
        try {
            ContentMonitor record = detectionService.detectContent(request);
            record.setViolationType(toChineseViolationType(record.getViolationType()));
            return Result.data(record);
        } catch (Exception e) {
            log.error("content detect failed", e);
            return Result.fail("内容检测失败: " + e.getMessage());
        }
    }

    @PostMapping("/test-audit")
    public Result<TestAuditResponseVO> testAudit(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "deviceId", defaultValue = "test-device") String deviceId,
                                                 @RequestParam(value = "remark", required = false) String remark) {
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String ext = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase()
                    : ".jpg";
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            String objectName = "test/" + dateDir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

            minioUtil.createBucket(minioBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                            .build()
            );

            String minioUrl = minioEndpoint + "/" + minioBucket + "/" + objectName;
            String businessId = deviceId + "_test_" + System.currentTimeMillis();

            QwenDetectionRequestDTO request = new QwenDetectionRequestDTO();
            request.setBusinessId(businessId);
            request.setDeviceId(deviceId);
            request.setDeviceName(deviceId);
            request.setMinioPath(objectName);
            request.setFileName(originalFilename);
            request.setContentType("image");
            request.setCaptureTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            request.setDescription(remark != null ? remark : "测试审计图片-" + originalFilename);

            ContentMonitor record = detectionService.detectContent(request);
            TestAuditResponseVO resp = new TestAuditResponseVO();
            resp.setId(record.getId());
            resp.setContentId(record.getContentId());
            resp.setMinioPath(objectName);
            resp.setMinioUrl(minioUrl);
            resp.setStatus(record.getStatus());
            resp.setIsViolation(record.getIsViolation());
            resp.setViolationType(toChineseViolationType(record.getViolationType()));
            resp.setConfidence(record.getConfidence());
            resp.setReason(record.getReason());
            resp.setRequestId(record.getRequestId());
            resp.setErrorMessage(record.getErrorMessage());
            return Result.data(resp);
        } catch (Exception e) {
            log.error("test audit failed", e);
            return Result.fail("测试审计失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/pre-audit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PreAuditResponseVO> preAudit(@RequestParam("metadata") String metadataJson,
                                               @RequestParam(value = "file", required = false) MultipartFile file) {
        if (!preAuditEnabled) {
            return Result.fail("pre-audit disabled");
        }

        try {
            PreAuditMetadataVO metadata = OBJECT_MAPPER.readValue(metadataJson, PreAuditMetadataVO.class);
            if (metadata == null) {
                return Result.fail("invalid metadata");
            }
            String contentType = normalizeContentType(metadata.getContentType(), metadata.getFileName());
            PreAuditResponseVO response = new PreAuditResponseVO();
            response.setAuditId(metadata.getAuditId());
            response.setContentType(contentType);
            response.setDecision("NEED_MANUAL");
            response.setReason("MODEL_NEED_MANUAL");
            response.setSyncTimeoutMs(preAuditSyncTimeoutMs);

            if (file != null && !file.isEmpty() && exceedsPreAuditFileLimit(file.getSize())) {
                response.setDecision("NEED_MANUAL");
                response.setReason("FILE_SIZE_EXCEEDED");
                return Result.data(response);
            }

            String businessId = defaultIfBlank(metadata.getAuditId(),
                    "preaudit_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", ""));
            String clientId = defaultIfBlank(metadata.getClientId(), "client-pre-audit");

            QwenDetectionResultDTO auditResult;
            if ("text".equals(contentType)) {
                String textContent = metadata.getTextContent();
                if ((textContent == null || textContent.trim().isEmpty()) && file != null && !file.isEmpty()) {
                    textContent = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                if (textContent == null || textContent.trim().isEmpty()) {
                    response.setDecision("NEED_MANUAL");
                    response.setReason("TEXT_CONTENT_EMPTY");
                    return Result.data(response);
                }
                auditResult = localAuditService.auditText(textContent, businessId, clientId);
            } else if ("video".equals(contentType)) {
                if (file == null || file.isEmpty()) {
                    response.setDecision("NEED_MANUAL");
                    response.setReason("VIDEO_FILE_EMPTY");
                    return Result.data(response);
                }
                auditResult = localAuditService.auditVideoFile(file, businessId, clientId);
            } else {
                if (file == null || file.isEmpty()) {
                    response.setDecision("NEED_MANUAL");
                    response.setReason("IMAGE_FILE_EMPTY");
                    return Result.data(response);
                }
                auditResult = localAuditService.auditImageFile(file, businessId, clientId);
            }

            response.setStatus(auditResult.getDetectionResult());
            response.setViolationType(auditResult.getViolationType());
            response.setRiskLevel(auditResult.getViolationLevel());
            response.setAuditMessage(auditResult.getReason());

            if ("compliant".equalsIgnoreCase(auditResult.getDetectionResult())) {
                response.setDecision("PASS");
                response.setReason("MODEL_APPROVED");
                response.setIsViolation(0);
            } else if ("violation".equalsIgnoreCase(auditResult.getDetectionResult())) {
                response.setDecision("REJECT");
                response.setReason(defaultIfBlank(auditResult.getReason(), "MODEL_VIOLATION"));
                response.setIsViolation(1);
            } else {
                response.setDecision("NEED_MANUAL");
                response.setReason(defaultIfBlank(auditResult.getReason(), "MODEL_NEED_MANUAL"));
            }
            return Result.data(response);
        } catch (Exception e) {
            log.error("pre-audit failed", e);
            return Result.fail("pre-audit failed: " + e.getMessage());
        }
    }

    @PostMapping("/page")
    public Result<Page<ContentMonitor>> page(@RequestBody DetectionRecordQueryDTO dto) {
        return Result.data(detectionService.pageRecords(dto));
    }

    @GetMapping("/statistics")
    public Result<ContentDetectionService.DetectionStatisticsDTO> statistics() {
        return Result.data(detectionService.getStatistics());
    }

    @PostMapping("/sync-rules")
    public Result<String> syncRules() {
        try {
            detectionService.syncRuleConfig();
            return Result.data("规则同步成功");
        } catch (Exception e) {
            log.error("sync rules failed", e);
            return Result.fail("规则同步失败: " + e.getMessage());
        }
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

    private boolean exceedsPreAuditFileLimit(long bytes) {
        long maxBytes = Math.max(1L, preAuditMaxFileSizeMb) * 1024L * 1024L;
        return bytes > maxBytes;
    }

    private String uploadPreAuditFile(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase()
                : ".bin";
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String objectName = "preaudit/" + dateDir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        minioUtil.createBucket(preAuditBucket);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(preAuditBucket)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                        .build()
        );
        return objectName;
    }

    private String normalizeContentType(String contentType, String fileName) {
        String value = contentType == null ? "" : contentType.trim().toLowerCase();
        if ("image".equals(value) || "video".equals(value) || "text".equals(value)) {
            return value;
        }
        if (fileName == null || !fileName.contains(".")) {
            return "image";
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if ("txt".equals(ext) || "json".equals(ext) || "xml".equals(ext) || "csv".equals(ext) || "log".equals(ext)) {
            return "text";
        }
        if ("mp4".equals(ext) || "avi".equals(ext) || "mov".equals(ext) || "mkv".equals(ext)
                || "wmv".equals(ext) || "flv".equals(ext) || "mpeg".equals(ext) || "mpg".equals(ext)) {
            return "video";
        }
        return "image";
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    @Data
    private static class TestAuditResponseVO {
        private Long id;
        private String contentId;
        private String minioPath;
        private String minioUrl;
        private String status;
        private Integer isViolation;
        private String violationType;
        private Double confidence;
        private String reason;
        private String requestId;
        private String errorMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PreAuditMetadataVO {
        private String auditId;
        private String contentType;
        private String fileName;
        private String filePath;
        private String sourceIp;
        private Integer sourcePort;
        private String targetIp;
        private Integer targetPort;
        private String clientId;
        private Long pid;
        private String processName;
        private Long fileSize;
        private String fileHash;
        private Integer totalPackets;
        private Long completedAt;
        private String textContent;
    }

    @Data
    private static class PreAuditResponseVO {
        private String auditId;
        private String contentType;
        private String decision;
        private String reason;
        private Long recordId;
        private String status;
        private Integer isViolation;
        private String storedObject;
        private String violationType;
        private String riskLevel;
        private String auditMessage;
        private Long syncTimeoutMs = 0L;
    }
}
