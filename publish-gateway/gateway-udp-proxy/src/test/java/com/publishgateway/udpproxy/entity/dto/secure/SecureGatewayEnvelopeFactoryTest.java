package com.publishgateway.udpproxy.entity.dto.secure;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gateway.standardization.dto.StandardizedPublishPackage;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 信封工厂测试 —— 验证 v2 publish/control JSON 包含 schemaVersion/messageType
 */
public class SecureGatewayEnvelopeFactoryTest {

    @Test
    public void testPublishEnvelopeHasSchemaVersionAndMessageType() {
        SecureGatewayEnvelope.TargetRef target = new SecureGatewayEnvelope.TargetRef();
        target.setDeviceId("device-001");
        target.setIp("192.168.1.100");
        target.setPort(5200);
        target.setVendorHint("NOVA");

        StandardizedPublishPackage pkg = new StandardizedPublishPackage();
        pkg.setDeliveryTaskId("DLV-001");
        pkg.setAction("PUBLISH_PLAYLIST");

        SecureGatewayEnvelope envelope = SecureGatewayEnvelopeFactory.publish(
                "2.0", "REQ-001", "DLV-001", "client-001", target, pkg);

        assertNotNull(envelope);
        assertEquals("2.0", envelope.getSchemaVersion());
        assertEquals("PUBLISH", envelope.getMessageType());
        assertEquals("REQ-001", envelope.getRequestId());
        assertEquals("DLV-001", envelope.getDeliveryTaskId());
        assertNotNull(envelope.getSource());
        assertEquals("publish-gateway", envelope.getSource().getGatewayId());
        assertEquals("client-001", envelope.getSource().getClientId());
        assertNotNull(envelope.getTarget());
        assertNotNull(envelope.getPublish());
        assertNull(envelope.getControl());

        byte[] jsonBytes = SecureGatewayEnvelopeFactory.toBytes(envelope);
        String json = new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\":\"2.0\""));
        assertTrue(json.contains("\"messageType\":\"PUBLISH\""));
        assertTrue(json.contains("\"publish\""));
    }

    @Test
    public void testControlEnvelopeHasSchemaVersionAndMessageType() {
        SecureGatewayEnvelope.TargetRef target = new SecureGatewayEnvelope.TargetRef();
        target.setDeviceId("device-001");
        target.setIp("192.168.1.100");
        target.setPort(5200);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("brightness", 80);

        SecureGatewayEnvelope envelope = SecureGatewayEnvelopeFactory.control(
                "2.0", "CMD-001", "CMD-001", "client-001", target, "BRIGHTNESS", params);

        assertNotNull(envelope);
        assertEquals("2.0", envelope.getSchemaVersion());
        assertEquals("CONTROL", envelope.getMessageType());
        assertEquals("CMD-001", envelope.getRequestId());
        assertEquals("CMD-001", envelope.getCommandTaskId());
        assertNull(envelope.getDeliveryTaskId());
        assertNotNull(envelope.getControl());
        assertNull(envelope.getPublish());

        byte[] jsonBytes = SecureGatewayEnvelopeFactory.toBytes(envelope);
        String json = new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\":\"2.0\""));
        assertTrue(json.contains("\"messageType\":\"CONTROL\""));
        assertTrue(json.contains("\"control\""));
        assertTrue(json.contains("\"BRIGHTNESS\""));
    }
}
