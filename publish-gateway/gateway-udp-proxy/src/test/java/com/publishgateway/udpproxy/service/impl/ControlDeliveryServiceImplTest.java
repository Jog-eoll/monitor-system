package com.publishgateway.udpproxy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.config.SecureDeliveryEnvelopeProperties;
import com.publishgateway.udpproxy.entity.dto.control.ControlDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.control.ControlDeliveryTaskResponse;
import com.publishgateway.udpproxy.entity.dto.secure.SecureTerminalClient;
import com.publishgateway.udpproxy.service.CryptoService;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ControlDeliveryServiceImplTest {

    @Test
    public void createControlTaskAcceptsPlaintextPackageWhenDecryptReturnsNull() throws Exception {
        ControlDeliveryServiceImpl service = new ControlDeliveryServiceImpl();
        setField(service, "cryptoService", new NullDecryptCryptoService());
        setField(service, "secureTerminalClient", new StubSecureTerminalClient());
        setField(service, "envelopeProperties", new SecureDeliveryEnvelopeProperties());
        setField(service, "terminalGatewayUrl", "http://127.0.0.1:8093");
        service.init();

        try {
            ControlDeliveryTaskRequest request = new ControlDeliveryTaskRequest();
            request.setCommandTaskId("CTRL-plain-001");
            request.setCommand("QUERY_STATUS");
            JSONObject plain = new JSONObject();
            plain.put("commandTaskId", "CTRL-plain-001");
            plain.put("command", "QUERY_STATUS");
            JSONObject source = new JSONObject();
            source.put("clientId", "monitor-platform-forward");
            plain.put("source", source);
            JSONObject target = new JSONObject();
            target.put("deviceId", "terminal-gateway-26");
            target.put("ip", "192.168.1.26");
            target.put("port", 8093);
            plain.put("target", target);
            request.setEncryptedCommandPackage(Base64.getEncoder().encodeToString(
                    JSON.toJSONString(plain).getBytes(StandardCharsets.UTF_8)));

            ControlDeliveryTaskResponse response = service.createControlTask(request);

            assertNotNull(response.getDeliveryTaskId());
            assertEquals("ACCEPTED", response.getStatus());
        } finally {
            service.destroy();
        }
    }

    private static class NullDecryptCryptoService implements CryptoService {
        @Override
        public byte[] encrypt(byte[] data) {
            return data;
        }

        @Override
        public byte[] decrypt(byte[] data) {
            return null;
        }
    }

    private static class StubSecureTerminalClient extends SecureTerminalClient {
        @Override
        public ResponseEntity<String> postControl(String baseUrl, byte[] encryptedBytes,
                                                  String requestId, String commandTaskId) {
            return ResponseEntity.ok("{\"code\":200,\"status\":\"ACCEPTED\",\"batchTaskId\":\"BATCH-1\",\"mappedCapability\":\"QUERY_STATUS\"}");
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
