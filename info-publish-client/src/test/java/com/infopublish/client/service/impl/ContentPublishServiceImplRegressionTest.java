package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishRequest;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.SigmaApiClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class ContentPublishServiceImplRegressionTest {

    public static void main(String[] args) throws Exception {
        ContentPublishServiceImplRegressionTest test = new ContentPublishServiceImplRegressionTest();
        test.configuredTwoGigabyteLimitDoesNotOverflow();
        test.videoWithoutDurationDefaultsToThirtySecondsBeforePrecheckAndDelivery();
        test.secureDeliveryTerminalFailureMakesMainResponseFalse();
        test.secureDeliverySuccessKeepsMainResponseTrue();
    }

    public void configuredTwoGigabyteLimitDoesNotOverflow() throws Exception {
        ContentPublishResponse response = runScenario("DLV-large-limit", "SUCCESS", "delivered", 2048);
        if (!response.isSuccess()) {
            throw new AssertionError("Expected content-publish.max-file-size-mb=2048 to allow a small file but got "
                    + response.getCode() + ": " + response.getMessage());
        }
    }

    public void videoWithoutDurationDefaultsToThirtySecondsBeforePrecheckAndDelivery() throws Exception {
        final byte[] videoBytes = new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
        final AtomicReference<PrecheckRequest> precheckRequest = new AtomicReference<>();
        final AtomicReference<String> deliveryBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files/qingsong.mp4", exchange -> send(exchange, 200,
                "application/octet-stream", videoBytes));
        server.createContext("/api/secure-delivery/tasks", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod())
                    && "/api/secure-delivery/tasks".equals(path)) {
                deliveryBody.set(readBody(exchange));
                sendJson(exchange, 200, "{\"deliveryTaskId\":\"DLV-video\",\"status\":\"ACCEPTED\",\"createdAt\":1}");
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())
                    && "/api/secure-delivery/tasks/DLV-video".equals(path)) {
                sendJson(exchange, 200,
                        "{\"found\":true,\"deliveryTaskId\":\"DLV-video\",\"status\":\"SUCCESS\",\"message\":\"delivered\"}");
                return;
            }
            sendJson(exchange, 404, "{\"found\":false,\"message\":\"not found\"}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ContentPublishServiceImpl service = new ContentPublishServiceImpl();
            setField(service, "sigmaApiClient", videoProgramClient(baseUrl, videoBytes));
            setField(service, "publishPrecheckService", new PublishPrecheckService() {
                @Override
                public PrecheckResponse precheck(PrecheckRequest request) {
                    precheckRequest.set(request);
                    PrecheckResponse response = PrecheckResponse.passed(request.getPlaylistId(), null, null,
                            request.getRequestId());
                    response.attachPermit("header.payload.signature", "digest-video");
                    response.attachProgram(request.getTarget(), request.getItems());
                    return response;
                }
            });
            setField(service, "restTemplate", new RestTemplate());
            setField(service, "gatewayUrl", baseUrl);
            setField(service, "downloadTimeoutMs", 3000);
            setField(service, "maxFileSizeMb", 1);

            ContentPublishResponse response = service.publish(publishRequest());
            if (!response.isSuccess()) {
                throw new AssertionError("Expected video publish to pass with default duration but got "
                        + response.getCode() + ": " + response.getMessage());
            }

            PrecheckRequest captured = precheckRequest.get();
            if (captured == null || captured.getItems() == null || captured.getItems().isEmpty()) {
                throw new AssertionError("Expected precheck to receive normalized video item");
            }
            Integer precheckDuration = captured.getItems().get(0).getDurationSeconds();
            if (!Integer.valueOf(30).equals(precheckDuration)) {
                throw new AssertionError("Expected video durationSeconds=30 before precheck but was "
                        + precheckDuration);
            }

            JSONObject body = JSON.parseObject(deliveryBody.get());
            Integer deliveryDuration = body.getJSONArray("files").getJSONObject(0).getInteger("durationSeconds");
            if (!Integer.valueOf(30).equals(deliveryDuration)) {
                throw new AssertionError("Expected secure-delivery video durationSeconds=30 but was "
                        + deliveryDuration);
            }
        } finally {
            server.stop(0);
        }
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
        return runScenario(deliveryTaskId, deliveryStatus, deliveryMessage, 1);
    }

    private ContentPublishResponse runScenario(String deliveryTaskId, String deliveryStatus,
                                               String deliveryMessage, int maxFileSizeMb) throws Exception {
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
            setField(service, "maxFileSizeMb", maxFileSizeMb);

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

    private static SigmaApiClient videoProgramClient(String baseUrl, byte[] videoBytes) throws Exception {
        final String fileHash = sha256Hex(videoBytes);
        return new SigmaApiClient() {
            @Override
            public QingsongProgramResponse.ProgramData getProgramByIp(String sigmaBaseUrl, String ip) {
                QingsongProgramResponse.ProgramData program = new QingsongProgramResponse.ProgramData();
                program.setSuccess(Boolean.TRUE);
                program.setPlaylistId("playlist-video");
                SigmaVerifyRequest.TargetRef target = new SigmaVerifyRequest.TargetRef();
                target.setIp(ip);
                target.setPort(9520);
                target.setVendorHint("JETFILEII");
                program.setTarget(target);
                SigmaVerifyRequest.PlaylistItem item = new SigmaVerifyRequest.PlaylistItem();
                item.setOrderNo(1);
                item.setFileName("qingsong.mp4");
                item.setFileType("video");
                item.setFileUrl(baseUrl + "/files/qingsong.mp4");
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

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream input = exchange.getRequestBody();
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return sb.toString();
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
