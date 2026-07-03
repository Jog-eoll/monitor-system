package com.publishgateway.udpproxy.entity.dto.secure;

import com.alibaba.fastjson2.JSON;
import com.gateway.standardization.dto.StandardizedPublishPackage;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一出站信封工厂
 * <p>
 * 把当前手写 JSONObject envelope 收敛到此工厂，确保 requestId、deliveryTaskId/commandTaskId
 * 同时写入 JSON 信封和 HTTP header，保证解密网关幂等、日志和回放都能稳定工作。
 * </p>
 */
public class SecureGatewayEnvelopeFactory {

    private static final String GATEWAY_ID = "publish-gateway";

    /**
     * 构建发布包 v2 信封
     *
     * @param schemaVersion  信封模式版本
     * @param requestId      请求 ID（同时写入信封和 HTTP header）
     * @param deliveryTaskId 发布任务 ID（同时写入信封和 HTTP header）
     * @param sourceClientId 来源客户端 ID（可选）
     * @param target         目标设备引用
     * @param pkg            已构建好的 StandardizedPublishPackage
     * @return 信封对象
     */
    public static SecureGatewayEnvelope publish(String schemaVersion,
                                                String requestId,
                                                String deliveryTaskId,
                                                String sourceClientId,
                                                SecureGatewayEnvelope.TargetRef target,
                                                StandardizedPublishPackage pkg) {
        SecureGatewayEnvelope envelope = new SecureGatewayEnvelope();
        envelope.setSchemaVersion(schemaVersion);
        envelope.setMessageType("PUBLISH");
        envelope.setRequestId(requestId);
        envelope.setDeliveryTaskId(deliveryTaskId);

        // source
        SecureGatewayEnvelope.SourceRef sourceRef = new SecureGatewayEnvelope.SourceRef();
        sourceRef.setGatewayId(GATEWAY_ID);
        sourceRef.setClientId(sourceClientId);
        envelope.setSource(sourceRef);

        // target
        envelope.setTarget(target);

        // publish payload
        Map<String, Object> publishPayload = new LinkedHashMap<>();
        publishPayload.put("action", pkg.getAction());
        publishPayload.put("playlist", pkg.getPlaylist());
        publishPayload.put("files", pkg.getFiles());
        publishPayload.put("options", pkg.getOptions());
        publishPayload.put("sigmaPublishId", pkg.getSigmaPublishId());
        publishPayload.put("publishPermit", pkg.getPublishPermit());
        envelope.setPublish(publishPayload);

        return envelope;
    }

    /**
     * 构建控制包 v2 信封
     *
     * @param schemaVersion  信封模式版本
     * @param requestId      请求 ID（同时写入信封和 HTTP header）
     * @param commandTaskId  控制任务 ID（同时写入信封和 HTTP header）
     * @param sourceClientId 来源客户端 ID
     * @param target         目标设备引用
     * @param command        控制指令（如 BRIGHTNESS）
     * @param params         控制参数
     * @return 信封对象
     */
    public static SecureGatewayEnvelope control(String schemaVersion,
                                                String requestId,
                                                String commandTaskId,
                                                String sourceClientId,
                                                SecureGatewayEnvelope.TargetRef target,
                                                String command,
                                                Map<String, Object> params) {
        SecureGatewayEnvelope envelope = new SecureGatewayEnvelope();
        envelope.setSchemaVersion(schemaVersion);
        envelope.setMessageType("CONTROL");
        envelope.setRequestId(requestId);
        envelope.setCommandTaskId(commandTaskId);

        // source
        SecureGatewayEnvelope.SourceRef sourceRef = new SecureGatewayEnvelope.SourceRef();
        sourceRef.setGatewayId(GATEWAY_ID);
        sourceRef.setClientId(sourceClientId);
        envelope.setSource(sourceRef);

        // target
        envelope.setTarget(target);

        // control payload
        Map<String, Object> controlPayload = new LinkedHashMap<>();
        controlPayload.put("action", "CONTROL_SCREEN");
        controlPayload.put("command", command);
        controlPayload.put("params", params);
        envelope.setControl(controlPayload);

        return envelope;
    }

    /**
     * 将信封序列化为 JSON 字节数组（UTF-8）
     */
    public static byte[] toBytes(SecureGatewayEnvelope envelope) {
        return JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8);
    }
}
