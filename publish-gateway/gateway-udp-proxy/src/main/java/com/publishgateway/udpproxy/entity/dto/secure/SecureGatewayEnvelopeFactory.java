package com.publishgateway.udpproxy.entity.dto.secure;

import com.alibaba.fastjson2.JSON;
import com.gateway.standardization.dto.StandardizedPublishPackage;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Secure gateway envelope factory for control-command delivery.
 */
public class SecureGatewayEnvelopeFactory {

    private static final String GATEWAY_ID = "publish-gateway";

    private SecureGatewayEnvelopeFactory() {
    }

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

        SecureGatewayEnvelope.SourceRef sourceRef = new SecureGatewayEnvelope.SourceRef();
        sourceRef.setGatewayId(GATEWAY_ID);
        sourceRef.setClientId(sourceClientId);
        envelope.setSource(sourceRef);

        envelope.setTarget(target);

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

        SecureGatewayEnvelope.SourceRef sourceRef = new SecureGatewayEnvelope.SourceRef();
        sourceRef.setClientId(sourceClientId);
        envelope.setSource(sourceRef);

        envelope.setTarget(target);

        Map<String, Object> control = new LinkedHashMap<>();
        control.put("commandTaskId", commandTaskId);
        control.put("command", command);
        control.put("params", params);
        envelope.setControl(control);
        return envelope;
    }

    public static byte[] toBytes(SecureGatewayEnvelope envelope) {
        return JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8);
    }
}
