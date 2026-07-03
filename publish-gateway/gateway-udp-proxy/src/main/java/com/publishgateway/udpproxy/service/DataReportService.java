package com.publishgateway.udpproxy.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.publishgateway.udpproxy.log.DiagnosticLogReport;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseContext;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseResult;
import com.publishgateway.udpproxy.protocol.strategy.context.ReportPayload;
import com.publishgateway.udpproxy.protocol.strategy.ProtocolParserRegistry;
import com.publishgateway.udpproxy.protocol.strategy.ProtocolParserStrategy;
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

/**
 * 数据上报服务
 * 将发布网关收到的UDP数据异步上报到管控平台内容服务
 */
@Slf4j
@Service
public class DataReportService {

    /** 管控平台内容服务URL */
    @Value("${monitor.platform.content-url:}")
    private String contentServiceUrl;

    /** 原始包内存缓存（注入） */
    @Resource
    private RawPacketStore rawPacketStore;

    /** MinIO 上传服务（注入） */
    @Resource
    private MinioUploadService minioUploadService;

    @Resource
    private RelayFileSignatureService relayFileSignatureService;

    /** 协议解析策略注册中心 */
    @Resource
    private ProtocolParserRegistry parserRegistry;

    /** 诊断日志上报器 */
    @Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    /** 异步上报线程池 */
    private final ExecutorService reportExecutor = Executors.newFixedThreadPool(4);

    /**
     * 异步上报数据（兼容旧调用，默认使用 sigma 协议解析）
     */
    public void reportAsync(String ruleId, Long chainId, byte[] data, String sourceIp) {
        reportAsync(ruleId, chainId, data, sourceIp, null, null, null);
    }

    /**
     * 异步上报数据到管控平台
     *
     * @param ruleId       规则ID（作为网关标识）
     * @param chainId      链路ID（业务标识，显式传递）
     * @param data         UDP原始数据
     * @param sourceIp     数据来源IP
     * @param manufacturer 情报板厂家标识（用于选择协议解析策略，为空时默认 sigma）
     * @param boardIp      情报板IP（转发目标IP）
     * @param boardPort    情报板端口（转发目标端口）
     */
    public void reportAsync(String ruleId, Long chainId, byte[] data, String sourceIp,
                            String manufacturer, String boardIp, Integer boardPort) {
        // 在任何解析之前先缓存原始包，保证始终可查
        rawPacketStore.save(ruleId, chainId, sourceIp, data);

        if (contentServiceUrl == null || contentServiceUrl.isEmpty()) {
            return;
        }

        reportExecutor.execute(() -> {
            try {
                doReport(ruleId, chainId, data, sourceIp, manufacturer, boardIp, boardPort);
            } catch (Exception e) {
                log.warn("【数据上报】上报失败（不影响转发）: ruleId={}, error={}", ruleId, e.getMessage(), e);
            }
        });
    }

    /**
     * 执行上报
     * 根据 manufacturer 自动选择对应的协议解析策略
     *
     * @param manufacturer 情报板厂家标识，为空时默认 sigma
     */
    public void doReport(String ruleId, Long chainId, byte[] data, String sourceIp,
                          String manufacturer, String boardIp, Integer boardPort) {
        // 通过策略注册中心获取对应的协议解析策略
        ProtocolParserStrategy strategy = parserRegistry.getStrategy(manufacturer);
        if (strategy == null) {
            log.warn("【数据上报】未找到协议解析策略: manufacturer={}, ruleId={}", manufacturer, ruleId);
            return;
        }

        ParseContext ctx = new ParseContext(ruleId, chainId, sourceIp, minioUploadService,
                boardIp, boardPort, relayFileSignatureService);
        ParseResult result = strategy.parse(data, ctx);

        if (result.isSuccess()) {
            ReportPayload payload = result.getPayload();
            // 填充公共字段
            fillCommonFields(payload, ruleId, chainId, sourceIp, data, boardIp, boardPort);
            // 按内容类型路由上报
            sendReport(ruleId, payload, result.getDataSize());

            // 处理伴随文件（播放列表缓存命中时，包含列表中的图片/视频）
            if (result.getAdditionalPayloads() != null) {
                for (ReportPayload additional : result.getAdditionalPayloads()) {
                    fillCommonFields(additional, ruleId, chainId, sourceIp, data, boardIp, boardPort);
                    sendReport(ruleId, additional, result.getDataSize());
                }
            }
        }
    }

    /**
     * 填充每次上报都携带的公共字段
     */
    private void fillCommonFields(ReportPayload payload, String ruleId,
                                   Long chainId, String sourceIp, byte[] data,
                                   String boardIp, Integer boardPort) {
        String businessId = ruleId + "-" + System.currentTimeMillis();
        payload.setBusinessId(businessId);
        // deviceId/deviceName 以情报板为主导：优先使用情报板IP，无则降级为网关ruleId
        String deviceId = (boardIp != null && !boardIp.isEmpty()) ? boardIp : ruleId;
        String deviceName = (boardIp != null && !boardIp.isEmpty()) ? "情报板-" + boardIp : "网关-" + ruleId;
        payload.setDeviceId(deviceId);
        payload.setDeviceName(deviceName);
        payload.setCaptureTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));
        payload.setGatewayId(ruleId);
        payload.setChainId(chainId);
        payload.setSourceIp(sourceIp);
        payload.setTimestamp(System.currentTimeMillis());
        payload.setRawPacket(Base64.getEncoder().encodeToString(data));
        payload.setBoardIp(boardIp);
        payload.setBoardPort(boardPort);
    }

    /**
     * 发送HTTP上报请求，按内容类型路由到不同接口：
     *   - image + (screenshotBase64 或 minioPath) → /content/detection/detect  (AI图片检测服务)
     *   - text / file_reference                    → /content/receive       (内容监看服务)
     *   - video + minioPath                       → /content/detection/detect  (AI视频检测服务)
     *   - binary / 其他                            → 跳过
     */
    private void sendReport(String ruleId, ReportPayload payload, int dataLength) {
        String contentType = payload.getContentType();

        // 1. 图片 → AI 检测服务
        if ("image".equals(contentType) && payload.hasImageData()) {
            doHttpPost(ruleId, contentServiceUrl + "/content/detection/detect",
                    payload, dataLength, "图片AI检测");
            return;
        }

        // 2. 文本 / 文件引用 → 内容监看服务
        if ("text".equals(contentType) || "file_reference".equals(contentType)) {
            payload.normalizeForTextReport();
            doHttpPost(ruleId, contentServiceUrl + "/content/receive",
                    payload, dataLength, "文本内容监看");
            return;
        }

        // 3. 视频 → AI 检测服务
        if ("video".equals(contentType) && payload.getMinioPath() != null) {
            doHttpPost(ruleId, contentServiceUrl + "/content/detection/detect",
                    payload, dataLength, "视频AI检测");
            return;
        }

        // 4. binary / unknown → 跳过
        log.debug("【数据上报】跳过非 image/text 内容: ruleId={}, contentType={}", ruleId, contentType);
    }

    /**
     * 执行 HTTP POST，统一连接参数与日志输出。
     * 在成功/失败/异常分支补 GATEWAY_CONTENT_REPORTED / GATEWAY_CONTENT_REPORT_FAILED。
     *
     * @param label 日志标签（用于区分图片/文本）
     */
    private void doHttpPost(String ruleId, String apiUrl, ReportPayload payload,
                            int dataLength, String label) {
        String jsonBody = JSON.toJSONString(payload, SerializerFeature.IgnoreNonFieldGetter);
        String businessId = payload.getBusinessId();
        String protocol = payload.getProtocol();
        String sourceIp = payload.getSourceIp();

        log.info("【数据上报】开始上报[{}]: ruleId={}, dataLength={}, protocol={}, url={}",
                label, ruleId, dataLength, protocol, apiUrl);

        boolean success = false;
        String errorMessage = null;

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(60000);  // AI检测耗时较长，等待最多60秒
            conn.setDoOutput(true);

            byte[] jsonBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(jsonBytes);
            conn.getOutputStream().flush();

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                success = true;
                log.info("【数据上报】上报成功[{}]: ruleId={}, protocol={}, content={}",
                        label, ruleId, protocol, payload.getDescription());
            } else {
                errorMessage = "HTTP " + responseCode;
                log.warn("【数据上报】上报返回非200[{}]: ruleId={}, code={}", label, ruleId, responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.warn("【数据上报】HTTP请求失败[{}]: {}", label, errorMessage);
        } finally {
            reportGatewayContent(ruleId, businessId, protocol, sourceIp, dataLength, label, success, errorMessage);
        }
    }

    /**
     * 发送内容上报诊断日志到管控平台日志服务。
     * 日志上报失败不影响转发，仅 warn 级别记录。
     */
    private void reportGatewayContent(String ruleId, String businessId, String protocol,
                                       String sourceIp, int dataLength, String label,
                                       boolean success, String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setEventType(success ? "GATEWAY_CONTENT_REPORTED" : "GATEWAY_CONTENT_REPORT_FAILED");
            report.setEventLevel(success ? "INFO" : "WARN");
            report.setStage("PUBLISH_GATEWAY");
            report.setContentId(businessId);
            report.setSourceIp(sourceIp);
            report.setSummary(String.format("[%s] ruleId=%s, protocol=%s, dataLength=%d, success=%s",
                    label, ruleId, protocol, dataLength, success));
            if (!success && errorMessage != null) {
                report.setErrorMessage(errorMessage);
            }
            report.setEventTime(LocalDateTime.now());
            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.warn("【数据上报】诊断日志上报失败: ruleId={}, error={}", ruleId, e.getMessage());
        }
    }
}
