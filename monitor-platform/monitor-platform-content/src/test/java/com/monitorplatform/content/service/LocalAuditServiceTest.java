package com.monitorplatform.content.service;

import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAuditServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void auditVideoFilePostsVideoMultipartAndMapsPass() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(readBody(exchange));
            writeJson(exchange, "{\"code\":0,\"data\":{\"audit_result\":\"通过\",\"violation_level\":\"无\",\"violations\":[]}}");
        });

        LocalAuditService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", new byte[]{0x01, 0x02, 0x03});

        QwenDetectionResultDTO result = service.auditVideoFile(file, "biz-1", "device-1");

        assertEquals("compliant", result.getDetectionResult());
        assertEquals("none", result.getViolationType());
        assertMultipartContainsVideoParams(requestBody.get());
    }

    @Test
    void auditVideoDownloadsMinioVideoAndPostsVideoMultipart() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/monitor-content/videos/demo.mp4", exchange -> {
            byte[] bytes = new byte[]{0x10, 0x11, 0x12};
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/audit/video", exchange -> {
            requestBody.set(readBody(exchange));
            writeJson(exchange, "{\"code\":0,\"data\":{\"audit_result\":\"阻断\",\"violation_level\":\"高\",\"violations\":[{\"type\":\"暴力\",\"confidence\":0.98,\"violation_reason\":\"测试违规\"}]}}");
        });
        server.start();

        LocalAuditService service = newService();
        ReflectionTestUtils.setField(service, "minioEndpoint", baseUrl());
        ReflectionTestUtils.setField(service, "minioBucketName", "monitor-content");

        QwenDetectionRequestDTO request = new QwenDetectionRequestDTO();
        request.setBusinessId("biz-2");
        request.setDeviceId("device-2");
        request.setMinioPath("videos/demo.mp4");
        request.setContentType("video");

        QwenDetectionResultDTO result = service.auditVideo(request);

        assertEquals("violation", result.getDetectionResult());
        assertEquals("violence", result.getViolationType());
        assertEquals(Integer.valueOf(98), result.getConfidence());
        assertMultipartContainsVideoParams(requestBody.get());
    }

    @Test
    void auditImageFileMapsEnglishBlockResponseToExistingViolationContract() throws Exception {
        startImageServer(exchange -> writeJson(exchange,
                "{\"code\":0,\"data\":{\"audit_result\":\"block\",\"violation_level\":\"high\","
                        + "\"violations\":[{\"type\":\"object\",\"class_name\":\"knife\","
                        + "\"confidence\":0.85,\"violation_level\":\"high\","
                        + "\"violation_reason\":\"疑似管制刀具\"}]}}"));

        LocalAuditService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.jpg", "image/jpeg", new byte[]{0x01, 0x02});

        QwenDetectionResultDTO result = service.auditImageFile(file, "biz-english-block", "device-1");

        assertEquals("violation", result.getDetectionResult());
        assertEquals("阻断", result.getAuditResult());
        assertEquals("高", result.getViolationLevel());
        assertEquals("violence", result.getViolationType());
        assertEquals(Integer.valueOf(85), result.getConfidence());
        assertEquals("疑似管制刀具", result.getReason());
    }

    @Test
    void auditImageFileAddsBearerAuthorizationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        startImageServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, "{\"code\":0,\"data\":{\"audit_result\":\"pass\",\"violation_level\":\"none\",\"violations\":[]}}");
        });

        LocalAuditService service = newService();
        ReflectionTestUtils.setField(service, "authMode", "bearer");
        ReflectionTestUtils.setField(service, "authToken", "local-token");
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.jpg", "image/jpeg", new byte[]{0x01, 0x02});

        QwenDetectionResultDTO result = service.auditImageFile(file, "biz-bearer", "device-1");

        assertEquals("compliant", result.getDetectionResult());
        assertEquals("Bearer local-token", authorization.get());
    }

    @Test
    void auditImageFileLogsInWithCertificateAndSendsSessionCookie() throws Exception {
        AtomicReference<String> loginBody = new AtomicReference<>();
        AtomicReference<String> cookie = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/auth/cert-login", exchange -> {
            loginBody.set(readUtf8Body(exchange));
            byte[] bytes = "{\"code\":0,\"data\":{\"username\":\"admin\",\"role\":\"admin\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=abc123; Path=/; HttpOnly");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/audit/image/quick", exchange -> {
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            writeJson(exchange, "{\"code\":0,\"data\":{\"audit_result\":\"pass\",\"violation_level\":\"none\",\"violations\":[]}}");
        });
        server.start();

        LocalAuditService service = newService();
        ReflectionTestUtils.setField(service, "authMode", "cert-session");
        ReflectionTestUtils.setField(service, "authUrl", baseUrl() + "/api/auth/cert-login");
        ReflectionTestUtils.setField(service, "authUsername", "admin");
        ReflectionTestUtils.setField(service, "authPassword", "secret");
        ReflectionTestUtils.setField(service, "authCertContent", "-----BEGIN SM2 CERTIFICATE-----\\n04abcd\\n-----END SM2 CERTIFICATE-----");
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.jpg", "image/jpeg", new byte[]{0x01, 0x02});

        QwenDetectionResultDTO result = service.auditImageFile(file, "biz-cert", "device-1");

        assertEquals("compliant", result.getDetectionResult());
        assertTrue(loginBody.get().contains("\"username\":\"admin\""), loginBody.get());
        assertTrue(loginBody.get().contains("\"cert_content\""), loginBody.get());
        assertEquals("SESSION=abc123", cookie.get());
    }

    private LocalAuditService newService() {
        LocalAuditService service = new LocalAuditService();
        ReflectionTestUtils.setField(service, "imageUrl", baseUrl() + "/api/audit/image/quick");
        ReflectionTestUtils.setField(service, "textUrl", baseUrl() + "/api/audit/text");
        ReflectionTestUtils.setField(service, "videoUrl", baseUrl() + "/api/audit/video");
        ReflectionTestUtils.setField(service, "connectTimeout", 2000);
        ReflectionTestUtils.setField(service, "readTimeout", 5000);
        ReflectionTestUtils.setField(service, "maxRetry", 1);
        ReflectionTestUtils.setField(service, "retryInterval", 10);
        ReflectionTestUtils.setField(service, "videoFrameInterval", 0.5D);
        ReflectionTestUtils.setField(service, "videoMaxFrames", 200);
        ReflectionTestUtils.setField(service, "videoSaveFrames", false);
        return service;
    }

    private void startServer(ExchangeHandler videoHandler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/audit/video", exchange -> videoHandler.handle(exchange));
        server.start();
    }

    private void startImageServer(ExchangeHandler imageHandler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/audit/image/quick", exchange -> imageHandler.handle(exchange));
        server.start();
    }

    private String baseUrl() {
        assertNotNull(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void assertMultipartContainsVideoParams(String body) {
        assertNotNull(body);
        assertTrue(body.contains("name=\"video\""), body);
        assertTrue(body.contains("name=\"frame_interval\""), body);
        assertTrue(body.contains("0.5"), body);
        assertTrue(body.contains("name=\"max_frames\""), body);
        assertTrue(body.contains("200"), body);
        assertTrue(body.contains("name=\"save_frames\""), body);
        assertTrue(body.contains("false"), body);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = exchange.getRequestBody().read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        byte[] bytes = buffer.toByteArray();
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private String readUtf8Body(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = exchange.getRequestBody().read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
