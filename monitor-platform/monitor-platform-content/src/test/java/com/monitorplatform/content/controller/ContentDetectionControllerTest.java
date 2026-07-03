package com.monitorplatform.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import com.monitorplatform.content.service.LocalAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentDetectionControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void preAuditVideoCallsLocalVideoAuditAndKeepsResponseContract() throws Exception {
        LocalAuditService localAuditService = mock(LocalAuditService.class);
        QwenDetectionResultDTO auditResult = new QwenDetectionResultDTO();
        auditResult.setDetectionResult("compliant");
        auditResult.setViolationType("none");
        auditResult.setViolationLevel("无");
        auditResult.setReason("该发布内容合规，继续传输");
        when(localAuditService.auditVideoFile(any(MultipartFile.class), eq("audit-video-1"), eq("client-1")))
                .thenReturn(auditResult);

        ContentDetectionController controller = newController(localAuditService);
        MockMultipartFile video = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", new byte[]{0x01, 0x02});
        String metadata = "{\"auditId\":\"audit-video-1\",\"clientId\":\"client-1\",\"contentType\":\"video\",\"fileName\":\"demo.mp4\"}";

        Object responseObject = controller.preAudit(metadata, video);
        Result<?> response = (Result<?>) responseObject;
        Map<?, ?> data = OBJECT_MAPPER.convertValue(response.getData(), Map.class);

        assertEquals("video", data.get("contentType"));
        assertEquals("PASS", data.get("decision"));
        assertEquals("MODEL_APPROVED", data.get("reason"));
        verify(localAuditService).auditVideoFile(any(MultipartFile.class), eq("audit-video-1"), eq("client-1"));
    }

    @Test
    void preAuditVideoWithoutFileReturnsManualDecision() throws Exception {
        LocalAuditService localAuditService = mock(LocalAuditService.class);
        ContentDetectionController controller = newController(localAuditService);
        String metadata = "{\"auditId\":\"audit-video-2\",\"clientId\":\"client-1\",\"contentType\":\"video\",\"fileName\":\"demo.mp4\"}";

        Object responseObject = controller.preAudit(metadata, null);
        Result<?> response = (Result<?>) responseObject;
        Map<?, ?> data = OBJECT_MAPPER.convertValue(response.getData(), Map.class);

        assertEquals("video", data.get("contentType"));
        assertEquals("NEED_MANUAL", data.get("decision"));
        assertEquals("VIDEO_FILE_EMPTY", data.get("reason"));
        verify(localAuditService, never()).auditVideoFile(any(MultipartFile.class), anyString(), anyString());
    }

    private ContentDetectionController newController(LocalAuditService localAuditService) {
        ContentDetectionController controller = new ContentDetectionController();
        ReflectionTestUtils.setField(controller, "localAuditService", localAuditService);
        ReflectionTestUtils.setField(controller, "preAuditEnabled", true);
        ReflectionTestUtils.setField(controller, "preAuditMaxFileSizeMb", 500);
        ReflectionTestUtils.setField(controller, "preAuditSyncTimeoutMs", 120000L);
        return controller;
    }
}
