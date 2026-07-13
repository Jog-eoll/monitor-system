package com.monitorplatform.forward.service;

import com.monitorplatform.forward.config.MqttDispatchProperties;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttCommandPublishServiceTest {

    @Test
    void publishCommandWritesCreatedAndPublishedLifecycleEvents() throws Exception {
        MqttCommandPublishService service = new MqttCommandPublishService();
        DeviceMqttCommandMapper mapper = Mockito.mock(DeviceMqttCommandMapper.class);
        MqttCommandEventService eventService = Mockito.mock(MqttCommandEventService.class);
        MqttClient mqttClient = Mockito.mock(MqttClient.class);
        MqttDispatchProperties properties = new MqttDispatchProperties();
        properties.setMode("mqtt");
        properties.setTenantId("tenant-a");
        properties.setSiteId("site-a");
        properties.setQos(1);
        properties.setCommandTimeoutSec(30);

        doAnswer(invocation -> {
            DeviceMqttCommand command = invocation.getArgument(0);
            command.setId(100L);
            return 1;
        }).when(mapper).insert(any(DeviceMqttCommand.class));
        when(mqttClient.isConnected()).thenReturn(true);

        ReflectionTestUtils.setField(service, "deviceMqttCommandMapper", mapper);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "mqttCommandEventService", eventService);
        ReflectionTestUtils.setField(service, "mqttClient", mqttClient);

        DeviceMqttCommand record = service.publishCommand("terminal-gateway-12",
                MqttCommandMessage.COMMAND_QUERY_STATUS, Collections.emptyMap(),
                Collections.emptyList(), "biz-query-001");

        assertNotNull(record);
        assertEquals(DeviceMqttCommand.STATUS_PUBLISHED, record.getStatus());
        assertEquals("biz-query-001", record.getBusinessId());
        verify(mapper).insert(any(DeviceMqttCommand.class));
        verify(mapper).updateById(record);

        InOrder inOrder = Mockito.inOrder(eventService);
        inOrder.verify(eventService).record(any(DeviceMqttCommand.class),
                eq(DeviceMqttCommand.STATUS_CREATED), any());
        inOrder.verify(eventService).record(record,
                DeviceMqttCommand.STATUS_PUBLISHED, record);
    }
}
