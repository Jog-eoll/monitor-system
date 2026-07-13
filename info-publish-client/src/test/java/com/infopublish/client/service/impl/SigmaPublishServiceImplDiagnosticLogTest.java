package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.log.DiagnosticLogReport;
import com.infopublish.client.log.DiagnosticLogReporter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SigmaPublishServiceImplDiagnosticLogTest {

    public static void main(String[] args) throws Exception {
        SigmaPublishServiceImplDiagnosticLogTest test =
                new SigmaPublishServiceImplDiagnosticLogTest();
        test.reportPermitSignedUsesPlaylistIdAsContentId();
    }

    public void reportPermitSignedUsesPlaylistIdAsContentId() throws Exception {
        CapturingDiagnosticLogReporter reporter = new CapturingDiagnosticLogReporter();
        SigmaPublishServiceImpl service = new SigmaPublishServiceImpl();
        setField(service, "diagnosticLogReporter", reporter);
        setField(service, "permitIssuer", "info-publish-client");
        setField(service, "expireSeconds", 3600);

        SigmaVerifyRequest request = new SigmaVerifyRequest();
        request.setRequestId("REQ-PERMIT-1");
        request.setPlaylistId("157");
        request.setOperatorId("sagooiot-platform");
        SigmaVerifyRequest.TargetRef target = new SigmaVerifyRequest.TargetRef();
        target.setDeviceId("00-1D-6F-03-95-CC");
        target.setIp("192.168.113.88");
        target.setPort(9520);
        target.setVendorHint("JETFILEII");
        request.setTarget(target);

        Method method = SigmaPublishServiceImpl.class.getDeclaredMethod("reportPermitSigned",
                SigmaVerifyRequest.class, String.class, String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, request, "jti-1", "digest-157",
                "sagooiot-platform", "00-1D-6F-03-95-CC", null);

        DiagnosticLogReport report = reporter.report;
        assertEquals("CLIENT_PUBLISH_PERMIT_SIGNED", report.getEventType(), "eventType");
        assertEquals("REQ-PERMIT-1", report.getTraceId(), "traceId");
        assertEquals("157", report.getContentId(), "contentId");
        assertEquals("client", report.getStage(), "stage");
        assertEquals("success", report.getSignStatus(), "signStatus");
        assertEquals("192.168.113.88", report.getBoardIp(), "boardIp");
        assertEquals(Integer.valueOf(9520), report.getBoardPort(), "boardPort");
        JSONObject detail = JSON.parseObject(report.getDetailJson());
        assertEquals("157", detail.getString("playlistId"), "detail.playlistId");
    }

    private static void assertEquals(Object expected, Object actual, String field) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(field + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class CapturingDiagnosticLogReporter extends DiagnosticLogReporter {
        private DiagnosticLogReport report;

        @Override
        public void reportAsync(DiagnosticLogReport report) {
            this.report = report;
        }
    }
}
