package com.monitorplatform.forward.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttCommandControllerContractTest {

    @Test
    void exposesMinimalMqttCommandApiSurface() throws Exception {
        Class<?> controller = Class.forName("com.monitorplatform.forward.controller.MqttCommandController");
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/api/mqtt/commands"}, mapping.value());

        Method queryStatus = controller.getMethod("queryStatus",
                Class.forName("com.monitorplatform.forward.entity.dto.MqttQueryStatusRequest"));
        PostMapping postMapping = queryStatus.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/query-status"}, postMapping.value());

        boolean hasGetByMessageId = Arrays.stream(controller.getMethods()).anyMatch(method -> {
            GetMapping getMapping = method.getAnnotation(GetMapping.class);
            return getMapping != null && Arrays.asList(getMapping.value()).contains("/{messageId}");
        });
        assertTrue(hasGetByMessageId);
    }
}
