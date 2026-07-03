package com.publishgateway.udpproxy.entity.dto.secure;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 解密网关统一响应 ACK DTO
 * <p>
 * 解密网关返回结构：
 * <pre>
 * {
 *   "code": 200,
 *   "msg": "...",
 *   "data": {
 *     "accepted": true,
 *     "status": "ACCEPTED",
 *     "code": "...",
 *     "message": "...",
 *     "requestId": "...",
 *     "batchTaskId": "...",
 *     "orchestrationTaskId": "...",
 *     "mappedCapability": "...",
 *     "steps": [ { "stepName", "status", "message" } ]
 *   }
 * }
 * </pre>
 * 失败判定以 data.accepted == false 或内部 status 为准，不只看外层 Result.code。
 * DUPLICATE_REQUEST 如果 accepted=true，应按成功回放处理。
 * </p>
 */
@Data
public class SecureGatewayAck implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 外层 Result 字段 ──
    private Integer outerCode;
    private String outerMsg;

    // ── 内层 data 字段 ──
    private Boolean accepted;
    private String status;
    private String code;
    private String message;
    private String requestId;
    private String taskId;
    private String batchTaskId;
    private String orchestrationTaskId;
    private String mappedCapability;
    private List<StepInfo> steps;

    @Data
    public static class StepInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String step;
        private String stepName;
        private String status;
        private String message;
    }

    /**
     * 判断是否为最终失败
     * <p>
     * accepted == false 永远按失败处理，即使 status/code 缺失。
     * accepted == true 时基于终态失败集合判断：
     *   DUPLICATE_REQUEST + accepted=true → 按成功回放处理，不算失败。
     *   TIMEOUT → 可重试失败，但也会被此处识别为失败。
     * </p>
     */
    public boolean isTerminalFailure() {
        if (Boolean.FALSE.equals(accepted)) {
            return true;
        }
        SecureStatusCode statusCode = SecureStatusCode.fromString(status);
        if (statusCode != null && statusCode.isFailure()) {
            return true;
        }
        if (hasText(status) && statusCode == null) {
            return true;
        }
        if (steps != null) {
            for (StepInfo step : steps) {
                SecureStatusCode stepStatus = SecureStatusCode.fromString(step.getStatus());
                if (stepStatus != null && stepStatus.isFailure()) {
                    return true;
                }
                if (hasText(step.getStatus()) && stepStatus == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否为 DUPLICATE_REQUEST 幂等回放成功
     */
    public boolean isDuplicateAccepted() {
        SecureStatusCode statusCode = SecureStatusCode.fromString(status);
        return statusCode == SecureStatusCode.DUPLICATE_REQUEST && Boolean.TRUE.equals(accepted);
    }

    /**
     * 获取最可读的错误信息
     */
    public String getErrorMessage() {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        if (outerMsg != null && !outerMsg.trim().isEmpty()) {
            return outerMsg;
        }
        if (status != null) {
            return "status=" + status;
        }
        return "unknown error";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
