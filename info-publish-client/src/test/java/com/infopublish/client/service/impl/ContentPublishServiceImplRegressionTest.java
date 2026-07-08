package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishRequest;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.SigmaApiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

public class ContentPublishServiceImplRegressionTest {

    public static void main(String[] args) throws Exception {
        ContentPublishServiceImplRegressionTest test = new ContentPublishServiceImplRegressionTest();
        test.secureDeliveryTerminalFailureMakesMainResponseFalse();
        test.secureDeliverySuccessKeepsMainResponseTrue();
    }

    public void secureDeliveryTerminalFailureMakesMainResponseFalse() throws Exception {
        ContentPublishResponse response = runScenario("DLV-failed", "FAILED",
                "file download failed: no route");
        if (response.isSuccess()) {
            throw new AssertionError("Expected success=false when secure-delivery task status is FAILED");
        }
        if (!"DELIVERY_FAILED".equals(response.getCode())) {
            throw new AssertionError("Expected code=DELIVERY_FAILED but was " + response.getCode());
        }
        if (response.getDelivery() == null) {
            throw new AssertionError("Expected delivery summary to be present");
        }
        if (!"DLV-failed".equals(response.getDelivery().getDeliveryTaskId())) {
            throw new AssertionError("Expected failed deliveryTaskId to be preserved");
        }
        if (!"FAILED".equals(response.getDelivery().getStatus())) {
            throw new AssertionError("Expected delivery.status=FAILED but was "
                    + response.getDelivery().getStatus());
        }
        if (response.getMessage() == null || !response.getMessage().contains("file download failed")) {
            throw new AssertionError("Expected gateway failure message to be propagated");
        }
    }

    public void secureDeliverySuccessKeepsMainResponseTrue() throws Exception {
        ContentPublishResponse response = runScenario("DLV-success", "SUCCESS", "delivered");
        if (!response.isSuccess()) {
            throw new AssertionError("Expected success=true when secure-delivery task status is SUCCESS");
        }
        if (response.getDelivery() == null) {
            throw new AssertionError("Expected delivery summary to be present");
        }
        if (!"DLV-success".equals(response.getDelivery().getDeliveryTaskId())) {
            throw new AssertionError("Expected successful deliveryTaskId to be preserved");
        }
        if (!"SUCCESS".equals(response.getDelivery().getStatus())) {
            throw new AssertionError("Expected delivery.status=SUCCESS but was "
                    + response.getDelivery().getStatus());
        }
    }

    private ContentPublishResponse runScenario(String deliveryTaskId, String deliveryStatus,
                                               String deliveryMessage) throws Exception {
        final byte[] imageBytes = new byte[]{0x42, 0x4d, 0x01, 0x00};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files/qingsong.bmp", exchange -> send(exchange, 200,
                "application/octet-stream", imageBytes));
        server.createContext("/api/secure-delivery/tasks", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod())
                    && "/api/secure-delivery/tasks".equals(path)) {
                sendJson(exchange, 200, "{\"deliveryTaskId\":\"" + deliveryTaskId
                        + "\",\"status\":\"ACCEPTED\",\"createdAt\":1}");
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())
                    && ("/api/secure-delivery/tasks/" + deliveryTaskId).equals(path)) {
                sendJson(exchange, 200, "{\"found\":true,\"deliveryTaskId\":\"" + deliveryTaskId
                        + "\",\"status\":\"" + deliveryStatus + "\",\"message\":\""
                        + deliveryMessage + "\"}");
                return;
            }
            sendJson(exchange, 404, "{\"found\":false,\"message\":\"not found\"}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ContentPublishServiceImpl service = new ContentPublishServiceImpl();
            setField(service, "sigmaApiClient", programClient(baseUrl, imageBytes));
            setField(service, "publishPrecheckService", passingPrecheck());
            setField(service, "restTemplate", new RestTemplate());
            setField(service, "gatewayUrl", baseUrl);
            setField(service, "downloadTimeoutMs", 3000);
            setField(service, "maxFileSizeMb", 1);

            return service.publish(publishRequest());
        } finally {
            server.stop(0);
        }
    }

    private static SigmaApiClient programClient(String baseUrl, byte[] imageBytes) throws Exception {
        final String fileHash = sha256Hex(imageBytes);
        return new SigmaApiClient() {
            @Override
            public QingsongProgramResponse.ProgramData getProgramByIp(String sigmaBaseUrl, String ip) {
                QingsongProgramResponse.ProgramData program = new QingsongProgramResponse.ProgramData();
                program.setSuccess(Boolean.TRUE);
                program.setPlaylistId("playlist-1");
                SigmaVerifyRequest.TargetRef target = new SigmaVerifyRequest.TargetRef();
                target.setIp(ip);
                target.setPort(9520);
                target.setVendorHint("JETFILEII");
                program.setTarget(target);
                SigmaVerifyRequest.PlaylistItem item = new SigmaVerifyRequest.PlaylistItem();
                item.setOrderNo(1);
                item.setFileName("qingsong.bmp");
                item.setFileType("image");
                item.setFileUrl(baseUrl + "/files/qingsong.bmp");
                item.setDurationSeconds(5);
                item.setFileHash(fileHash);
                program.setItems(Collections.singletonList(item));
                return program;
            }

            @Override
            public byte[] downloadFileContent(String sigmaBaseUrl, String playlistId, String innerFileId) {
                throw new UnsupportedOperationException("not used");
            }
        };
    }

    private static PublishPrecheckService passingPrecheck() {
        return new PublishPrecheckService() {
            @Override
            public PrecheckResponse precheck(PrecheckRequest request) {
                PrecheckResponse response = PrecheckResponse.passed("playlist-1", null, null,
                        request.getRequestId());
                response.attachPermit("header.payload.signature", "digest-1");
                response.attachProgram(request.getTarget(), request.getItems());
                return response;
            }
        };
    }

    private static ContentPublishRequest publishRequest() {
        ContentPublishRequest request = new ContentPublishRequest();
        request.setRequestId("REQ-regression-1");
        request.setSigmaBaseUrl("http://sigma.local");
        request.setOperatorId("tester");
        request.setTimeoutMs(2000);
        SigmaVerifyRequest.TargetRef target = new SigmaVerifyRequest.TargetRef();
        target.setIp("192.168.113.88");
        target.setPort(9520);
        target.setVendorHint("JETFILEII");
        request.setTarget(target);
        ContentPublishRequest.Options options = new ContentPublishRequest.Options();
        options.setWaitForDelivery(Boolean.FALSE);
        request.setOptions(options);
        return request;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(body);
        } finally {
            output.close();
        }
    }

    private static String sha256Hex(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
