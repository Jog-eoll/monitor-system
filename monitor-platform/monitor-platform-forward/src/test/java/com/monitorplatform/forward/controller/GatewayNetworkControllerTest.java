package com.monitorplatform.forward.controller;

import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.dto.GatewayNetworkChangeRequest;
import com.monitorplatform.forward.service.IpChangeCallbackService;
import com.monitorplatform.forward.service.MqttCommandPublishService;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayNetworkControllerTest {

    @Test
    void changeIpUsesChangeIdAsMqttBusinessId() {
        GatewayNetworkController controller = new GatewayNetworkController();
        MqttCommandPublishService publishService = Mockito.mock(MqttCommandPublishService.class);
        IpChangeCallbackService callbackService = Mockito.mock(IpChangeCallbackService.class);
        DeviceMqttCommand record = new DeviceMqttCommand();
        record.setId(8L);
        record.setMessageId("msg-change");
        record.setGatewayDeviceId("terminal-gateway-12");
        record.setTargetDeviceId("terminal-gateway-12");
        record.setCommand("CHANGE_SYSTEM_IP");
        record.setStatus(DeviceMqttCommand.STATUS_SUCCESS);

        when(publishService.publishCommand(eq("terminal-gateway-12"), eq("CHANGE_SYSTEM_IP"),
                anyMap(), ArgumentMatchers.<java.util.List<MqttCommandMessage.Action>>any(),
                eq("change-20260708-001"))).thenReturn(record);
        when(publishService.waitForFinalStatus(8L, 30)).thenReturn(record);
        when(publishService.isSuccess(record)).thenReturn(true);

        ReflectionTestUtils.setField(controller, "mqttCommandPublishService", publishService);
        ReflectionTestUtils.setField(controller, "ipChangeCallbackService", callbackService);

        GatewayNetworkChangeRequest request = new GatewayNetworkChangeRequest();
        request.setTargetDeviceId("terminal-gateway-12");
        request.setInterfaceName("eth0");
        request.setNewIp("192.168.77.88");
        request.setPrefixLength(24);
        request.setDryRun(true);
        request.setChangeId("change-20260708-001");

        controller.changeIp(request);

        verify(publishService).publishCommand(eq("terminal-gateway-12"), eq("CHANGE_SYSTEM_IP"),
                anyMap(), ArgumentMatchers.<java.util.List<MqttCommandMessage.Action>>any(),
                eq("change-20260708-001"));
        verify(callbackService, never()).onIpSuccess(anyString(), anyString());
    }
}
