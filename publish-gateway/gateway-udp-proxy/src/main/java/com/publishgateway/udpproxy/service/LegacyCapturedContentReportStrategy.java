package com.publishgateway.udpproxy.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.publishgateway.udpproxy.log.DiagnosticLogReport;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.publishgateway.udpproxy.protocol.strategy.ProtocolParserRegistry;
import com.publishgateway.udpproxy.protocol.strategy.ProtocolParserStrategy;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseContext;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseResult;
import com.publishgateway.udpproxy.protocol.strategy.context.ReportPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class LegacyCapturedContentReportStrategy
        implements ContentReportStrategy<LegacyCapturedContentReportRequest> {

    @Value("${monitor.platform.content-url:}")
    private String contentServiceUrl;

    @Resource
    private RawPacketStore rawPacketStore;

    @Resource
    private MinioUploadService minioUploadService;

    @Resource
    private RelayFileSignatureService relayFileSignatureService;

    @Resource
    private ProtocolParserRegistry parserRegistry;

    @Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    private final ExecutorService reportExecutor = Executors.newFixedThreadPool(4);

    @Override
    public ContentReportMode mode() {
        return ContentReportMode.LEGACY_CAPTURE;
    }

    @Override
    public void report(LegacyCapturedContentReportRequest request) {
        if (request == null) {
            return;
        }
        reportAsync(request.getRuleId(), request.getChainId(), request.getData(),
                request.getSourceIp(), request.getManufacturer(),
                request.getBoardIp(), request.getBoardPort());
    }

    public void reportAsync(String ruleId, Long chainId, byte[] data, String sourceIp) {
        reportAsync(ruleId, chainId, data, sourceIp, null, null, null);
    }

    public void reportAsync(String ruleId, Long chainId, byte[] data, String sourceIp,
                            String manufacturer, String boardIp, Integer boardPort) {
        if (rawPacketStore != null) {
            rawPacketStore.save(ruleId, chainId, sourceIp, data);
        }

        if (isBlank(contentServiceUrl)) {
            return;
        }

        reportExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    doReport(ruleId, chainId, data, sourceIp, manufacturer, boardIp, boardPort);
                } catch (Exception e) {
                    log.warn("[content-report][legacy] report failed but forwarding continues: ruleId={}, error={}",
                            ruleId, e.getMessage(), e);
                }
            }
        });
    }

    public void doReport(String ruleId, Long chainId, byte[] data, String sourceIp,
                         String manufacturer, String boardIp, Integer boardPort) {
        ProtocolParserStrategy strategy = parserRegistry != null ? parserRegistry.getStrategy(manufacturer) : null;
        if (strategy == null) {
            log.warn("[content-report][legacy] parser strategy not found: manufacturer={}, ruleId={}",
                    manufacturer, ruleId);
            return;
        }

        ParseContext ctx = new ParseContext(ruleId, chainId, sourceIp, minioUploadService,
                boardIp, boardPort, relayFileSignatureService);
        ParseResult result = strategy.parse(data, ctx);
        if (!result.isSuccess()) {
            return;
        }

        ReportPayload payload = result.getPayload();
        fillCommonFields(payload, ruleId, chainId, sourceIp, data, boardIp, boardPort);
        sendReport(ruleId, payload, result.getDataSize());

        if (result.getAdditionalPayloads() != null) {
            for (ReportPayload additional : result.getAdditionalPayloads()) {
                fillCommonFields(additional, ruleId, chainId, sourceIp, data, boardIp, boardPort);
                sendReport(ruleId, additional, result.getDataSize());
            }
        }
    }

    private void fillCommonFields(ReportPayload payload, String ruleId,
                                  Long chainId, String sourceIp, byte[] data,
                                  String boardIp, Integer boardPort) {
        if (payload == null) {
            return;
        }
        String businessId = ruleId + "-" + System.currentTimeMillis();
        payload.setBusinessId(businessId);
        String deviceId = !isBlank(boardIp) ? boardIp : ruleId;
        String deviceName = !isBlank(boardIp) ? "info-board-" + boardIp : "gateway-" + ruleId;
        payload.setDeviceId(deviceId);
        payload.setDeviceName(deviceName);
        payload.setCaptureTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));
        payload.setGatewayId(ruleId);
        payload.setChainId(chainId);
        payload.setSourceIp(sourceIp);
        payload.setTimestamp(System.currentTimeMillis());
        payload.setRawPacket(Base64.getEncoder().encodeToString(data == null ? new byte[0] : data));
        payload.setBoardIp(boardIp);
        payload.setBoardPort(boardPort);
    }

    private void sendReport(String ruleId, ReportPayload payload, int dataLength) {
        if (payload == null) {
            return;
        }
        String contentType = payload.getContentType();

        if ("image".equals(contentType) && payload.hasImageData()) {
            doHttpPost(ruleId, normalizeUrl(contentServiceUrl) + "/content/detection/detect",
                    payload, dataLength, "image-ai-detection");
            return;
        }

        if ("text".equals(contentType) || "file_reference".equals(contentType)) {
            payload.normalizeForTextReport();
            doHttpPost(ruleId, normalizeUrl(contentServiceUrl) + "/content/receive",
                    payload, dataLength, "text-content-monitor");
            return;
        }

        if ("video".equals(contentType) && payload.getMinioPath() != null) {
            doHttpPost(ruleId, normalizeUrl(contentServiceUrl) + "/content/detection/detect",
                    payload, dataLength, "video-ai-detection");
            return;
        }

        log.debug("[content-report][legacy] skip non-reportable content: ruleId={}, contentType={}",
                ruleId, contentType);
    }

    private void doHttpPost(String ruleId, String apiUrl, ReportPayload payload,
                            int dataLength, String label) {
        String jsonBody = JSON.toJSONString(payload, SerializerFeature.IgnoreNonFieldGetter);
        String businessId = payload.getBusinessId();
        String protocol = payload.getProtocol();
        String sourceIp = payload.getSourceIp();

        boolean success = false;
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
            conn.getOutputStream().write(jsonBytes);
            conn.getOutputStream().flush();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                success = true;
                log.info("[content-report][legacy] report success: label={}, ruleId={}, protocol={}, content={}",
                        label, ruleId, protocol, payload.getDescription());
            } else {
                errorMessage = "HTTP " + responseCode;
                log.warn("[content-report][legacy] report returned non-200: label={}, ruleId={}, code={}",
                        label, ruleId, responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.warn("[content-report][legacy] HTTP request failed: label={}, error={}",
                    label, errorMessage);
        } finally {
            reportGatewayContent(ruleId, businessId, payload.getPublishRequestId(),
                    protocol, sourceIp, dataLength, label, success, errorMessage);
        }
    }

    private void reportGatewayContent(String ruleId, String businessId, String publishRequestId,
                                      String protocol, String sourceIp, int dataLength,
                                      String label, boolean success, String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setEventType(success ? "GATEWAY_CONTENT_REPORTED" : "GATEWAY_CONTENT_REPORT_FAILED");
            report.setEventLevel(success ? "INFO" : "WARN");
            report.setStage("PUBLISH_GATEWAY");
            report.setContentId(businessId);
            report.setTraceId(publishRequestId);
            report.setSourceIp(sourceIp);
            report.setSummary(String.format("[%s] ruleId=%s, protocol=%s, dataLength=%d, success=%s",
                    label, ruleId, protocol, dataLength, success));
            if (!success && errorMessage != null) {
                report.setErrorMessage(errorMessage);
            }
            report.setEventTime(LocalDateTime.now());
            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.warn("[content-report][legacy] diagnostic report failed: ruleId={}, error={}",
                    ruleId, e.getMessage());
        }
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
