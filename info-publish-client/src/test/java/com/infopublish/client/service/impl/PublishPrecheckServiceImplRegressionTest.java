package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig;
import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyResponse;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.GatewayService;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.SigmaApiClient;
import com.infopublish.client.service.SigmaPublishService;
import com.infopublish.client.service.UkeyLifecycleManager;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class PublishPrecheckServiceImplRegressionTest {

    public static void main(String[] args) throws Exception {
        PublishPrecheckServiceImplRegressionTest test = new PublishPrecheckServiceImplRegressionTest();
        test.v1VideoWithoutDurationDefaultsToThirtySecondsBeforePermit();
    }

    public void v1VideoWithoutDurationDefaultsToThirtySecondsBeforePermit() throws Exception {
        final AtomicReference<SigmaVerifyRequest> permitRequest = new AtomicReference<>();

        PublishPrecheckServiceImpl service = new PublishPrecheckServiceImpl();
        setField(service, "precheckEnabled", true);
        setField(service, "ukeyLifecycleManager", proxy(UkeyLifecycleManager.class,
                "isAuthenticated", Boolean.TRUE));
        setField(service, "clientAuthService", proxy(ClientAuthService.class,
                "isAuthenticated", Boolean.TRUE));
        setField(service, "processBindService", proxy(ProcessBindService.class,
                "getAuthorizedPid", Long.valueOf(1234L)));
        AppConfig.ProcessBindProperties processBindProperties = new AppConfig.ProcessBindProperties();
        processBindProperties.setEnabled(false);
        setField(service, "processBindProperties", processBindProperties);
        setField(service, "gatewayService", proxy(GatewayService.class,
                "getChannelStatus", statusReady()));
        setField(service, "sigmaApiClient", videoProgramClient());
        setField(service, "sigmaPublishService", new SigmaPublishService() {
            @Override
            public com.infopublish.client.entity.dto.sigma.SigmaPublishStatusResponse getStatus() {
                return null;
            }

            @Override
            public SigmaVerifyResponse verify(SigmaVerifyRequest request) {
                return issuePermit(request);
            }

            @Override
            public SigmaVerifyResponse issuePermit(SigmaVerifyRequest request) {
                permitRequest.set(request);
                return SigmaVerifyResponse.pass("permit-v1", "digest-v1",
                        request.getRequestId(), request.getPrecheckId());
            }
        });

        PrecheckResponse response = service.precheck(precheckRequest());
        if (!response.isPublishAllowed()) {
            throw new AssertionError("Expected V1 precheck to allow video without duration but got: "
                    + response.getMessage());
        }
        SigmaVerifyRequest captured = permitRequest.get();
        if (captured == null || captured.getItems() == null || captured.getItems().isEmpty()) {
            throw new AssertionError("Expected publishPermit request to include normalized item");
        }
        Integer durationSeconds = captured.getItems().get(0).getDurationSeconds();
        if (!Integer.valueOf(30).equals(durationSeconds)) {
            throw new AssertionError("Expected V1 video durationSeconds=30 before permit but was "
                    + durationSeconds);
        }
    }

    private static PrecheckRequest precheckRequest() {
        PrecheckRequest request = new PrecheckRequest();
        request.setRequestId("REQ-PRECHECK-VIDEO");
        request.setSigmaBaseUrl("http://sigma.local");
        request.setTimeoutMs(3000);
        SigmaVerifyRequest.TargetRef target = new SigmaVerifyRequest.TargetRef();
        target.setIp("192.168.113.88");
        target.setPort(9520);
        target.setVendorHint("JETFILEII");
        request.setTarget(target);
        return request;
    }

    private static SigmaApiClient videoProgramClient() {
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
                item.setFileUrl("http://sigma.local/files/qingsong.mp4");
                item.setFileHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
                program.setItems(Collections.singletonList(item));
                return program;
            }

            @Override
            public byte[] downloadFileContent(String sigmaBaseUrl, String playlistId, String innerFileId) {
                throw new UnsupportedOperationException("not used");
            }
        };
    }

    private static Map<String, Object> statusReady() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", 1);
        return status;
    }

    private static Object proxy(Class<?> type, String methodName, Object result) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (methodName.equals(method.getName())) {
                return result;
            }
            if ("toString".equals(method.getName())) {
                return "test-" + type.getSimpleName();
            }
            Class<?> returnType = method.getReturnType();
            if (Boolean.TYPE.equals(returnType)) {
                return Boolean.FALSE;
            }
            if (Long.TYPE.equals(returnType)) {
                return Long.valueOf(0L);
            }
            if (Integer.TYPE.equals(returnType)) {
                return Integer.valueOf(0);
            }
            return null;
        });
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
