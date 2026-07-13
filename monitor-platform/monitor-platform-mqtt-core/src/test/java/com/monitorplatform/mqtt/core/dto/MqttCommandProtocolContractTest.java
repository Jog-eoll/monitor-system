package com.monitorplatform.mqtt.core.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MqttCommandProtocolContractTest {

    @Test
    void commandMessageExposesSchemaVersionAndGatewayProbeCommands() throws Exception {
        assertEquals("QUERY_STATUS", publicStaticString(MqttCommandMessage.class, "COMMAND_QUERY_STATUS"));
        assertEquals("NOOP", publicStaticString(MqttCommandMessage.class, "COMMAND_NOOP"));
        assertEquals("ECHO", publicStaticString(MqttCommandMessage.class, "COMMAND_ECHO"));

        Field schemaVersion = MqttCommandMessage.class.getDeclaredField("schemaVersion");
        assertNotNull(schemaVersion);

        MqttCommandMessage command = new MqttCommandMessage();
        Method getter = MqttCommandMessage.class.getMethod("getSchemaVersion");
        assertEquals("1.0", getter.invoke(command));
    }

    @Test
    void replyMessageCarriesSchemaVersionErrorCodeAndProgress() throws Exception {
        assertEquals("EXECUTION_FAILED", publicStaticString(MqttReplyMessage.class, "ERROR_EXECUTION_FAILED"));
        assertEquals("REJECTED", publicStaticString(MqttReplyMessage.class, "ERROR_REJECTED"));

        MqttReplyMessage failed = MqttReplyMessage.failed("cmd-1", "gw-1", "failed");
        assertEquals("1.0", MqttReplyMessage.class.getMethod("getSchemaVersion").invoke(failed));
        assertEquals("EXECUTION_FAILED", MqttReplyMessage.class.getMethod("getErrorCode").invoke(failed));

        MqttReplyMessage processing = MqttReplyMessage.processing("cmd-1", "gw-1");
        MqttReplyMessage.class.getMethod("setProgress", Integer.class).invoke(processing, 50);
        assertEquals(50, MqttReplyMessage.class.getMethod("getProgress").invoke(processing));
    }

    private String publicStaticString(Class<?> type, String name) throws Exception {
        Field field = type.getField(name);
        return String.valueOf(field.get(null));
    }
}
