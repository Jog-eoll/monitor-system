package com.monitorplatform.forward.controller;

import com.monitorplatform.forward.config.MqttDispatchProperties;
import com.monitorplatform.forward.feign.DeviceFeignClient;
import com.monitorplatform.mqtt.core.dto.MqttAclRequest;
import com.monitorplatform.mqtt.core.dto.MqttAuthRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MqttAccessControllerTest {

    private MqttAccessController controller;
    private MqttDispatchProperties properties;
    private DeviceFeignClient deviceFeignClient;

    @BeforeEach
    void setUp() {
        controller = new MqttAccessController();
        properties = new MqttDispatchProperties();
        deviceFeignClient = Mockito.mock(DeviceFeignClient.class);
        ReflectionTestUtils.setField(controller, "properties", properties);
        ReflectionTestUtils.setField(controller, "deviceFeignClient", deviceFeignClient);
    }

    // ======================== Auth 测试 ========================

    @Test
    void auth_disabled_alwaysAllow() {
        properties.setAuthEnabled(false);
        ResponseEntity<Void> resp = controller.auth(null);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    void auth_platform_correctCreds_allow() {
        properties.setMqttUsername("platform");
        properties.setMqttPassword("secret");
        properties.setPlatformClientId("monitor-platform-001");
        MqttAuthRequest req = new MqttAuthRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setPassword("secret");
        ResponseEntity<Void> resp = controller.auth(req);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    void auth_platform_wrongPassword_reject() {
        properties.setMqttUsername("platform");
        properties.setMqttPassword("secret");
        properties.setPlatformClientId("monitor-platform-001");
        MqttAuthRequest req = new MqttAuthRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setPassword("wrong");
        ResponseEntity<Void> resp = controller.auth(req);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void auth_device_correctPassword_allow() {
        properties.setDeviceDefaultPassword("device-shared");
        MqttAuthRequest req = new MqttAuthRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setPassword("device-shared");
        ResponseEntity<Void> resp = controller.auth(req);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    void auth_device_wrongPassword_reject() {
        properties.setDeviceDefaultPassword("device-shared");
        MqttAuthRequest req = new MqttAuthRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setPassword("bad");
        ResponseEntity<Void> resp = controller.auth(req);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void auth_device_noDefaultConfigured_reject() {
        MqttAuthRequest req = new MqttAuthRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setPassword("anything");
        ResponseEntity<Void> resp = controller.auth(req);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    // ======================== ACL 测试 ========================

    @Test
    void acl_platform_publishDown_topicAllowed() {
        properties.setPlatformClientId("monitor-platform-001");
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setAction("publish");
        req.setTopic("/default/site-001/gw-001/down/proxy-command");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("allow", resp.getBody().get("result"));
    }

    @Test
    void acl_platform_publishUp_topicDenied() {
        properties.setPlatformClientId("monitor-platform-001");
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setAction("publish");
        req.setTopic("/default/site-001/gw-001/up/reply");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("deny", resp.getBody().get("result"));
    }

    @Test
    void acl_platform_subscribeUp_topicAllowed() {
        properties.setPlatformClientId("monitor-platform-001");
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setAction("subscribe");
        req.setTopic("/+/+/+/up/#");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("allow", resp.getBody().get("result"));
    }

    @Test
    void acl_platform_subscribeDown_topicDenied() {
        properties.setPlatformClientId("monitor-platform-001");
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setAction("subscribe");
        req.setTopic("/+/+/+/down/#");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void acl_device_publishOwnUp_topicAllowed() {
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setAction("publish");
        req.setTopic("/default/site-001/gw-001/up/heartbeat");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("allow", resp.getBody().get("result"));
    }

    @Test
    void acl_device_publishOtherDevice_topicDenied() {
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setAction("publish");
        req.setTopic("/default/site-001/gw-002/up/heartbeat");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("deny", resp.getBody().get("result"));
    }

    @Test
    void acl_device_publishDown_topicDenied() {
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setAction("publish");
        req.setTopic("/default/site-001/gw-001/down/proxy-command");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void acl_device_subscribeOwnDown_topicAllowed() {
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setAction("subscribe");
        req.setTopic("/default/site-001/gw-001/down/proxy-command");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("allow", resp.getBody().get("result"));
    }

    @Test
    void acl_device_subscribeOtherDevice_topicDenied() {
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("gw-001");
        req.setUsername("gw-001");
        req.setAction("subscribe");
        req.setTopic("/default/site-001/gw-999/down/command");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void acl_nullRequest_reject() {
        ResponseEntity<Map<String, Object>> resp = controller.acl(null);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void acl_missingClientId_reject() {
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("   ");
        req.setAction("publish");
        req.setTopic("/default/site-001/gw-001/up/heartbeat");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void acl_topicFormatInvalid_reject() {
        properties.setPlatformClientId("monitor-platform-001");
        MqttAclRequest req = new MqttAclRequest();
        req.setClientId("monitor-platform-001");
        req.setUsername("platform");
        req.setAction("publish");
        req.setTopic("too/few/segments");
        ResponseEntity<Map<String, Object>> resp = controller.acl(req);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }
}
