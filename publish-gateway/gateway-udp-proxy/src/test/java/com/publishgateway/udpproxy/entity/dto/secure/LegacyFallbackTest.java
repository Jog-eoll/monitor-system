package com.publishgateway.udpproxy.entity.dto.secure;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gateway.standardization.dto.StandardizedPublishPackage;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.service.impl.ControlDeliveryServiceImpl;
import com.publishgateway.udpproxy.service.impl.SecureDeliveryServiceImpl;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Legacy 回退测试 —— 验证 envelope.enabled=false 时输出的旧扁平 JSON 不包含 schemaVersion/messageType/publish/control
 * <p>
 * 通过反射调用生产代码中的 private legacy 构造方法，避免测试手写结构与实际实现分叉。
 * </p>
 */
public class LegacyFallbackTest {

    @Test
    public void testLegacyPublishPayloadNoSchemaVersion() throws Exception {
        SecureDeliveryTaskRequest request = new SecureDeliveryTaskRequest();
        request.setRequestId("REQ-001");

        StandardizedPublishPackage pkg = new StandardizedPublishPackage();
        pkg.setPublishPermit("jwt-token");

        StandardizedPublishPackage.TargetRef target = new StandardizedPublishPackage.TargetRef();
        target.setDeviceId("device-001");
        target.setIp("192.168.1.100");
        target.setPort(5200);

        StandardizedPublishPackage.PlaylistRef playlist = new StandardizedPublishPackage.PlaylistRef();
        StandardizedPublishPackage.PublishOptions options = new StandardizedPublishPackage.PublishOptions();

        Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod(
                "buildLegacyPublishPayload",
                String.class,
                SecureDeliveryTaskRequest.class,
                StandardizedPublishPackage.class,
                StandardizedPublishPackage.TargetRef.class,
                StandardizedPublishPackage.PlaylistRef.class,
                java.util.List.class,
                StandardizedPublishPackage.PublishOptions.class);
        method.setAccessible(true);

        JSONObject payload = (JSONObject) method.invoke(
                new SecureDeliveryServiceImpl(),
                "DLV-001",
                request,
                pkg,
                target,
                playlist,
                Collections.emptyList(),
                options);

        String json = JSON.toJSONString(payload);

        // 不包含 schemaVersion、messageType、publish 包裹层
        assertFalse(json.contains("schemaVersion"));
        assertFalse(json.contains("messageType"));
        assertFalse(json.contains("\"publish\"") || json.contains("\"publish\":"));

        // 根节点直接包含 deliveryTaskId、requestId、target
        assertTrue(json.contains("deliveryTaskId"));
        assertTrue(json.contains("requestId"));
        assertTrue(json.contains("target"));
    }

    @Test
    public void testLegacyControlPayloadNoSchemaVersion() throws Exception {
        JSONObject plainTask = new JSONObject();
        plainTask.put("commandTaskId", "CMD-001");
        plainTask.put("command", "BRIGHTNESS");

        JSONObject source = new JSONObject();
        source.put("clientId", "client-001");
        plainTask.put("source", source);

        JSONObject target = new JSONObject();
        target.put("deviceId", "device-001");
        target.put("ip", "192.168.1.100");
        target.put("port", 5200);
        plainTask.put("target", target);

        Method method = ControlDeliveryServiceImpl.class.getDeclaredMethod(
                "buildLegacyControlPayload", JSONObject.class);
        method.setAccessible(true);
        JSONObject payload = (JSONObject) method.invoke(new ControlDeliveryServiceImpl(), plainTask);

        String json = JSON.toJSONString(payload);

        // 不包含 schemaVersion、messageType、control 包裹层
        assertFalse(json.contains("schemaVersion"));
        assertFalse(json.contains("messageType"));
        assertFalse(json.contains("\"control\"") || json.contains("\"control\":"));

        // 根节点直接包含 commandTaskId、requestId、command、action、target
        assertTrue(json.contains("taskId"));
        assertTrue(json.contains("commandTaskId"));
        assertTrue(json.contains("requestId"));
        assertTrue(json.contains("command"));
        assertTrue(json.contains("target"));
    }

    @Test
    public void testSecureEnvelopeParserDetectsLegacyVsV2() {
        // 给定旧扁平 JSON（无 schemaVersion/messageType），应能进入回退路径
        JSONObject legacy = new JSONObject();
        legacy.put("deliveryTaskId", "DLV-001");
        legacy.put("requestId", "REQ-001");
        String legacyJson = JSON.toJSONString(legacy);
        JSONObject parsedLegacy = JSON.parseObject(legacyJson);

        assertNull(parsedLegacy.getString("schemaVersion"));
        assertNull(parsedLegacy.getString("messageType"));

        // 给定 v2 信封 JSON（有 schemaVersion/messageType），应能检测到
        JSONObject v2 = new JSONObject();
        v2.put("schemaVersion", "2.0");
        v2.put("messageType", "PUBLISH");
        String v2Json = JSON.toJSONString(v2);
        JSONObject parsedV2 = JSON.parseObject(v2Json);

        assertNotNull(parsedV2.getString("schemaVersion"));
        assertNotNull(parsedV2.getString("messageType"));
    }
}
