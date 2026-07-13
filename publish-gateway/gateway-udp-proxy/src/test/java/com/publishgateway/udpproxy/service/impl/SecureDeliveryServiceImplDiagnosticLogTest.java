package com.publishgateway.udpproxy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.config.SecureDeliveryEnvelopeProperties;
import com.publishgateway.udpproxy.entity.dto.delivery.DeliveryTaskStatus;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskResponse;
import com.publishgateway.udpproxy.entity.dto.secure.SecureTerminalClient;
import com.publishgateway.udpproxy.log.DiagnosticLogReport;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.DataReportService;
import com.publishgateway.udpproxy.service.SecurePublishDeliveredFile;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SecureDeliveryServiceImplDiagnosticLogTest {

    @Test
    public void createTaskReportsPlaylistSignatureVerifiedWithPlaylistId() throws Exception {
        CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
        SecureDeliveryServiceImpl service = newService(reporter);

        SecureDeliveryTaskRequest request = request("REQ-LOG-1", "157", "digest-157");
        request.setPublishPermit(signPermit("digest-157"));

        SecureDeliveryTaskResponse response = service.createTask(request);

        assertEquals("ACCEPTED", response.getStatus());
        DiagnosticLogReport report = reporter.find("SECURE_DELIVERY_PLAYLIST_VERIFIED");
        assertNotNull(report);
        assertEquals("REQ-LOG-1", report.getTraceId());
        assertEquals("157", report.getContentId());
        assertEquals("192.168.113.88", report.getBoardIp());
        assertEquals(Integer.valueOf(9520), report.getBoardPort());
        assertEquals("publish_gateway", report.getStage());
        assertEquals("success", report.getVerifyStatus());
        assertEquals("success", report.getResultStatus());
        assertEquals("发布清单验签通过", report.getSummary());
        assertNull(report.getErrorMessage());
    }

    @Test
    public void createTaskReportsSecurePublishContentAfterPermitVerified() throws Exception {
        CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
        CapturingDataReportService dataReportService = new CapturingDataReportService();
        SecureDeliveryServiceImpl service = newService(reporter);
        setField(service, "dataReportService", dataReportService);

        SecureDeliveryTaskRequest request = request("REQ-LOG-TRACE", "157", "digest-157");
        request.setPublishPermit(signPermit("digest-157"));

        SecureDeliveryTaskResponse response = service.createTask(request);

        assertEquals("ACCEPTED", response.getStatus());
        assertNotNull(dataReportService.reportedRequest);
        assertEquals("REQ-LOG-TRACE", dataReportService.reportedRequest.getRequestId());
        assertEquals("157", dataReportService.reportedRequest.getPlaylist().getPlaylistId());
    }

    @Test
    public void createTaskReportsPlaylistSignatureVerificationFailureWithPlaylistId() throws Exception {
        CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
        SecureDeliveryServiceImpl service = newService(reporter);

        SecureDeliveryTaskRequest request = request("REQ-LOG-2", "157", "wrong-digest");
        request.setPublishPermit(signPermit("digest-157"));

        SecureDeliveryTaskResponse response = service.createTask(request);

        assertEquals("FAILED", response.getStatus());
        DiagnosticLogReport report = reporter.find("SECURE_DELIVERY_PLAYLIST_VERIFY_FAILED");
        assertNotNull(report);
        assertEquals("REQ-LOG-2", report.getTraceId());
        assertEquals("157", report.getContentId());
        assertEquals("publish_gateway", report.getStage());
        assertEquals("fail", report.getVerifyStatus());
        assertEquals("fail", report.getResultStatus());
        assertEquals("发布清单验签失败", report.getSummary());
        assertNotNull(report.getErrorMessage());
    }

    @Test
    public void executeDeliveryReportsPlaylistItemVerifiedAndAckSummary() throws Exception {
        final byte[] fileBody = "qingsong-log-file".getBytes(StandardCharsets.UTF_8);
        final String fileHash = sha256Hex(fileBody);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files/qingsong.bin", exchange -> {
            exchange.sendResponseHeaders(200, fileBody.length);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(fileBody);
            } finally {
                output.close();
            }
        });
        server.start();
        try {
            CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
            SecureDeliveryServiceImpl service = newService(reporter);
            setField(service, "cryptoService", new PassThroughCryptoService());
            setField(service, "secureTerminalClient", new SuccessfulTerminalClient());
            setField(service, "envelopeProperties", new SecureDeliveryEnvelopeProperties());
            setField(service, "downloadTimeoutMs", 3000);
            setField(service, "maxFileSizeMb", 1);

            SecureDeliveryTaskRequest request = request("REQ-LOG-3", "157", "digest-157");
            request.getFiles().get(0).setFileUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/files/qingsong.bin");
            request.getFiles().get(0).setFileHash(fileHash);

            String taskId = "DLV-log3";
            DeliveryTaskStatus status = new DeliveryTaskStatus();
            status.setDeliveryTaskId(taskId);
            status.setStatus("ACCEPTED");
            taskStore(service).put(taskId, status);

            Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod(
                    "executeDelivery", String.class, SecureDeliveryTaskRequest.class);
            method.setAccessible(true);
            method.invoke(service, taskId, request);

            assertEquals("SUCCESS", status.getStatus());
            DiagnosticLogReport itemReport = reporter.find("SECURE_DELIVERY_PLAYLIST_ITEM_VERIFIED");
            assertNotNull(itemReport);
            assertEquals("157", itemReport.getContentId());
            assertEquals("发布清单验签通过", itemReport.getSummary());
            assertEquals("success", itemReport.getVerifyStatus());
            JSONObject itemDetail = JSON.parseObject(itemReport.getDetailJson());
            assertEquals(fileHash, itemDetail.getString("actualHash"));

            DiagnosticLogReport ackReport = reporter.find("SECURE_DELIVERY_ACK_SUMMARY");
            assertNotNull(ackReport);
            assertEquals("REQ-LOG-3", ackReport.getTraceId());
            assertEquals("157", ackReport.getContentId());
            assertEquals("解密网关内容下发成功", ackReport.getSummary());
            assertEquals("success", ackReport.getResultStatus());
            JSONObject ackDetail = JSON.parseObject(ackReport.getDetailJson());
            assertEquals("ORCH-LOG-3", ackDetail.getString("orchestrationTaskId"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void executeDeliveryReportsDeliveredMediaAfterTerminalSuccess() throws Exception {
        final byte[] fileBody = "qingsong-image-content".getBytes(StandardCharsets.UTF_8);
        final String fileHash = sha256Hex(fileBody);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files/qingsong.png", exchange -> {
            exchange.sendResponseHeaders(200, fileBody.length);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(fileBody);
            } finally {
                output.close();
            }
        });
        server.start();
        try {
            CapturingDeliveredDataReportService dataReportService =
                    new CapturingDeliveredDataReportService();
            SecureDeliveryServiceImpl service = newService(new CapturingDiagnosticLogReporter());
            setField(service, "cryptoService", new PassThroughCryptoService());
            setField(service, "secureTerminalClient", new SuccessfulTerminalClient());
            setField(service, "envelopeProperties", new SecureDeliveryEnvelopeProperties());
            setField(service, "dataReportService", dataReportService);
            setField(service, "downloadTimeoutMs", 3000);
            setField(service, "maxFileSizeMb", 1);

            SecureDeliveryTaskRequest request = request("REQ-DELIVERED-MEDIA", "157", "digest-157");
            request.getFiles().get(0).setFileName("qingsong.png");
            request.getFiles().get(0).setFileType("image");
            request.getFiles().get(0).setFileUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/files/qingsong.png");
            request.getFiles().get(0).setFileHash(fileHash);

            String taskId = "DLV-media";
            DeliveryTaskStatus status = new DeliveryTaskStatus();
            status.setDeliveryTaskId(taskId);
            status.setStatus("ACCEPTED");
            taskStore(service).put(taskId, status);

            Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod(
                    "executeDelivery", String.class, SecureDeliveryTaskRequest.class);
            method.setAccessible(true);
            method.invoke(service, taskId, request);

            assertEquals("SUCCESS", status.getStatus());
            assertEquals(taskId, dataReportService.deliveryTaskId);
            assertEquals("REQ-DELIVERED-MEDIA", dataReportService.request.getRequestId());
            assertEquals(1, dataReportService.files.size());
            assertEquals("qingsong.png", dataReportService.files.get(0).getFileRef().getFileName());
            assertArrayEquals(fileBody, dataReportService.files.get(0).getData());
            assertEquals(fileHash, dataReportService.files.get(0).getActualHash());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void executeDeliveryKeepsSuccessWhenDeliveredMediaReportFails() throws Exception {
        final byte[] fileBody = "qingsong-image-content".getBytes(StandardCharsets.UTF_8);
        final String fileHash = sha256Hex(fileBody);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files/qingsong.png", exchange -> {
            exchange.sendResponseHeaders(200, fileBody.length);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(fileBody);
            } finally {
                output.close();
            }
        });
        server.start();
        try {
            SecureDeliveryServiceImpl service = newService(new CapturingDiagnosticLogReporter());
            setField(service, "cryptoService", new PassThroughCryptoService());
            setField(service, "secureTerminalClient", new SuccessfulTerminalClient());
            setField(service, "envelopeProperties", new SecureDeliveryEnvelopeProperties());
            setField(service, "dataReportService", new ThrowingDeliveredDataReportService());
            setField(service, "downloadTimeoutMs", 3000);
            setField(service, "maxFileSizeMb", 1);

            SecureDeliveryTaskRequest request = request("REQ-DELIVERED-FAIL-OPEN", "157", "digest-157");
            request.getFiles().get(0).setFileName("qingsong.png");
            request.getFiles().get(0).setFileType("image");
            request.getFiles().get(0).setFileUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/files/qingsong.png");
            request.getFiles().get(0).setFileHash(fileHash);

            String taskId = "DLV-report-fail-open";
            DeliveryTaskStatus status = new DeliveryTaskStatus();
            status.setDeliveryTaskId(taskId);
            status.setStatus("ACCEPTED");
            taskStore(service).put(taskId, status);

            Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod(
                    "executeDelivery", String.class, SecureDeliveryTaskRequest.class);
            method.setAccessible(true);
            method.invoke(service, taskId, request);

            assertEquals("SUCCESS", status.getStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void videoDeliveryUsesFileReferenceManifestWithoutDownloadingBody() throws Exception {
        CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
        CapturingTerminalClient terminalClient = new CapturingTerminalClient();
        SecureDeliveryServiceImpl service = newService(reporter);
        setField(service, "cryptoService", new PassThroughCryptoService());
        setField(service, "secureTerminalClient", terminalClient);
        setField(service, "envelopeProperties", new SecureDeliveryEnvelopeProperties());
        setField(service, "downloadTimeoutMs", 100);
        setField(service, "maxFileSizeMb", 2048);

        SecureDeliveryTaskRequest request = request("REQ-LARGE-VIDEO", "200", "digest-video");
        SecureDeliveryTaskRequest.FileRef ref = request.getFiles().get(0);
        ref.setFileType("video");
        ref.setFileName("large-video.mp4");
        ref.setFileUrl("http://127.0.0.1:9/large-video.mp4");
        ref.setFileHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        request.setPublishPermit(signPermit("digest-video"));

        String taskId = "DLV-video";
        DeliveryTaskStatus status = new DeliveryTaskStatus();
        status.setDeliveryTaskId(taskId);
        status.setStatus("ACCEPTED");
        taskStore(service).put(taskId, status);

        Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod(
                "executeDelivery", String.class, SecureDeliveryTaskRequest.class);
        method.setAccessible(true);
        method.invoke(service, taskId, request);

        assertEquals("SUCCESS", status.getStatus());
        assertEquals(1, status.getDownloadedFiles());
        assertNotNull(terminalClient.body);
        assertFalse("video manifest must not inline Base64 payload",
                terminalClient.body.contains("contentBase64"));

        JSONObject payload = JSON.parseObject(terminalClient.body);
        JSONObject file = payload.getJSONArray("files").getJSONObject(0);
        assertEquals("FILE_REF", file.getString("transferMode"));
        assertEquals("large-video.mp4", file.getString("fileName"));
        assertEquals(ref.getFileHash(), file.getString("fileHash"));
        JSONObject fileRef = file.getJSONObject("fileRef");
        assertNotNull(fileRef);
        assertEquals(ref.getFileUrl(), fileRef.getString("url"));
        assertEquals(ref.getFileHash(), fileRef.getString("sha256"));
    }

    @Test
    public void executeDeliveryMarksFailedWhenUnexpectedThrowableEscapes() throws Exception {
        final byte[] fileBody = "small-image".getBytes(StandardCharsets.UTF_8);
        final String fileHash = sha256Hex(fileBody);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files/image.bin", exchange -> {
            exchange.sendResponseHeaders(200, fileBody.length);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(fileBody);
            } finally {
                output.close();
            }
        });
        server.start();
        try {
            SecureDeliveryServiceImpl service = newService(new CapturingDiagnosticLogReporter());
            setField(service, "cryptoService", new ThrowingCryptoService());
            setField(service, "secureTerminalClient", new SuccessfulTerminalClient());
            setField(service, "envelopeProperties", new SecureDeliveryEnvelopeProperties());
            setField(service, "downloadTimeoutMs", 3000);
            setField(service, "maxFileSizeMb", 1);

            SecureDeliveryTaskRequest request = request("REQ-THROWABLE", "201", "digest-throwable");
            request.getFiles().get(0).setFileUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/files/image.bin");
            request.getFiles().get(0).setFileHash(fileHash);
            request.setPublishPermit(signPermit("digest-throwable"));

            String taskId = "DLV-throwable";
            DeliveryTaskStatus status = new DeliveryTaskStatus();
            status.setDeliveryTaskId(taskId);
            status.setStatus("ACCEPTED");
            taskStore(service).put(taskId, status);

            Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod(
                    "executeDelivery", String.class, SecureDeliveryTaskRequest.class);
            method.setAccessible(true);
            try {
                method.invoke(service, taskId, request);
            } catch (InvocationTargetException expectedBeforeFix) {
                // The assertion below proves the worker did not leave the task stuck in RUNNING.
            }

            assertEquals("FAILED", status.getStatus());
            assertTrue(status.getMessage().contains("out-of-memory-probe"));
        } finally {
            server.stop(0);
        }
    }

    private static SecureDeliveryServiceImpl newService(CapturingDiagnosticLogReporter reporter)
            throws Exception {
        SecureDeliveryServiceImpl service = new SecureDeliveryServiceImpl();
        setField(service, "hmacSecret", "test-secret");
        setField(service, "executor", new NoOpExecutorService());
        setField(service, "largeFileRefEnabled", true);
        setFieldIfPresent(service, "diagnosticLogReporter", reporter);
        return service;
    }

    private static SecureDeliveryTaskRequest request(String requestId, String playlistId,
                                                     String digest) {
        SecureDeliveryTaskRequest request = new SecureDeliveryTaskRequest();
        request.setRequestId(requestId);
        request.setSigmaPublishId("sigma-" + requestId);

        SecureDeliveryTaskRequest.TargetRef target = new SecureDeliveryTaskRequest.TargetRef();
        target.setDeviceId("00-1D-6F-03-95-CC");
        target.setIp("192.168.113.88");
        target.setPort(9520);
        target.setVendorHint("JETFILEII");
        request.setTarget(target);

        SecureDeliveryTaskRequest.PlaylistInfo playlist =
                new SecureDeliveryTaskRequest.PlaylistInfo();
        playlist.setPlaylistId(playlistId);
        playlist.setDigest(digest);
        request.setPlaylist(playlist);

        SecureDeliveryTaskRequest.FileRef file = new SecureDeliveryTaskRequest.FileRef();
        file.setOrderNo(1);
        file.setFileName("qingsong.bin");
        file.setFileType("image");
        file.setFileUrl("http://127.0.0.1/files/qingsong.bin");
        file.setDurationSeconds(5);
        file.setFileHash("file-hash");
        request.setFiles(Collections.singletonList(file));
        return request;
    }

    private static String signPermit(String playlistDigest) throws Exception {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = base64Url("{\"playlistDigest\":\"" + playlistDigest
                + "\",\"exp\":" + exp + "}");
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static void setFieldIfPresent(Object target, String fieldName, Object value)
            throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, DeliveryTaskStatus> taskStore(
            SecureDeliveryServiceImpl service) throws Exception {
        Field field = SecureDeliveryServiceImpl.class.getDeclaredField("taskStore");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, DeliveryTaskStatus>) field.get(service);
    }

    private static class CapturingDiagnosticLogReporter extends DiagnosticLogReporter {
        private final List<DiagnosticLogReport> reports = new ArrayList<>();

        @Override
        public void reportAsync(DiagnosticLogReport report) {
            reports.add(report);
        }

        private DiagnosticLogReport find(String eventType) {
            for (DiagnosticLogReport report : reports) {
                if (eventType.equals(report.getEventType())) {
                    return report;
                }
            }
            return null;
        }
    }

    private static class CapturingDataReportService extends DataReportService {
        private SecureDeliveryTaskRequest reportedRequest;

        @Override
        public void reportSecurePublishAccepted(SecureDeliveryTaskRequest request) {
            this.reportedRequest = request;
        }
    }

    private static class CapturingDeliveredDataReportService extends DataReportService {
        private String deliveryTaskId;
        private SecureDeliveryTaskRequest request;
        private List<SecurePublishDeliveredFile> files;

        @Override
        public void reportSecurePublishDelivered(String deliveryTaskId,
                                                 SecureDeliveryTaskRequest request,
                                                 List<SecurePublishDeliveredFile> files) {
            this.deliveryTaskId = deliveryTaskId;
            this.request = request;
            this.files = files;
        }
    }

    private static class ThrowingDeliveredDataReportService extends DataReportService {
        @Override
        public void reportSecurePublishDelivered(String deliveryTaskId,
                                                 SecureDeliveryTaskRequest request,
                                                 List<SecurePublishDeliveredFile> files) {
            throw new RuntimeException("content report failed");
        }
    }

    private static class PassThroughCryptoService implements CryptoService {
        @Override
        public byte[] encrypt(byte[] data) {
            return data;
        }

        @Override
        public byte[] decrypt(byte[] data) {
            return data;
        }
    }

    private static class SuccessfulTerminalClient extends SecureTerminalClient {
        @Override
        public ResponseEntity<String> postPublish(String baseUrl, byte[] encryptedBytes,
                                                  String requestId, String deliveryTaskId) {
            String body = "{\"code\":200,\"msg\":\"OK\",\"data\":{\"accepted\":true,"
                    + "\"status\":\"SUCCESS\",\"requestId\":\"" + requestId + "\","
                    + "\"orchestrationTaskId\":\"ORCH-LOG-3\",\"steps\":["
                    + "{\"step\":\"IMAGE_UPLOAD\",\"status\":\"SUCCESS\",\"message\":\"ok\"},"
                    + "{\"step\":\"PLAYLIST_SET\",\"status\":\"SUCCESS\",\"message\":\"ok\"}]}}";
            return ResponseEntity.ok(body);
        }
    }

    private static class CapturingTerminalClient extends SecureTerminalClient {
        private String body;

        @Override
        public ResponseEntity<String> postPublish(String baseUrl, byte[] encryptedBytes,
                                                  String requestId, String deliveryTaskId) {
            this.body = new String(encryptedBytes, StandardCharsets.UTF_8);
            String response = "{\"code\":200,\"msg\":\"OK\",\"data\":{\"accepted\":true,"
                    + "\"status\":\"SUCCESS\",\"requestId\":\"" + requestId + "\","
                    + "\"orchestrationTaskId\":\"ORCH-VIDEO\"}}";
            return ResponseEntity.ok(response);
        }
    }

    private static class ThrowingCryptoService implements CryptoService {
        @Override
        public byte[] encrypt(byte[] data) {
            throw new OutOfMemoryError("out-of-memory-probe");
        }

        @Override
        public byte[] decrypt(byte[] data) {
            return data;
        }
    }

    private static class NoOpExecutorService extends AbstractExecutorService {
        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
        }
    }
}
