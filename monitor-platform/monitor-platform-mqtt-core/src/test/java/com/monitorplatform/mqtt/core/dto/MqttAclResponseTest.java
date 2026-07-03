package com.monitorplatform.mqtt.core.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MqttAclResponseTest {

    @Test
    void allow_populatesTenantSiteDevice() {
        MqttAclResponse r = MqttAclResponse.allow("tenant-a", "site-001", "gw-001");
        assertTrue(r.isAllowed());
        assertEquals("tenant-a", r.getTenantId());
        assertEquals("site-001", r.getSiteId());
        assertEquals("gw-001", r.getDeviceId());
    }

    @Test
    void allow_nullFields_doNotAppearInResultMap() {
        MqttAclResponse r = MqttAclResponse.allow(null, null, null);
        Map<String, Object> map = r.toResultMap();
        assertEquals("allow", map.get("result"));
        assertFalse(map.containsKey("tenantId"));
        assertFalse(map.containsKey("reason"));
    }

    @Test
    void deny_setsReason() {
        MqttAclResponse r = MqttAclResponse.deny("topic not permitted");
        assertFalse(r.isAllowed());
        assertEquals("topic not permitted", r.getReason());
        assertEquals("deny", r.toResultMap().get("result"));
        assertEquals("topic not permitted", r.toResultMap().get("reason"));
    }
}
