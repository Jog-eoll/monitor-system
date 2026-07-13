package com.monitorplatform.forward.controller;

import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.dto.ControlDeliveryRequest;
import com.monitorplatform.forward.service.DeviceCommandDispatcher;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttControlDeliveryControllerTest {

    @Test
    void publishGatewayDeliverySelfAppliesAndKeepsTargetInsideBody() {
        MqttControlDeliveryController controller = new MqttControlDeliveryController();
        DeviceCommandDispatcher dispatcher = Mockito.mock(DeviceCommandDispatcher.class);
        DeviceMqttCommand record = new DeviceMqttCommand();
        record.setId(9L);
        record.setMessageId("msg-control");
        record.setStatus(DeviceMqttCommand.STATUS_SUCCESS);

        when(dispatcher.dispatch(eq("publish-gateway-25"), eq("CONTROL_DELIVERY"),
                anyMap(), any(), eq("ctrl-001"))).thenReturn(record);
        when(dispatcher.waitForFinalStatus(9L, 30)).thenReturn(record);
        when(dispatcher.isSuccess(record)).thenReturn(true);
        ReflectionTestUtils.setField(controller, "deviceCommandDispatcher", dispatcher);

        ControlDeliveryRequest request = new ControlDeliveryRequest();
        request.setGatewayDeviceId("publish-gateway-25");
        request.setTargetDeviceId("terminal-gateway-26");
        request.setTargetDeviceType("publish_gateway");
        request.setControlCommand("QUERY_STATUS");
        request.setTargetIp("192.168.1.26");
        request.setTargetPort(8093);
        request.setBusinessId("ctrl-001");

        controller.controlDelivery(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MqttCommandMessage.Action>> actionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(eq("publish-gateway-25"), eq("CONTROL_DELIVERY"),
                anyMap(), actionsCaptor.capture(), eq("ctrl-001"));
        List<MqttCommandMessage.Action> actions = actionsCaptor.getValue();
        assertEquals(1, actions.size());
        MqttCommandMessage.Action action = actions.get(0);
        assertEquals("SELF_APPLY", action.getMode());
        assertEquals("/api/secure-delivery/control-tasks", action.getPath());

        Map<String, Object> body = action.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) body.get("target");
        assertNotNull(target);
        assertEquals("terminal-gateway-26", target.get("deviceId"));
        assertEquals("192.168.1.26", target.get("ip"));
        assertEquals(8093, target.get("port"));
    }
}
