package com.monitorplatform.forward.service.impl;

import com.monitorplatform.forward.entity.TaskChainNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishGatewayConfigServiceImplTest {

    @Test
    void resolveMqttGatewayDeviceIdDerivesPublishGatewayAgentIdFromNodeIp() {
        PublishGatewayConfigServiceImpl service = new PublishGatewayConfigServiceImpl();
        TaskChainNode node = new TaskChainNode();
        node.setDeviceId("publish-001");
        node.setDeviceType("publish_gateway");
        node.setDeviceIp("192.168.1.25");

        String mqttDeviceId = ReflectionTestUtils.invokeMethod(service,
                "resolveMqttGatewayDeviceId", node);

        assertEquals("publish-gateway-25", mqttDeviceId);
    }

    @Test
    void resolveMqttGatewayDeviceIdKeepsRoutablePublishGatewayId() {
        PublishGatewayConfigServiceImpl service = new PublishGatewayConfigServiceImpl();
        TaskChainNode node = new TaskChainNode();
        node.setDeviceId("publish-gateway-25");
        node.setDeviceType("publish_gateway");
        node.setDeviceIp("192.168.1.25");

        String mqttDeviceId = ReflectionTestUtils.invokeMethod(service,
                "resolveMqttGatewayDeviceId", node);

        assertEquals("publish-gateway-25", mqttDeviceId);
    }
}
