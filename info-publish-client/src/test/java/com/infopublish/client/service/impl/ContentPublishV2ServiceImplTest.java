package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import com.infopublish.client.service.PublishPrecheckService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ContentPublishV2ServiceImplTest {

    public static void main(String[] args) throws Exception {
        ContentPublishV2ServiceImplTest test = new ContentPublishV2ServiceImplTest();
        test.publishV2UsesPrecheckAndKeepsSecureDeliveryJsonContract();
        test.publishV2RejectsHashMismatchBeforePrecheck();
        test.publishV2RejectsInvalidPortAsInvalidTarget();
        test.publishV2ReturnsDeliveryFailedWhenStatusQueryFails();
        test.sigmaPublishControllerExposesMultipartExecuteV2();
        test.sigmaPublishControllerExposesPrecheckV2();
    }

    public void publishV2UsesPrecheckAndKeepsSecureDeliveryJsonContract() throws Exception {
        final byte[] imageBytes = new byte[]{0x42, 0x4d, 0x02, 0x00};
        final AtomicReference<PrecheckRequest> precheckRequest = new AtomicReference<>();
        final AtomicReference<String> deliveryBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/secure-delivery/tasks", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && "/api/secure-delivery/tasks".equals(path)) {
                deliveryBody.set(readBody(exchange));
                sendJson(exchange, 200, "{\"deliveryTaskId\":\"DLV-v2\",\"status\":\"ACCEPTED\",\"createdAt\":1}");
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/secure-delivery/tasks/DLV-v2".equals(path)) {
                sendJson(exchange, 200, "{\"found\":true,\"deliveryTaskId\":\"DLV-v2\",\"status\":\"SUCCESS\",\"message\":\"delivered\"}");
                return;
            }
            sendJson(exchange, 404, "{\"found\":false}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            Object service = createService(baseUrl, new PublishPrecheckService() {
                @Override
                public PrecheckResponse precheck(PrecheckRequest request) {
                    precheckRequest.set(request);
                    PrecheckResponse response = PrecheckResponse.passed(request.getPlaylistId(), null, null,
                            request.getRequestId());
                    response.attachPermit("header.payload.signature", "digest-v2");
                    response.attachProgram(request.getTarget(), request.getItems());
                    return response;
                }
            });

            Object responseObject = invokePublish(service, baseJson("REQ-V2-001", "MEDIA_MULTI_UPLOAD"),
                    playlistJson(sha256Hex(imageBytes)), new MultipartFile[]{
                            new MemoryMultipartFile("f1", "B002.jpg", "image/jpeg", imageBytes)
                    });
            ContentPublishResponse response = (ContentPublishResponse) responseObject;

            if (!response.isSuccess()) {
                throw new AssertionError("Expected V2 publish success but got " + response.getCode()
                        + ": " + response.getMessage());
            }
            PrecheckRequest captured = precheckRequest.get();
            if (captured == null) {
                throw new AssertionError("Expected V2 publish to call precheck");
            }
            if (captured.getSigmaBaseUrl() != null) {
                throw new AssertionError("V2 precheck must not use sigmaBaseUrl");
            }
            if (captured.getItems() == null || captured.getItems().size() != 1) {
                throw new AssertionError("Expected one item passed into precheck");
            }
            String fileUrl = captured.getItems().get(0).getFileUrl();
            if (fileUrl == null || !fileUrl.startsWith(baseUrl + "/api/client/publish/v2/files/")) {
                throw new AssertionError("Expected precheck item fileUrl to use V2 temp download endpoint: " + fileUrl);
            }

            String body = deliveryBody.get();
            if (body == null) {
                throw new AssertionError("Expected V2 publish to call secure-delivery");
            }
            JSONObject parsed = JSON.parseObject(body);
            if (!"REQ-V2-001".equals(parsed.getString("requestId"))) {
                throw new AssertionError("requestId not preserved in secure-delivery body");
            }
            if (!"header.payload.signature".equals(parsed.getString("publishPermit"))) {
                throw new AssertionError("publishPermit from precheck not forwarded");
            }
            if (!"digest-v2".equals(parsed.getJSONObject("playlist").getString("digest"))) {
                throw new AssertionError("playlist digest from precheck not forwarded");
            }
            if (parsed.getJSONArray("files") == null
                    || parsed.getJSONArray("files").getJSONObject(0).getString("fileUrl") == null) {
                throw new AssertionError("secure-delivery files must contain fileUrl");
            }
            if (body.contains("contentBase64")) {
                throw new AssertionError("V2 client must not change secure-delivery contract to inline file bytes");
            }
        } finally {
            server.stop(0);
        }
    }

    public void publishV2RejectsHashMismatchBeforePrecheck() throws Exception {
        final AtomicBoolean precheckCalled = new AtomicBoolean(false);
        Object service = createService("http://127.0.0.1:18080", new PublishPrecheckService() {
            @Override
            public PrecheckResponse precheck(PrecheckRequest request) {
                precheckCalled.set(true);
                return PrecheckResponse.error("should not be called", request.getRequestId());
            }
        });

        Object responseObject = invokePublish(service, baseJson("REQ-V2-BAD-HASH", "MEDIA_MULTI_UPLOAD"),
                playlistJson("0000000000000000000000000000000000000000000000000000000000000000"),
                new MultipartFile[]{
                        new MemoryMultipartFile("f1", "B002.jpg", "image/jpeg", new byte[]{0x01, 0x02})
                });
        ContentPublishResponse response = (ContentPublishResponse) responseObject;
        if (response.isSuccess()) {
            throw new AssertionError("Expected hash mismatch to reject V2 publish");
        }
        if (!"FILE_HASH_MISMATCH".equals(response.getCode())) {
            throw new AssertionError("Expected FILE_HASH_MISMATCH but was " + response.getCode());
        }
        if (precheckCalled.get()) {
            throw new AssertionError("Hash mismatch must be rejected before precheck");
        }
    }

    public void publishV2RejectsInvalidPortAsInvalidTarget() throws Exception {
        final AtomicBoolean precheckCalled = new AtomicBoolean(false);
        Object service = createService("http://127.0.0.1:18080", new PublishPrecheckService() {
            @Override
            public PrecheckResponse precheck(PrecheckRequest request) {
                precheckCalled.set(true);
                return PrecheckResponse.error("should not be called", request.getRequestId());
            }
        });

        String baseJson = "{"
                + "\"requestId\":\"REQ-V2-BAD-PORT\","
                + "\"operatorId\":\"DESKTOP-ABC123\","
                + "\"target\":{\"ip\":\"192.168.113.88\",\"port\":\"not-a-port\"},"
                + "\"command\":\"MEDIA_MULTI_UPLOAD\""
                + "}";
        ContentPublishResponse response = (ContentPublishResponse) invokePublish(service,
                baseJson, playlistJson("0000000000000000000000000000000000000000000000000000000000000000"),
                new MultipartFile[0]);
        if (!"INVALID_TARGET".equals(response.getCode())) {
            throw new AssertionError("Expected INVALID_TARGET but was " + response.getCode());
        }
        if (precheckCalled.get()) {
            throw new AssertionError("Invalid target must be rejected before precheck");
        }
    }

    public void publishV2ReturnsDeliveryFailedWhenStatusQueryFails() throws Exception {
        final byte[] imageBytes = new byte[]{0x22, 0x33, 0x44};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/secure-delivery/tasks", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && "/api/secure-delivery/tasks".equals(path)) {
                readBody(exchange);
                sendJson(exchange, 200, "{\"deliveryTaskId\":\"DLV-v2-failed\",\"status\":\"ACCEPTED\"}");
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())
                    && "/api/secure-delivery/tasks/DLV-v2-failed".equals(path)) {
                sendJson(exchange, 500, "{\"status\":\"FAILED\",\"message\":\"gateway query error\"}");
                return;
            }
            sendJson(exchange, 404, "{\"found\":false}");
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            Object service = createService(baseUrl, new PublishPrecheckService() {
                @Override
                public PrecheckResponse precheck(PrecheckRequest request) {
                    PrecheckResponse response = PrecheckResponse.passed(request.getPlaylistId(), null, null,
                            request.getRequestId());
                    response.attachPermit("permit-v2", "digest-v2");
                    response.attachProgram(request.getTarget(), request.getItems());
                    return response;
                }
            });

            ContentPublishResponse response = (ContentPublishResponse) invokePublish(service,
                    baseJson("REQ-V2-QUERY-FAIL", "MEDIA_MULTI_UPLOAD"),
                    playlistJson(sha256Hex(imageBytes)),
                    new MultipartFile[]{
                            new MemoryMultipartFile("f1", "B002.jpg", "image/jpeg", imageBytes)
                    });
            if (response.isSuccess()) {
                throw new AssertionError("Expected status query failure to fail V2 publish");
            }
            if (!"DELIVERY_FAILED".equals(response.getCode())) {
                throw new AssertionError("Expected DELIVERY_FAILED but was " + response.getCode());
            }
        } finally {
            server.stop(0);
        }
    }

    public void sigmaPublishControllerExposesMultipartExecuteV2() throws Exception {
        Class<?> controllerClass = Class.forName("com.infopublish.client.controller.SigmaPublishController");
        Method method = null;
        for (Method candidate : controllerClass.getDeclaredMethods()) {
            PostMapping mapping = candidate.getAnnotation(PostMapping.class);
            if (mapping != null) {
                for (String value : mapping.value()) {
                    if ("/publish/executeV2".equals(value)) {
                        method = candidate;
                    }
                }
            }
        }
        if (method == null) {
            throw new AssertionError("Expected SigmaPublishController to expose /publish/executeV2");
        }
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        boolean multipart = false;
        for (String consumes : mapping.consumes()) {
            if (MediaType.MULTIPART_FORM_DATA_VALUE.equals(consumes)) {
                multipart = true;
            }
        }
        if (!multipart) {
            throw new AssertionError("Expected /publish/executeV2 to consume multipart/form-data");
        }
    }

    public void sigmaPublishControllerExposesPrecheckV2() throws Exception {
        Class<?> controllerClass = Class.forName("com.infopublish.client.controller.SigmaPublishController");
        Method method = null;
        for (Method candidate : controllerClass.getDeclaredMethods()) {
            PostMapping mapping = candidate.getAnnotation(PostMapping.class);
            if (mapping != null) {
                for (String value : mapping.value()) {
                    if ("/publish/precheckV2".equals(value)) {
                        method = candidate;
                    }
                }
            }
        }
        if (method == null) {
            throw new AssertionError("Expected SigmaPublishController to expose /publish/precheckV2");
        }
    }

    private static Object createService(String baseUrl, PublishPrecheckService precheckService) throws Exception {
        Class<?> serviceClass = Class.forName(
                "com.infopublish.client.service.impl.ContentPublishV2ServiceImpl");
        Object service = serviceClass.getDeclaredConstructor().newInstance();
        setField(service, "publishPrecheckV2Service", createPrecheckV2Service(precheckService));
        setField(service, "restTemplate", new RestTemplate());
        setField(service, "gatewayUrl", baseUrl);
        setField(service, "fileDownloadBaseUrl", baseUrl);
        setField(service, "maxFileSizeMb", 1L);
        setField(service, "tempFileTtlMs", 60000L);
        setField(service, "tempFileService", createTempFileService());
        return service;
    }

    private static Object createPrecheckV2Service(PublishPrecheckService precheckService) throws Exception {
        Class<?> v2ServiceClass = Class.forName("com.infopublish.client.service.PublishPrecheckV2Service");
        return Proxy.newProxyInstance(v2ServiceClass.getClassLoader(), new Class<?>[]{v2ServiceClass},
                (proxy, method, args) -> {
                    if ("precheckV2".equals(method.getName())) {
                        return precheckService.precheck((PrecheckRequest) args[0]);
                    }
                    if ("toString".equals(method.getName())) {
                        return "test-precheck-v2";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Object createTempFileService() throws Exception {
        Class<?> serviceClass = Class.forName(
                "com.infopublish.client.service.impl.PublishTempFileService");
        Object service = serviceClass.getDeclaredConstructor().newInstance();
        setField(service, "storageRoot", System.getProperty("java.io.tmpdir")
                + File.separator + "info-publish-client-v2-test");
        return service;
    }

    private static Object invokePublish(Object service, String baseJson, String playlistJson,
                                        MultipartFile[] files) throws Exception {
        Method method = service.getClass().getMethod("publish", String.class, String.class, MultipartFile[].class);
        return method.invoke(service, baseJson, playlistJson, files);
    }

    private static String baseJson(String requestId, String command) {
        return "{"
                + "\"requestId\":\"" + requestId + "\","
                + "\"operatorId\":\"DESKTOP-ABC123\","
                + "\"target\":{\"ip\":\"192.168.113.88\",\"port\":\"9520\","
                + "\"deviceId\":\"54-B5-6C-05-5E-66\",\"vendorHint\":\"JET_FILE_II\"},"
                + "\"command\":\"" + command + "\""
                + "}";
    }

    private static String playlistJson(String fileHash) {
        return "{\"items\":[{\"orderNo\":1,\"fileName\":\"B002.jpg\","
                + "\"fileType\":\"image\",\"durationSeconds\":10,\"fileHash\":\""
                + fileHash + "\"}]}";
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(bytes);
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

    private static class MemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        MemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            throw new IOException("not used");
        }
    }
}
