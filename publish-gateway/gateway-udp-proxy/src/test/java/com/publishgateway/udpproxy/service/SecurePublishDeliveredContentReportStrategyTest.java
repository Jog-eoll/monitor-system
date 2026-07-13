package com.publishgateway.udpproxy.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.log.DiagnosticLogReport;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SecurePublishDeliveredContentReportStrategyTest {

    @Test
    public void reportDeliveredPostsImageAndVideoDetectionRecords() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<String> requestBodies = Collections.synchronizedList(new ArrayList<String>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/content/detection/detect", exchange -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = exchange.getRequestBody().read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            requestBodies.add(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":200,\"message\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            OutputStream out = exchange.getResponseBody();
            try {
                out.write(response);
            } finally {
                out.close();
            }
            latch.countDown();
        });
        server.start();
        try {
            SecurePublishContentReportStrategy strategy = new SecurePublishContentReportStrategy();
            setField(strategy, "contentServiceUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            setField(strategy, "minioUploadService", new FixedMinioUploadService());
            setField(strategy, "diagnosticLogReporter", new CapturingDiagnosticLogReporter());

            SecureDeliveryTaskRequest request = request();
            List<SecurePublishDeliveredFile> files = Arrays.asList(
                    new SecurePublishDeliveredFile(request.getFiles().get(0),
                            "image-bytes".getBytes(StandardCharsets.UTF_8), "actual-image-hash"),
                    new SecurePublishDeliveredFile(request.getFiles().get(1),
                            "video-bytes".getBytes(StandardCharsets.UTF_8), "actual-video-hash"));

            strategy.reportDelivered("DLV-001", request, files);

            assertTrue("expected both media detection calls", latch.await(3, TimeUnit.SECONDS));
            assertEquals(2, requestBodies.size());
            JSONObject image = findByType(requestBodies, "image");
            JSONObject video = findByType(requestBodies, "video");
            assertNotNull(image);
            assertNotNull(video);

            assertEquals("REQ-PUBLISH-20260709-157", image.getString("publishRequestId"));
            assertEquals("157", image.getString("playBatchId"));
            assertEquals(Integer.valueOf(1), image.getInteger("playBatchSeq"));
            assertEquals(Integer.valueOf(2), image.getInteger("playBatchSize"));
            assertEquals("192.168.113.88", image.getString("boardIp"));
            assertEquals(Integer.valueOf(9520), image.getInteger("boardPort"));
            assertEquals("board.png", image.getString("fileName"));
            assertEquals("images/secure/board.png", image.getString("minioPath"));

            assertEquals("REQ-PUBLISH-20260709-157", video.getString("publishRequestId"));
            assertEquals("157", video.getString("playBatchId"));
            assertEquals(Integer.valueOf(2), video.getInteger("playBatchSeq"));
            assertEquals(Integer.valueOf(2), video.getInteger("playBatchSize"));
            assertEquals("clip.mp4", video.getString("fileName"));
            assertEquals("videos/secure/clip.mp4", video.getString("minioPath"));

            JSONObject imageDetail = JSON.parseObject(image.getString("data"));
            assertEquals("DLV-001", imageDetail.getString("deliveryTaskId"));
            assertEquals("hash-image", imageDetail.getString("fileHash"));
            assertEquals("actual-image-hash", imageDetail.getString("actualHash"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void reportDeliveredRecordsFailureWhenMediaUploadFails() throws Exception {
        SecurePublishContentReportStrategy strategy = new SecurePublishContentReportStrategy();
        setField(strategy, "contentServiceUrl", "http://127.0.0.1:9");
        setField(strategy, "minioUploadService", new FailingMinioUploadService());
        CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
        setField(strategy, "diagnosticLogReporter", reporter);

        SecureDeliveryTaskRequest request = request();
        List<SecurePublishDeliveredFile> files = Collections.singletonList(
                new SecurePublishDeliveredFile(request.getFiles().get(1),
                        "video-bytes".getBytes(StandardCharsets.UTF_8), "actual-video-hash"));

        strategy.reportDelivered("DLV-001", request, files);

        DiagnosticLogReport report = reporter.await("SECURE_PUBLISH_MEDIA_CONTENT_REPORT_FAILED");
        assertNotNull(report);
        assertEquals("REQ-PUBLISH-20260709-157", report.getTraceId());
        assertEquals("MINIO_UPLOAD_FAILED", report.getErrorCode());
        assertEquals("fail", report.getResultStatus());
    }

    private static JSONObject findByType(List<String> bodies, String contentType) {
        for (String body : bodies) {
            JSONObject object = JSON.parseObject(body);
            if (contentType.equals(object.getString("contentType"))) {
                return object;
            }
        }
        return null;
    }

    private static SecureDeliveryTaskRequest request() {
        SecureDeliveryTaskRequest request = new SecureDeliveryTaskRequest();
        request.setRequestId("REQ-PUBLISH-20260709-157");
        request.setSigmaPublishId("sigma-157");

        SecureDeliveryTaskRequest.TargetRef target = new SecureDeliveryTaskRequest.TargetRef();
        target.setDeviceId("00-1D-6F-03-95-CC");
        target.setIp("192.168.113.88");
        target.setPort(9520);
        target.setVendorHint("JETFILEII");
        request.setTarget(target);

        SecureDeliveryTaskRequest.PlaylistInfo playlist = new SecureDeliveryTaskRequest.PlaylistInfo();
        playlist.setPlaylistId("157");
        playlist.setDigest("digest-157");
        request.setPlaylist(playlist);

        request.setFiles(new ArrayList<SecureDeliveryTaskRequest.FileRef>());
        request.getFiles().add(file(1, "board.png", "image", "hash-image"));
        request.getFiles().add(file(2, "clip.mp4", "video", "hash-video"));
        return request;
    }

    private static SecureDeliveryTaskRequest.FileRef file(int orderNo, String fileName,
                                                          String fileType, String hash) {
        SecureDeliveryTaskRequest.FileRef file = new SecureDeliveryTaskRequest.FileRef();
        file.setOrderNo(orderNo);
        file.setFileName(fileName);
        file.setFileType(fileType);
        file.setFileUrl("http://127.0.0.1/files/" + fileName);
        file.setDurationSeconds(5);
        file.setFileHash(hash);
        return file;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FixedMinioUploadService extends MinioUploadService {
        @Override
        public String uploadImage(byte[] data, String fileName, String format) {
            return "images/secure/" + fileName;
        }

        @Override
        public String uploadVideo(byte[] data, String fileName) {
            return "videos/secure/" + fileName;
        }
    }

    private static class FailingMinioUploadService extends MinioUploadService {
        @Override
        public String uploadImage(byte[] data, String fileName, String format) {
            return null;
        }

        @Override
        public String uploadVideo(byte[] data, String fileName) {
            return null;
        }
    }

    private static class CapturingDiagnosticLogReporter extends DiagnosticLogReporter {
        private final List<DiagnosticLogReport> reports =
                Collections.synchronizedList(new ArrayList<DiagnosticLogReport>());

        @Override
        public void reportAsync(DiagnosticLogReport report) {
            reports.add(report);
        }

        private DiagnosticLogReport await(String eventType) throws Exception {
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                for (DiagnosticLogReport report : reports) {
                    if (eventType.equals(report.getEventType())) {
                        return report;
                    }
                }
                Thread.sleep(20L);
            }
            return null;
        }
    }
}
