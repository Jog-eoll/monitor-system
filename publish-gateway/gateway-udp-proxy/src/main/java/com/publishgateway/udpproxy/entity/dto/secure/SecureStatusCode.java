package com.publishgateway.udpproxy.entity.dto.secure;

/**
 * 解密网关响应状态码枚举
 * <p>
 * 与解密网关 SecureCommandController 返回的内部 status 对齐。
 * 失败状态集合用于判定投递是否成功；DUPLICATE_REQUEST + accepted=true 按成功回放处理。
 * </p>
 */
public enum SecureStatusCode {

    // ── 成功类 ──
    ACCEPTED,
    SUCCESS,
    RUNNING,
    PENDING,
    DUPLICATE_REQUEST,

    // ── 失败类 ──
    REJECTED,
    DECRYPT_FAILED,
    BAD_JSON,
    VALIDATION_FAILED,
    TARGET_NOT_FOUND,
    UNSUPPORTED_CAPABILITY,
    ERROR,
    FAILED,
    TIMEOUT,
    PARTIAL_SUCCESS;

    /**
     * 判断是否为失败状态（包括 TIMEOUT 和 PARTIAL_SUCCESS）
     */
    public boolean isFailure() {
        switch (this) {
            case REJECTED:
            case DECRYPT_FAILED:
            case BAD_JSON:
            case VALIDATION_FAILED:
            case TARGET_NOT_FOUND:
            case UNSUPPORTED_CAPABILITY:
            case ERROR:
            case FAILED:
            case TIMEOUT:
            case PARTIAL_SUCCESS:
                return true;
            default:
                return false;
        }
    }

    /**
     * 判断是否为终态失败（不重试）
     * 终态失败包括：REJECTED、DECRYPT_FAILED、BAD_JSON、VALIDATION_FAILED、
     * TARGET_NOT_FOUND、UNSUPPORTED_CAPABILITY、ERROR、FAILED、PARTIAL_SUCCESS
     */
    public boolean isTerminalFailure() {
        switch (this) {
            case REJECTED:
            case DECRYPT_FAILED:
            case BAD_JSON:
            case VALIDATION_FAILED:
            case TARGET_NOT_FOUND:
            case UNSUPPORTED_CAPABILITY:
            case ERROR:
            case FAILED:
            case PARTIAL_SUCCESS:
                return true;
            default:
                return false;
        }
    }

    /**
     * 判断是否为可重试失败
     */
    public boolean isRetryableFailure() {
        return this == TIMEOUT;
    }

    /**
     * 判断是否为幂等回放成功
     */
    public static boolean isAcceptedReplay(SecureStatusCode statusCode, Boolean accepted) {
        return statusCode == DUPLICATE_REQUEST && Boolean.TRUE.equals(accepted);
    }

    /**
     * 从字符串安全解析，未知值返回 null
     */
    public static SecureStatusCode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
