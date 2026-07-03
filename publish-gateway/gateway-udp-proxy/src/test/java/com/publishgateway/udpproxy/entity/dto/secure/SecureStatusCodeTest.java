package com.publishgateway.udpproxy.entity.dto.secure;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 状态码语义测试 —— 验证 TIMEOUT/PARTIAL_SUCCESS 进入失败集合、终态失败判别、幂等回放判断
 */
public class SecureStatusCodeTest {

    @Test
    public void testTimeoutIsFailure() {
        assertTrue(SecureStatusCode.TIMEOUT.isFailure());
    }

    @Test
    public void testPartialSuccessIsFailure() {
        assertTrue(SecureStatusCode.PARTIAL_SUCCESS.isFailure());
    }

    @Test
    public void testAcceptedIsNotFailure() {
        assertFalse(SecureStatusCode.ACCEPTED.isFailure());
    }

    @Test
    public void testDuplicateRequestIsNotFailure() {
        assertFalse(SecureStatusCode.DUPLICATE_REQUEST.isFailure());
    }

    @Test
    public void testTimeoutIsTerminalFailure() {
        assertFalse(SecureStatusCode.TIMEOUT.isTerminalFailure());
    }

    @Test
    public void testTimeoutIsRetryableFailure() {
        assertTrue(SecureStatusCode.TIMEOUT.isRetryableFailure());
    }

    @Test
    public void testPartialSuccessIsTerminalFailure() {
        assertTrue(SecureStatusCode.PARTIAL_SUCCESS.isTerminalFailure());
    }

    @Test
    public void testDecryptFailedIsTerminalFailure() {
        assertTrue(SecureStatusCode.DECRYPT_FAILED.isTerminalFailure());
        assertFalse(SecureStatusCode.DECRYPT_FAILED.isRetryableFailure());
    }

    @Test
    public void testAcceptedReplay() {
        assertTrue(SecureStatusCode.isAcceptedReplay(SecureStatusCode.DUPLICATE_REQUEST, true));
        assertFalse(SecureStatusCode.isAcceptedReplay(SecureStatusCode.DUPLICATE_REQUEST, false));
        assertFalse(SecureStatusCode.isAcceptedReplay(SecureStatusCode.ACCEPTED, true));
    }

    @Test
    public void testAllTerminalFailures() {
        SecureStatusCode[] terminalFailures = {
                SecureStatusCode.REJECTED,
                SecureStatusCode.DECRYPT_FAILED,
                SecureStatusCode.BAD_JSON,
                SecureStatusCode.VALIDATION_FAILED,
                SecureStatusCode.TARGET_NOT_FOUND,
                SecureStatusCode.UNSUPPORTED_CAPABILITY,
                SecureStatusCode.ERROR,
                SecureStatusCode.FAILED,
                SecureStatusCode.PARTIAL_SUCCESS
        };
        for (SecureStatusCode code : terminalFailures) {
            assertTrue(code.name() + " should be terminal failure", code.isTerminalFailure());
        }
    }

    @Test
    public void testFromStringCaseInsensitive() {
        assertEquals(SecureStatusCode.ACCEPTED, SecureStatusCode.fromString("accepted"));
        assertEquals(SecureStatusCode.TIMEOUT, SecureStatusCode.fromString("timeout"));
        assertEquals(SecureStatusCode.PARTIAL_SUCCESS, SecureStatusCode.fromString("Partial_Success"));
    }

    @Test
    public void testFromStringUnknownReturnsNull() {
        assertNull(SecureStatusCode.fromString("UNKNOWN_STATUS"));
        assertNull(SecureStatusCode.fromString(null));
        assertNull(SecureStatusCode.fromString(""));
    }
}
