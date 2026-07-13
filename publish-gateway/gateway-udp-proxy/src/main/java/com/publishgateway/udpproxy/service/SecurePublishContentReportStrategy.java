package com.publishgateway.udpproxy.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.log.DiagnosticLogReport;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.publishgateway.udpproxy.protocol.strategy.context.ReportPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class SecurePublishContentReportStrategy
        implements ContentReportStrategy<SecureDeliveryTaskRequest> {

    private static final String PROTOCOL = "SECURE_PUBLISH";

    @Value("${monitor.platform.content-url:}")
    private String contentServiceUrl;

    @Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    @Resource
    private MinioUploadService minioUploadService;

    private final ExecutorService deliveredReportExecutor = Executors.newFixedThreadPool(2);

    @PreDestroy
    public void shutdown() {
        deliveredReportExecutor.shutdown();
    }

    @Override
    public ContentReportMode mode() {
        return ContentReportMode.SECURE_PUBLISH;
    }

    @Override
    public void report(SecureDeliveryTaskRequest request) {
        reportAccepted(request);
    }

    public void reportAccepted(SecureDeliveryTaskRequest request) {
        if (request == null) {
            return;
        }
        ReportPayload payload = buildPayload(request);
        if (payload == null) {
            reportContentRecord(request, null, false, "SECURE_PUBLISH_PAYLOAD_INVALID",
                    "playlistId or requestId is required");
            return;
        }
        if (isBlank(contentServiceUrl)) {
            reportContentRecord(request, payload, false, "CONTENT_SERVICE_URL_EMPTY",
                    "monitor.platform.content-url is empty");
            return;
        }
        doHttpPost(request, payload);
    }

    public void reportDelivered(final String deliveryTaskId,
                                final SecureDeliveryTaskRequest request,
                                final List<SecurePublishDeliveredFile> files) {
        if (request == null || files == null || files.isEmpty()) {
            return;
        }
        deliveredReportExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (SecurePublishDeliveredFile file : files) {
                    try {
                        reportDeliveredMediaFile(deliveryTaskId, request, file);
                    } catch (Exception e) {
                        log.warn("[content-report][secure-publish] delivered media report failed but delivery is kept: deliveryTaskId={}, requestId={}, error={}",
                                deliveryTaskId, request.getRequestId(), e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void reportDeliveredMediaFile(String deliveryTaskId,
                                          SecureDeliveryTaskRequest request,
                                          SecurePublishDeliveredFile file) {
        if (file == null || file.getFileRef() == null) {
            return;
        }
        SecureDeliveryTaskRequest.FileRef ref = file.getFileRef();
        String contentType = normalizeMediaContentType(ref);
        if (contentType == null) {
            return;
        }
        if (isBlank(contentServiceUrl)) {
            reportMediaContentRecord(deliveryTaskId, request, file, null, false,
                    "CONTENT_SERVICE_URL_EMPTY", "monitor.platform.content-url is empty");
            return;
        }
        if (!file.hasData()) {
            reportMediaContentRecord(deliveryTaskId, request, file, null, false,
                    "MEDIA_DATA_EMPTY", "downloaded media bytes are empty");
            return;
        }

        String minioPath = uploadMedia(contentType, ref, file.getData());
        if (isBlank(minioPath) && "video".equals(contentType)) {
            reportMediaContentRecord(deliveryTaskId, request, file, null, false,
                    "MINIO_UPLOAD_FAILED", "video content requires MinIO path for detection");
            return;
        }

        ReportPayload payload = buildMediaPayload(deliveryTaskId, request, file, contentType, minioPath);
        doMediaHttpPost(deliveryTaskId, request, file, payload);
    }

    private ReportPayload buildPayload(SecureDeliveryTaskRequest request) {
        String playlistId = playlistId(request);
        String contentId = firstNonBlank(playlistId, request.getRequestId());
        if (isBlank(contentId)) {
            return null;
        }

        ReportPayload payload = new ReportPayload();
        payload.setBusinessId(contentId);
        payload.setContentId(contentId);
        payload.setPublishRequestId(request.getRequestId());
        payload.setProtocol(PROTOCOL);
        payload.setCommandType("PUBLISH_PLAYLIST");
        payload.setContentType("playlist");
        payload.setGatewayId(PROTOCOL);
        payload.setDeviceId(targetDeviceId(request));
        payload.setDeviceName(targetName(request));
        payload.setSourceIp(null);
        payload.setBoardIp(targetIp(request));
        payload.setBoardPort(targetPort(request));
        payload.setPlayBatchId(contentId);
        payload.setPlayBatchSeq(0);
        payload.setPlayBatchSize(fileCount(request));
        payload.setFileName(firstFileName(request, "playlist-" + contentId));
        payload.setDescription("SECURE_PUBLISH playlist=" + contentId
                + ", files=" + fileCount(request)
                + ", target=" + targetName(request));
        payload.setData(JSON.toJSONString(buildDetail(request), SerializerFeature.IgnoreNonFieldGetter));
        payload.setCaptureTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));
        payload.setTimestamp(System.currentTimeMillis());
        return payload;
    }

    private void doHttpPost(SecureDeliveryTaskRequest request, ReportPayload payload) {
        String apiUrl = normalizeUrl(contentServiceUrl) + "/content/receive";
        String jsonBody = JSON.toJSONString(payload, SerializerFeature.IgnoreNonFieldGetter);
        boolean success = false;
        String errorCode = null;
        String errorMessage = null;

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            byte[] jsonBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = conn.getOutputStream();
            try {
                outputStream.write(jsonBytes);
                outputStream.flush();
            } finally {
                outputStream.close();
            }

            int responseCode = conn.getResponseCode();
            String responseBody = readResponseBody(conn, responseCode);
            Boolean businessSuccess = parseBusinessSuccess(responseBody);
            if (responseCode == 200 && !Boolean.FALSE.equals(businessSuccess)) {
                success = true;
                log.info("[content-report][secure-publish] content record reported: requestId={}, contentId={}",
                        request.getRequestId(), payload.getContentId());
            } else if (responseCode == 200) {
                errorCode = "CONTENT_RECEIVE_BUSINESS_FAILED";
                errorMessage = parseBusinessMessage(responseBody);
                log.warn("[content-report][secure-publish] content receive business failed: requestId={}, message={}",
                        request.getRequestId(), errorMessage);
            } else {
                errorCode = "CONTENT_RECEIVE_HTTP_" + responseCode;
                errorMessage = "HTTP " + responseCode;
                log.warn("[content-report][secure-publish] content receive returned non-200: requestId={}, code={}",
                        request.getRequestId(), responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            errorCode = "CONTENT_RECEIVE_FAILED";
            errorMessage = e.getMessage();
            log.warn("[content-report][secure-publish] content receive failed: requestId={}, error={}",
                    request.getRequestId(), errorMessage);
        } finally {
            reportContentRecord(request, payload, success, errorCode, errorMessage);
        }
    }

    private void doMediaHttpPost(String deliveryTaskId,
                                 SecureDeliveryTaskRequest request,
                                 SecurePublishDeliveredFile file,
                                 ReportPayload payload) {
        String apiUrl = normalizeUrl(contentServiceUrl) + "/content/detection/detect";
        String jsonBody = JSON.toJSONString(payload, SerializerFeature.IgnoreNonFieldGetter);
        boolean success = false;
        String errorCode = null;
        String errorMessage = null;

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            byte[] jsonBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = conn.getOutputStream();
            try {
                outputStream.write(jsonBytes);
                outputStream.flush();
            } finally {
                outputStream.close();
            }

            int responseCode = conn.getResponseCode();
            String responseBody = readResponseBody(conn, responseCode);
            Boolean businessSuccess = parseBusinessSuccess(responseBody);
            if (responseCode == 200 && !Boolean.FALSE.equals(businessSuccess)) {
                success = true;
                log.info("[content-report][secure-publish] delivered media reported: deliveryTaskId={}, requestId={}, contentId={}, type={}",
                        deliveryTaskId, request.getRequestId(), payload.getContentId(), payload.getContentType());
            } else if (responseCode == 200) {
                errorCode = "CONTENT_DETECTION_BUSINESS_FAILED";
                errorMessage = parseBusinessMessage(responseBody);
                log.warn("[content-report][secure-publish] delivered media detection business failed: deliveryTaskId={}, requestId={}, message={}",
                        deliveryTaskId, request.getRequestId(), errorMessage);
            } else {
                errorCode = "CONTENT_DETECTION_HTTP_" + responseCode;
                errorMessage = "HTTP " + responseCode;
                log.warn("[content-report][secure-publish] delivered media detection returned non-200: deliveryTaskId={}, requestId={}, code={}",
                        deliveryTaskId, request.getRequestId(), responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            errorCode = "CONTENT_DETECTION_FAILED";
            errorMessage = e.getMessage();
            log.warn("[content-report][secure-publish] delivered media detection failed: deliveryTaskId={}, requestId={}, error={}",
                    deliveryTaskId, request.getRequestId(), errorMessage);
        } finally {
            reportMediaContentRecord(deliveryTaskId, request, file, payload, success, errorCode, errorMessage);
        }
    }

    private String readResponseBody(HttpURLConnection conn, int responseCode) {
        InputStream inputStream = null;
        try {
            inputStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (inputStream == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = inputStream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                    // ignore close failure
                }
            }
        }
    }

    private Boolean parseBusinessSuccess(String responseBody) {
        if (isBlank(responseBody)) {
            return null;
        }
        try {
            Object value = JSON.parseObject(responseBody).get("success");
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof String) {
                return Boolean.valueOf((String) value);
            }
        } catch (Exception ignored) {
            // Keep compatibility with non-JSON 200 responses.
        }
        return null;
    }

    private String parseBusinessMessage(String responseBody) {
        if (isBlank(responseBody)) {
            return "content receive returned success=false";
        }
        try {
            String message = JSON.parseObject(responseBody).getString("message");
            if (!isBlank(message)) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through to raw response body
        }
        return responseBody;
    }

    private void reportContentRecord(SecureDeliveryTaskRequest request, ReportPayload payload,
                                     boolean success, String errorCode, String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setTraceId(request != null ? request.getRequestId() : null);
            report.setEventType(success
                    ? "SECURE_PUBLISH_CONTENT_REPORTED"
                    : "SECURE_PUBLISH_CONTENT_REPORT_FAILED");
            report.setEventLevel(success ? "info" : "warn");
            report.setStage("publish_gateway");
            report.setContentId(payload != null ? payload.getContentId() : playlistId(request));
            report.setBoardIp(targetIp(request));
            report.setBoardPort(targetPort(request));
            report.setResultStatus(success ? "success" : "fail");
            report.setErrorCode(errorCode);
            report.setErrorMessage(errorMessage);
            report.setRefTable("t_content_monitor");
            report.setRefId(payload != null ? payload.getContentId() : playlistId(request));
            report.setSummary(success
                    ? "SECURE_PUBLISH content record reported"
                    : "SECURE_PUBLISH content record report failed");
            report.setDetailJson(JSON.toJSONString(buildDetail(request), SerializerFeature.IgnoreNonFieldGetter));
            report.setDedupKey(dedupKey("secure-publish-content",
                    request != null ? request.getRequestId() : null,
                    payload != null ? payload.getContentId() : playlistId(request),
                    success ? "success" : "fail"));
            report.setEventTime(LocalDateTime.now());
            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.warn("[content-report][secure-publish] diagnostic report failed: requestId={}, error={}",
                    request != null ? request.getRequestId() : null, e.getMessage());
        }
    }

    private void reportMediaContentRecord(String deliveryTaskId,
                                          SecureDeliveryTaskRequest request,
                                          SecurePublishDeliveredFile file,
                                          ReportPayload payload,
                                          boolean success,
                                          String errorCode,
                                          String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setTraceId(request != null ? request.getRequestId() : null);
            report.setEventType(success
                    ? "SECURE_PUBLISH_MEDIA_CONTENT_REPORTED"
                    : "SECURE_PUBLISH_MEDIA_CONTENT_REPORT_FAILED");
            report.setEventLevel(success ? "info" : "warn");
            report.setStage("publish_gateway");
            report.setContentId(payload != null ? payload.getContentId() : mediaContentId(request, file));
            report.setBoardIp(targetIp(request));
            report.setBoardPort(targetPort(request));
            report.setResultStatus(success ? "success" : "fail");
            report.setErrorCode(errorCode);
            report.setErrorMessage(errorMessage);
            report.setRefTable("t_content_monitor");
            report.setRefId(payload != null ? payload.getContentId() : mediaContentId(request, file));
            report.setSummary(success
                    ? "SECURE_PUBLISH media content reported"
                    : "SECURE_PUBLISH media content report failed");
            report.setDetailJson(JSON.toJSONString(buildDeliveredDetail(deliveryTaskId, request, file,
                    payload != null ? payload.getMinioPath() : null), SerializerFeature.IgnoreNonFieldGetter));
            report.setDedupKey(dedupKey("secure-publish-media",
                    request != null ? request.getRequestId() : null,
                    mediaContentId(request, file),
                    success ? "success" : "fail"));
            report.setEventTime(LocalDateTime.now());
            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.warn("[content-report][secure-publish] media diagnostic report failed: deliveryTaskId={}, requestId={}, error={}",
                    deliveryTaskId, request != null ? request.getRequestId() : null, e.getMessage());
        }
    }

    private ReportPayload buildMediaPayload(String deliveryTaskId,
                                            SecureDeliveryTaskRequest request,
                                            SecurePublishDeliveredFile file,
                                            String contentType,
                                            String minioPath) {
        SecureDeliveryTaskRequest.FileRef ref = file.getFileRef();
        String contentId = mediaContentId(request, file);

        ReportPayload payload = new ReportPayload();
        payload.setBusinessId(contentId);
        payload.setContentId(contentId);
        payload.setPublishRequestId(request.getRequestId());
        payload.setProtocol(PROTOCOL);
        payload.setCommandType("PUBLISH_MEDIA_FILE");
        payload.setContentType(contentType);
        payload.setGatewayId(PROTOCOL);
        payload.setDeviceId(targetDeviceId(request));
        payload.setDeviceName(targetName(request));
        payload.setBoardIp(targetIp(request));
        payload.setBoardPort(targetPort(request));
        payload.setPlayBatchId(firstNonBlank(playlistId(request), request.getRequestId()));
        payload.setPlayBatchSeq(ref.getOrderNo());
        payload.setPlayBatchSize(fileCount(request));
        payload.setFileName(ref.getFileName());
        payload.setFileExtension(fileExtension(ref.getFileName()));
        payload.setMinioPath(minioPath);
        payload.setTotalSize(file.getData() != null ? file.getData().length : null);
        if ("image".equals(contentType)) {
            payload.setImageFormat(imageFormat(ref.getFileName(), ref.getFileType()));
            if (isBlank(minioPath)) {
                String base64 = Base64.getEncoder().encodeToString(file.getData());
                payload.setScreenshotBase64(base64);
                payload.captureImageForAnalysis(file.getData());
            }
        }
        payload.setDescription("SECURE_PUBLISH media file=" + ref.getFileName()
                + ", playlist=" + playlistId(request)
                + ", deliveryTaskId=" + deliveryTaskId);
        payload.setData(JSON.toJSONString(buildDeliveredDetail(deliveryTaskId, request, file, minioPath),
                SerializerFeature.IgnoreNonFieldGetter));
        payload.setCaptureTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));
        payload.setTimestamp(System.currentTimeMillis());
        return payload;
    }

    private Map<String, Object> buildDeliveredDetail(String deliveryTaskId,
                                                     SecureDeliveryTaskRequest request,
                                                     SecurePublishDeliveredFile file,
                                                     String minioPath) {
        Map<String, Object> detail = buildDetail(request);
        detail.put("deliveryTaskId", deliveryTaskId);
        detail.put("contentReportStage", "postDelivery");
        detail.put("minioPath", minioPath);
        if (file != null && file.getFileRef() != null) {
            SecureDeliveryTaskRequest.FileRef ref = file.getFileRef();
            detail.put("orderNo", ref.getOrderNo());
            detail.put("fileName", ref.getFileName());
            detail.put("fileType", ref.getFileType());
            detail.put("fileUrl", ref.getFileUrl());
            detail.put("fileHash", ref.getFileHash());
            detail.put("actualHash", file.getActualHash());
            detail.put("fileSize", file.getData() != null ? file.getData().length : null);
            detail.put("durationSeconds", ref.getDurationSeconds());
        }
        return detail;
    }

    private String uploadMedia(String contentType,
                               SecureDeliveryTaskRequest.FileRef ref,
                               byte[] data) {
        if (minioUploadService == null || data == null || data.length == 0) {
            return null;
        }
        try {
            if ("image".equals(contentType)) {
                return minioUploadService.uploadImage(data, ref.getFileName(),
                        imageFormat(ref.getFileName(), ref.getFileType()));
            }
            if ("video".equals(contentType)) {
                return minioUploadService.uploadVideo(data, ref.getFileName());
            }
        } catch (Exception e) {
            log.warn("[content-report][secure-publish] MinIO upload failed: fileName={}, type={}, error={}",
                    ref != null ? ref.getFileName() : null, contentType, e.getMessage());
        }
        return null;
    }

    private String normalizeMediaContentType(SecureDeliveryTaskRequest.FileRef ref) {
        if (ref == null) {
            return null;
        }
        if (isImage(ref.getFileType()) || isImage(ref.getFileName())) {
            return "image";
        }
        if (isVideo(ref.getFileType()) || isVideo(ref.getFileName())) {
            return "video";
        }
        return null;
    }

    private boolean isImage(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "image".equals(normalized)
                || "jpg".equals(normalized)
                || "jpeg".equals(normalized)
                || "png".equals(normalized)
                || "gif".equals(normalized)
                || "bmp".equals(normalized)
                || normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg")
                || normalized.endsWith(".png")
                || normalized.endsWith(".gif")
                || normalized.endsWith(".bmp");
    }

    private boolean isVideo(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "video".equals(normalized)
                || "mp4".equals(normalized)
                || "flv".equals(normalized)
                || "avi".equals(normalized)
                || "mov".equals(normalized)
                || "wmv".equals(normalized)
                || "mkv".equals(normalized)
                || normalized.endsWith(".mp4")
                || normalized.endsWith(".flv")
                || normalized.endsWith(".avi")
                || normalized.endsWith(".mov")
                || normalized.endsWith(".wmv")
                || normalized.endsWith(".mkv");
    }

    private String imageFormat(String fileName, String fileType) {
        String extension = fileExtension(fileName);
        if (isBlank(extension)) {
            extension = fileType;
        }
        if (isBlank(extension)) {
            return "JPEG";
        }
        String normalized = extension.toUpperCase(Locale.ROOT);
        if ("JPG".equals(normalized)) {
            return "JPEG";
        }
        return normalized;
    }

    private String fileExtension(String fileName) {
        if (isBlank(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String mediaContentId(SecureDeliveryTaskRequest request,
                                  SecurePublishDeliveredFile file) {
        String base = firstNonBlank(playlistId(request),
                request != null ? request.getRequestId() : null);
        if (isBlank(base)) {
            base = "secure-publish";
        }
        SecureDeliveryTaskRequest.FileRef ref = file != null ? file.getFileRef() : null;
        String order = ref != null && ref.getOrderNo() != null
                ? String.valueOf(ref.getOrderNo()) : "item";
        String identity = ref != null ? firstNonBlank(ref.getFileHash(), ref.getFileName()) : null;
        if (isBlank(identity)) {
            identity = "media";
        }
        return trimContentId(base + "-" + order + "-" + sanitizeIdPart(identity));
    }

    private String sanitizeIdPart(String value) {
        if (value == null) {
            return "media";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isEmpty() ? "media" : sanitized;
    }

    private String trimContentId(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private Map<String, Object> buildDetail(SecureDeliveryTaskRequest request) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("reportMode", PROTOCOL);
        detail.put("requestId", request != null ? request.getRequestId() : null);
        detail.put("sigmaPublishId", request != null ? request.getSigmaPublishId() : null);
        detail.put("playlistId", playlistId(request));
        detail.put("playlistDigest", playlistDigest(request));
        detail.put("fileCount", fileCount(request));
        if (request != null && request.getTarget() != null) {
            Map<String, Object> target = new LinkedHashMap<String, Object>();
            target.put("deviceId", request.getTarget().getDeviceId());
            target.put("ip", request.getTarget().getIp());
            target.put("port", request.getTarget().getPort());
            target.put("vendorHint", request.getTarget().getVendorHint());
            detail.put("target", target);
        }
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        if (request != null && request.getFiles() != null) {
            for (SecureDeliveryTaskRequest.FileRef file : request.getFiles()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("orderNo", file.getOrderNo());
                item.put("fileName", file.getFileName());
                item.put("fileType", file.getFileType());
                item.put("fileUrl", file.getFileUrl());
                item.put("durationSeconds", file.getDurationSeconds());
                item.put("fileHash", file.getFileHash());
                files.add(item);
            }
        }
        detail.put("files", files);
        return detail;
    }

    private String playlistId(SecureDeliveryTaskRequest request) {
        return request != null && request.getPlaylist() != null
                ? request.getPlaylist().getPlaylistId() : null;
    }

    private String playlistDigest(SecureDeliveryTaskRequest request) {
        return request != null && request.getPlaylist() != null
                ? request.getPlaylist().getDigest() : null;
    }

    private int fileCount(SecureDeliveryTaskRequest request) {
        return request != null && request.getFiles() != null ? request.getFiles().size() : 0;
    }

    private String targetDeviceId(SecureDeliveryTaskRequest request) {
        return request != null && request.getTarget() != null
                ? firstNonBlank(request.getTarget().getDeviceId(), request.getTarget().getIp()) : null;
    }

    private String targetIp(SecureDeliveryTaskRequest request) {
        return request != null && request.getTarget() != null ? request.getTarget().getIp() : null;
    }

    private Integer targetPort(SecureDeliveryTaskRequest request) {
        return request != null && request.getTarget() != null ? request.getTarget().getPort() : null;
    }

    private String targetName(SecureDeliveryTaskRequest request) {
        String ip = targetIp(request);
        Integer port = targetPort(request);
        if (isBlank(ip)) {
            return targetDeviceId(request);
        }
        return port != null ? ip + ":" + port : ip;
    }

    private String firstFileName(SecureDeliveryTaskRequest request, String fallback) {
        if (request != null && request.getFiles() != null && !request.getFiles().isEmpty()
                && request.getFiles().get(0) != null
                && !isBlank(request.getFiles().get(0).getFileName())) {
            return request.getFiles().get(0).getFileName();
        }
        return fallback;
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String dedupKey(String prefix, String... parts) {
        StringBuilder key = new StringBuilder(prefix);
        if (parts != null) {
            for (String part : parts) {
                if (!isBlank(part)) {
                    key.append(':').append(part.trim());
                }
            }
        }
        return key.length() > 240 ? key.substring(0, 240) : key.toString();
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
