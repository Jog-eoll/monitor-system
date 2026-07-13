package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.publishgateway.udpproxy.entity.dto.control.ControlDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.control.ControlDeliveryTaskResponse;
import com.publishgateway.udpproxy.entity.dto.control.ControlTaskEncryptRequest;
import com.publishgateway.udpproxy.entity.dto.control.ControlTaskEncryptResponse;
import com.publishgateway.udpproxy.service.ControlDeliveryService;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GatewayCommandDispatcherTest {

    @Test
    public void queryStatusDoesNotFallbackToUdpProxyConfig() throws Exception {
        GatewayCommandDispatcher dispatcher = newDispatcher();
        CapturingLocalHttpForwardService httpForwardService = new CapturingLocalHttpForwardService();
        CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher();
        MqttAgentProperties properties = publishGatewayProperties();
        wire(dispatcher, httpForwardService, replyPublisher, properties);

        MqttCommandMessage command = new MqttCommandMessage();
        command.setCommand("QUERY_STATUS");

        dispatcher.onCommand(envelope("msg-query-status", command));

        assertEquals(0, httpForwardService.calls);
        assertEquals(2, replyPublisher.replies.size());
        assertEquals("PROCESSING", replyPublisher.replies.get(0).getStatus());
        MqttReplyMessage finalReply = replyPublisher.replies.get(1);
        assertEquals("SUCCESS", finalReply.getStatus());
        assertEquals("QUERY_STATUS", finalReply.getData().get("command"));
        assertEquals("publish-gateway-001", finalReply.getData().get("deviceId"));
        assertEquals("publish_gateway", finalReply.getData().get("deviceType"));
        assertEquals("publish-gateway", finalReply.getData().get("serviceName"));
        assertEquals(8092, finalReply.getData().get("localHttpPort"));
    }

    @Test
    public void actionBusinessCodeFailureMarksCommandFailed() throws Exception {
        GatewayCommandDispatcher dispatcher = newDispatcher();
        CapturingLocalHttpForwardService httpForwardService = new CapturingLocalHttpForwardService();
        Map<String, Object> actionResult = new LinkedHashMap<>();
        actionResult.put("code", 500);
        actionResult.put("msg", "config apply failed");
        actionResult.put("httpStatus", 200);
        actionResult.put("success", true);
        httpForwardService.result = actionResult;
        CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher();
        wire(dispatcher, httpForwardService, replyPublisher, publishGatewayProperties());

        MqttCommandMessage.Action action = new MqttCommandMessage.Action();
        action.setMode("SELF_APPLY");
        action.setPath("/udp-proxy/config");
        action.setHttpMethod("POST");
        MqttCommandMessage command = new MqttCommandMessage();
        command.setCommand("APPLY_CHAIN_CONFIG");
        command.setActions(Collections.singletonList(action));

        dispatcher.onCommand(envelope("msg-business-failure", command));

        assertEquals(1, httpForwardService.calls);
        assertEquals(2, replyPublisher.replies.size());
        MqttReplyMessage finalReply = replyPublisher.replies.get(1);
        assertEquals("FAILED", finalReply.getStatus());
        assertTrue(finalReply.getMessage().contains("config apply failed"));
    }

    @Test
    public void controlDeliveryAcceptedPollsFinalStatusBeforeSuccessReply() throws Exception {
        GatewayCommandDispatcher dispatcher = newDispatcher();
        CapturingLocalHttpForwardService httpForwardService = new CapturingLocalHttpForwardService();
        Map<String, Object> createData = new LinkedHashMap<>();
        createData.put("deliveryTaskId", "CDLV-test-001");
        createData.put("status", "ACCEPTED");
        Map<String, Object> actionResult = new LinkedHashMap<>();
        actionResult.put("code", 200);
        actionResult.put("msg", "success");
        actionResult.put("httpStatus", 200);
        actionResult.put("success", true);
        actionResult.put("data", createData);
        httpForwardService.result = actionResult;

        StubControlDeliveryService controlDeliveryService = new StubControlDeliveryService();
        Map<String, Object> finalStatus = new LinkedHashMap<>();
        finalStatus.put("found", true);
        finalStatus.put("deliveryTaskId", "CDLV-test-001");
        finalStatus.put("status", "FAILED");
        finalStatus.put("terminalStatus", "TARGET_NOT_FOUND");
        finalStatus.put("message", "target not found");
        controlDeliveryService.status = finalStatus;

        CapturingReplyPublisher replyPublisher = new CapturingReplyPublisher();
        wire(dispatcher, httpForwardService, replyPublisher, publishGatewayProperties());
        setFieldIfPresent(dispatcher, "controlDeliveryService", controlDeliveryService);
        setFieldIfPresent(dispatcher, "controlDeliveryPollIntervalMs", 1L);
        setFieldIfPresent(dispatcher, "controlDeliveryMonitorTimeoutMs", 200L);

        MqttCommandMessage.Action action = new MqttCommandMessage.Action();
        action.setMode("SELF_APPLY");
        action.setPath("/api/secure-delivery/control-tasks");
        action.setHttpMethod("POST");
        MqttCommandMessage command = new MqttCommandMessage();
        command.setCommand("CONTROL_DELIVERY");
        command.setActions(Collections.singletonList(action));

        dispatcher.onCommand(envelope("msg-control-delivery-failed", command));

        MqttReplyMessage finalReply = awaitTerminalReply(replyPublisher, 1000L);
        assertEquals("FAILED", finalReply.getStatus());
        assertTrue(finalReply.getMessage().contains("target not found"));
        assertEquals(1, controlDeliveryService.statusCalls);
    }

    @Test
    public void httpForwardAllowListMatchesExactPathOrChildPathOnly() throws Exception {
        LocalHttpForwardService service = new LocalHttpForwardService();
        setField(service, "allowedPaths", Arrays.asList("/api/client/commands", "/udp-proxy/config"));

        invokeValidateForwardTarget(service, "/api/client/commands", "GET");
        invokeValidateForwardTarget(service, "/api/client/commands/execute", "POST");
        invokeValidateForwardTarget(service, "/udp-proxy/config", "POST");

        try {
            invokeValidateForwardTarget(service, "/api/client/commands-bad", "POST");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            return;
        }
        throw new AssertionError("expected invalid child-like path to be rejected");
    }

    private GatewayCommandDispatcher newDispatcher() {
        return new GatewayCommandDispatcher();
    }

    private MqttAgentProperties publishGatewayProperties() {
        MqttAgentProperties properties = new MqttAgentProperties();
        properties.setDeviceId("publish-gateway-001");
        properties.setClientId("publish-gateway-001");
        properties.setDeviceType("publish_gateway");
        properties.setServiceName("publish-gateway");
        properties.setVersion("test-version");
        return properties;
    }

    private MqttEnvelope envelope(String messageId, MqttCommandMessage command) {
        MqttEnvelope envelope = new MqttEnvelope();
        envelope.setMessageId(messageId);
        envelope.setPayload(JSON.toJSONString(command));
        return envelope;
    }

    private void wire(GatewayCommandDispatcher dispatcher,
                      LocalHttpForwardService httpForwardService,
                      ReplyPublisher replyPublisher,
                      MqttAgentProperties properties) throws Exception {
        MqttCommandRecordService recordService = new MqttCommandRecordService();
        setField(dispatcher, "commandRecordService", recordService);
        setField(dispatcher, "localHttpForwardService", httpForwardService);
        setField(dispatcher, "systemNetworkChangeService", null);
        setField(dispatcher, "remoteUpgradeService", null);
        setField(dispatcher, "replyPublisher", replyPublisher);
        setField(dispatcher, "properties", properties);
        setField(dispatcher, "localHttpPort", 8092);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setFieldIfPresent(Object target, String fieldName, Object value) throws Exception {
        try {
            setField(target, fieldName, value);
        } catch (NoSuchFieldException ignored) {
            // Allows the red test to compile before the production field exists.
        }
    }

    private void invokeValidateForwardTarget(LocalHttpForwardService service, String path, String method) throws Exception {
        Method validate = LocalHttpForwardService.class.getDeclaredMethod("validateForwardTarget", String.class, String.class);
        validate.setAccessible(true);
        validate.invoke(service, path, method);
    }

    private MqttReplyMessage awaitTerminalReply(CapturingReplyPublisher replyPublisher, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        MqttReplyMessage lastReply = null;
        while (System.currentTimeMillis() < deadline) {
            lastReply = replyPublisher.lastReply();
            if (lastReply != null
                    && ("SUCCESS".equals(lastReply.getStatus()) || "FAILED".equals(lastReply.getStatus()))) {
                return lastReply;
            }
            Thread.sleep(10L);
        }
        if (lastReply != null) {
            return lastReply;
        }
        throw new AssertionError("no reply published");
    }

    private static class CapturingReplyPublisher extends ReplyPublisher {
        private final List<MqttReplyMessage> replies = new ArrayList<>();

        @Override
        public synchronized void publishReply(MqttReplyMessage reply) {
            replies.add(reply);
        }

        private synchronized MqttReplyMessage lastReply() {
            if (replies.isEmpty()) {
                return null;
            }
            return replies.get(replies.size() - 1);
        }
    }

    private static class CapturingLocalHttpForwardService extends LocalHttpForwardService {
        private int calls;
        private Map<String, Object> result;

        @Override
        public Map<String, Object> forward(String targetIp, int targetPort, String path,
                                           String httpMethod, Map<String, Object> body) {
            calls++;
            if (result != null) {
                return result;
            }
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("success", true);
            ok.put("httpStatus", 200);
            return ok;
        }
    }

    private static class StubControlDeliveryService implements ControlDeliveryService {
        private int statusCalls;
        private Map<String, Object> status;

        @Override
        public ControlTaskEncryptResponse encryptControlTask(ControlTaskEncryptRequest request) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ControlDeliveryTaskResponse createControlTask(ControlDeliveryTaskRequest request) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Map<String, Object> getControlTaskStatus(String commandTaskId) {
            statusCalls++;
            return status;
        }
    }
}
