package com.monitorplatform.forward.service;

import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttReplyHandlerTest {

    @Test
    void processingReplyDoesNotOverwriteFinalSuccess() {
        DeviceMqttCommand command = new DeviceMqttCommand();
        command.setId(1L);
        command.setMessageId("msg-1");
        command.setStatus(DeviceMqttCommand.STATUS_SUCCESS);

        DeviceMqttCommandMapper mapper = Mockito.mock(DeviceMqttCommandMapper.class);
        when(mapper.selectOne(any())).thenReturn(command);

        MqttReplyHandler handler = new MqttReplyHandler();
        ReflectionTestUtils.setField(handler, "deviceMqttCommandMapper", mapper);

        handler.handleReply(MqttReplyMessage.processing("msg-1", "gw-1"));

        assertEquals(DeviceMqttCommand.STATUS_SUCCESS, command.getStatus());
        verify(mapper, never()).updateById(any(DeviceMqttCommand.class));
    }

    @Test
    void failedReplyPersistsErrorCodeAndReplyPayload() throws Exception {
        DeviceMqttCommand.class.getDeclaredField("errorCode");
        DeviceMqttCommand.class.getDeclaredField("replyPayload");

        DeviceMqttCommand command = new DeviceMqttCommand();
        command.setId(2L);
        command.setMessageId("msg-2");
        command.setStatus(DeviceMqttCommand.STATUS_PROCESSING);

        DeviceMqttCommandMapper mapper = Mockito.mock(DeviceMqttCommandMapper.class);
        when(mapper.selectOne(any())).thenReturn(command);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("detail", "bad target");
        MqttReplyMessage reply = MqttReplyMessage.failed("msg-2", "gw-1", "failed");
        MqttReplyMessage.class.getMethod("setErrorCode", String.class).invoke(reply, "TARGET_OFFLINE");
        reply.setData(data);

        MqttReplyHandler handler = new MqttReplyHandler();
        ReflectionTestUtils.setField(handler, "deviceMqttCommandMapper", mapper);

        handler.handleReply(reply);

        assertEquals(DeviceMqttCommand.STATUS_FAILED, command.getStatus());
        assertEquals("TARGET_OFFLINE", field(command, "errorCode"));
        assertEquals("{\"detail\":\"bad target\"}", field(command, "replyPayload"));
        verify(mapper).updateById(command);
    }

    @Test
    void failedReplyWritesCommandLifecycleEvent() {
        DeviceMqttCommand command = new DeviceMqttCommand();
        command.setId(3L);
        command.setMessageId("msg-3");
        command.setStatus(DeviceMqttCommand.STATUS_PROCESSING);

        DeviceMqttCommandMapper mapper = Mockito.mock(DeviceMqttCommandMapper.class);
        when(mapper.selectOne(any())).thenReturn(command);

        MqttCommandEventService eventService = Mockito.mock(MqttCommandEventService.class);

        MqttReplyMessage reply = MqttReplyMessage.failed("msg-3", "gw-1", "config apply failed");
        MqttReplyHandler handler = new MqttReplyHandler();
        ReflectionTestUtils.setField(handler, "deviceMqttCommandMapper", mapper);
        ReflectionTestUtils.setField(handler, "mqttCommandEventService", eventService);

        handler.handleReply(reply);

        verify(eventService).record(command, DeviceMqttCommand.STATUS_FAILED, reply);
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
