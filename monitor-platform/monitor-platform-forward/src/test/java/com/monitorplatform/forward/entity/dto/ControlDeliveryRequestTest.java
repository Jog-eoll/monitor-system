package com.monitorplatform.forward.entity.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlDeliveryRequestTest {

    @Test
    void toActionBodyProducesPlaintextCompatibleContract() {
        ControlDeliveryRequest request = new ControlDeliveryRequest();
        request.setGatewayDeviceId("GW-DEV-001");
        request.setTargetDeviceId("terminal-gateway-26");
        request.setTargetDeviceType("publish_gateway");
        request.setControlCommand("BRIGHTNESS");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("brightness", 80);
        request.setParams(params);
        request.setTargetIp("192.168.1.100");
        request.setTargetPort(8093);

        Map<String, Object> body = request.toActionBody();

        String commandTaskId = (String) body.get("commandTaskId");
        assertNotNull(commandTaskId);
        assertTrue(commandTaskId.startsWith("CTRL-"));
        assertEquals(17, commandTaskId.length(), "CTRL- + 12 hex chars");
        assertEquals("BRIGHTNESS", body.get("command"));

        String encryptedCommandPackage = (String) body.get("encryptedCommandPackage");
        assertNotNull(encryptedCommandPackage);
        byte[] decoded = Base64.getDecoder().decode(encryptedCommandPackage);
        JSONObject plainPackage = JSON.parseObject(new String(decoded, StandardCharsets.UTF_8));
        assertNotNull(plainPackage);
        assertEquals("BRIGHTNESS", plainPackage.getString("command"));
        assertEquals(commandTaskId, plainPackage.getString("commandTaskId"));
        assertNotNull(plainPackage.getJSONObject("params"));
        assertEquals(80, plainPackage.getJSONObject("params").getIntValue("brightness"));
        assertNotNull(plainPackage.getJSONObject("source"));
        assertEquals("monitor-platform-forward", plainPackage.getJSONObject("source").getString("clientId"));
        assertNotNull(plainPackage.getJSONObject("target"));
        assertEquals("terminal-gateway-26", plainPackage.getJSONObject("target").getString("deviceId"));
        assertEquals("192.168.1.100", plainPackage.getJSONObject("target").getString("ip"));
        assertEquals(8093, plainPackage.getJSONObject("target").getIntValue("port"));

        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) body.get("target");
        assertNotNull(target);
        assertEquals("terminal-gateway-26", target.get("deviceId"));
        assertEquals("192.168.1.100", target.get("ip"));
        assertEquals(8093, target.get("port"));
        assertEquals("192.168.1.100", body.get("targetIp"));
        assertEquals(8093, body.get("targetPort"));
    }

    @Test
    void toActionBodyWorksWithoutOptionalFields() {
        ControlDeliveryRequest request = new ControlDeliveryRequest();
        request.setGatewayDeviceId("GW-DEV-002");
        request.setTargetDeviceType("terminal_encrypt_gateway");
        request.setControlCommand("QUERY_STATUS");

        Map<String, Object> body = request.toActionBody();

        assertNotNull(body.get("commandTaskId"));
        assertNotNull(body.get("encryptedCommandPackage"));
        assertEquals("QUERY_STATUS", body.get("command"));

        String encryptedCommandPackage = (String) body.get("encryptedCommandPackage");
        byte[] decoded = Base64.getDecoder().decode(encryptedCommandPackage);
        JSONObject plainPackage = JSON.parseObject(new String(decoded, StandardCharsets.UTF_8));
        assertEquals("QUERY_STATUS", plainPackage.getString("command"));
        assertEquals(body.get("commandTaskId"), plainPackage.getString("commandTaskId"));
        assertNotNull(plainPackage.getJSONObject("source"));
        assertEquals("monitor-platform-forward", plainPackage.getJSONObject("source").getString("clientId"));
        assertNotNull(plainPackage.getJSONObject("target"));
        assertEquals("GW-DEV-002", plainPackage.getJSONObject("target").getString("deviceId"));
    }

    @Test
    void toActionBodyGeneratesUniqueCommandTaskIds() {
        ControlDeliveryRequest request = new ControlDeliveryRequest();
        request.setGatewayDeviceId("GW-DEV-003");
        request.setTargetDeviceType("publish_gateway");
        request.setControlCommand("REBOOT");

        Map<String, Object> body1 = request.toActionBody();
        Map<String, Object> body2 = request.toActionBody();

        assertNotNull(body1.get("commandTaskId"));
        assertNotNull(body2.get("commandTaskId"));
        assertTrue(!body1.get("commandTaskId").equals(body2.get("commandTaskId")),
                "each call should generate a different commandTaskId");
    }
}
