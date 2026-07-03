package com.publishgateway.udpproxy.entity.dto.secure;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ACK 解析测试 —— 覆盖所有状态码和边界情况
 */
public class SecureGatewayAckParserTest {

    @Test
    public void testAccepted() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"ACCEPTED\",\"code\":1000,\"message\":\"已接受\",\"requestId\":\"REQ-001\",\"orchestrationTaskId\":\"ORCH-001\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertTrue(ack.getAccepted());
        assertEquals("ACCEPTED", ack.getStatus());
        assertEquals("REQ-001", ack.getRequestId());
        assertEquals("ORCH-001", ack.getOrchestrationTaskId());
        assertFalse(ack.isTerminalFailure());
    }

    @Test
    public void testDuplicateRequestAccepted() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"DUPLICATE_REQUEST\",\"code\":1001,\"message\":\"幂等回放\",\"requestId\":\"REQ-001\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertTrue(ack.getAccepted());
        assertEquals("DUPLICATE_REQUEST", ack.getStatus());
        assertTrue(ack.isDuplicateAccepted());
        assertFalse(ack.isTerminalFailure());
    }

    @Test
    public void testDecryptFailed() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":false,\"status\":\"DECRYPT_FAILED\",\"code\":4001,\"message\":\"解密失败\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertEquals("DECRYPT_FAILED", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testBadJson() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":false,\"status\":\"BAD_JSON\",\"code\":4002,\"message\":\"JSON格式错误\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertEquals("BAD_JSON", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testTargetNotFound() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":false,\"status\":\"TARGET_NOT_FOUND\",\"code\":4006,\"message\":\"目标设备不存在\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertEquals("TARGET_NOT_FOUND", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testTimeout() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":false,\"status\":\"TIMEOUT\",\"code\":5002,\"message\":\"执行超时\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertEquals("TIMEOUT", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testTimeoutWithAcceptedTrueStillFailure() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"TIMEOUT\",\"code\":5002,\"message\":\"执行超时\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertTrue(ack.getAccepted());
        assertEquals("TIMEOUT", ack.getStatus());
        assertTrue("TIMEOUT with accepted=true should still be failure", ack.isTerminalFailure());
    }

    @Test
    public void testPartialSuccess() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"PARTIAL_SUCCESS\",\"code\":5001,\"message\":\"部分步骤成功\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertEquals("PARTIAL_SUCCESS", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testStepsWithStepField() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"ACCEPTED\",\"steps\":[{\"step\":\"FILE_UPLOAD\",\"fileOrderNo\":1,\"fileName\":\"001.jpg\",\"status\":\"SUCCESS\",\"message\":\"ok\"}]}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertNotNull(ack.getSteps());
        assertEquals(1, ack.getSteps().size());
        assertEquals("FILE_UPLOAD", ack.getSteps().get(0).getStep());
        assertEquals("FILE_UPLOAD", ack.getSteps().get(0).getStepName());
    }

    @Test
    public void testStepsWithStepNameField() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"ACCEPTED\",\"steps\":[{\"stepName\":\"FILE_UPLOAD\",\"status\":\"SUCCESS\",\"message\":\"ok\"}]}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertNotNull(ack.getSteps());
        assertEquals(1, ack.getSteps().size());
        assertEquals("FILE_UPLOAD", ack.getSteps().get(0).getStep());
        assertEquals("FILE_UPLOAD", ack.getSteps().get(0).getStepName());
    }

    @Test
    public void testCodeNumeric() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"ACCEPTED\",\"code\":1000}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertEquals("1000", ack.getCode());
    }

    @Test
    public void testCodeString() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"ACCEPTED\",\"code\":\"E1000\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertEquals("E1000", ack.getCode());
    }

    @Test
    public void testUnknownStatus() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":false,\"status\":\"WEIRD_STATUS\",\"message\":\"strange\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertEquals("WEIRD_STATUS", ack.getStatus());
        // accepted=false 应判为失败
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testUnknownStatusWithAcceptedTrueStillFailure() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"WEIRD_STATUS\",\"message\":\"strange\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertEquals("WEIRD_STATUS", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testAcceptedFalseAlwaysFailure() {
        // accepted=false 即使 status 缺失也应失败
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":false}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testEmptyResponseBody() {
        SecureGatewayAck ack = SecureGatewayAckParser.parse("");
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertEquals("ERROR", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testNullResponseBody() {
        SecureGatewayAck ack = SecureGatewayAckParser.parse(null);
        assertNotNull(ack);
        assertFalse(ack.getAccepted());
        assertEquals("ERROR", ack.getStatus());
        assertTrue(ack.isTerminalFailure());
    }

    @Test
    public void testPreservesMappedCapabilityAndTaskIds() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":{\"accepted\":true,\"status\":\"ACCEPTED\",\"code\":1000,\"requestId\":\"REQ-001\",\"taskId\":\"T-001\",\"batchTaskId\":\"BATCH-001\",\"orchestrationTaskId\":\"ORCH-001\",\"mappedCapability\":\"BRIGHTNESS_SET\"}}";
        SecureGatewayAck ack = SecureGatewayAckParser.parse(body);
        assertNotNull(ack);
        assertEquals("REQ-001", ack.getRequestId());
        assertEquals("T-001", ack.getTaskId());
        assertEquals("BATCH-001", ack.getBatchTaskId());
        assertEquals("ORCH-001", ack.getOrchestrationTaskId());
        assertEquals("BRIGHTNESS_SET", ack.getMappedCapability());
    }
}
