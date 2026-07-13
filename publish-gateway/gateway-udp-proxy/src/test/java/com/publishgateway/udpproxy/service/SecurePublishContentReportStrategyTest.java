package com.publishgateway.udpproxy.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SecurePublishContentReportStrategyTest {

    @Test
    public void reportAcceptedWritesPlaylistContentWithPublishRequestId() throws Exception {
        final List<String> requestBodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/content/receive", exchange -> {
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
        });
        server.start();
        try {
            CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
            SecurePublishContentReportStrategy strategy = new SecurePublishContentReportStrategy();
            setField(strategy, "contentServiceUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            setField(strategy, "diagnosticLogReporter", reporter);

            strategy.reportAccepted(request());

            assertEquals(1, requestBodies.size());
            JSONObject body = JSON.parseObject(requestBodies.get(0));
            assertEquals("157", body.getString("businessId"));
            assertEquals("157", body.getString("contentId"));
            assertEquals("REQ-PUBLISH-20260709-157", body.getString("publishRequestId"));
            assertEquals("SECURE_PUBLISH", body.getString("protocol"));
            assertEquals("playlist", body.getString("contentType"));
            assertEquals("157", body.getString("playBatchId"));
            assertEquals(Integer.valueOf(0), body.getInteger("playBatchSeq"));
            assertEquals(Integer.valueOf(2), body.getInteger("playBatchSize"));
            assertEquals("192.168.113.88", body.getString("boardIp"));
            assertEquals(Integer.valueOf(9520), body.getInteger("boardPort"));
            JSONArray files = JSON.parseObject(body.getString("data")).getJSONArray("files");
            assertEquals(2, files.size());

            DiagnosticLogReport report = reporter.find("SECURE_PUBLISH_CONTENT_REPORTED");
            assertNotNull(report);
            assertEquals("REQ-PUBLISH-20260709-157", report.getTraceId());
            assertEquals("157", report.getContentId());
            assertEquals("success", report.getResultStatus());
            assertEquals("192.168.113.88", report.getBoardIp());
            assertEquals(Integer.valueOf(9520), report.getBoardPort());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void reportAcceptedTreatsBusinessFailureResponseAsFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/content/receive", exchange -> {
            byte[] chunk = new byte[1024];
            while (exchange.getRequestBody().read(chunk) > 0) {
                // drain request body
            }
            byte[] response = "{\"success\":false,\"message\":\"duplicate contentId\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            OutputStream out = exchange.getResponseBody();
            try {
                out.write(response);
            } finally {
                out.close();
            }
        });
        server.start();
        try {
            CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
            SecurePublishContentReportStrategy strategy = new SecurePublishContentReportStrategy();
            setField(strategy, "contentServiceUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            setField(strategy, "diagnosticLogReporter", reporter);

            strategy.reportAccepted(request());

            DiagnosticLogReport report = reporter.find("SECURE_PUBLISH_CONTENT_REPORT_FAILED");
            assertNotNull(report);
            assertEquals("fail", report.getResultStatus());
            assertEquals("CONTENT_RECEIVE_BUSINESS_FAILED", report.getErrorCode());
            assertEquals("duplicate contentId", report.getErrorMessage());
        } finally {
            server.stop(0);
        }
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

        SecureDeliveryTaskRequest.FileRef first = file(1, "first.bmp", "hash-1");
        SecureDeliveryTaskRequest.FileRef second = file(2, "second.bmp", "hash-2");
        request.setFiles(new ArrayList<SecureDeliveryTaskRequest.FileRef>());
        request.getFiles().add(first);
        request.getFiles().add(second);
        return request;
    }

    private static SecureDeliveryTaskRequest.FileRef file(int orderNo, String fileName, String hash) {
        SecureDeliveryTaskRequest.FileRef file = new SecureDeliveryTaskRequest.FileRef();
        file.setOrderNo(orderNo);
        file.setFileName(fileName);
        file.setFileType("image");
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

    private static class CapturingDiagnosticLogReporter extends DiagnosticLogReporter {
        private final List<DiagnosticLogReport> reports =
                Collections.synchronizedList(new ArrayList<DiagnosticLogReport>());

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
}
