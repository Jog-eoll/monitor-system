package com.gateway.device.core.controller.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 密文控制响应 —— 解密网关返回给加密网关的控制指令执行结果。
 */
@Data
public class SecureControlResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否接受 */
    private boolean accepted;

    /** 控制任务 ID（来自客户端） */
    private String commandTaskId;

    /** 请求 ID（回显 X-Request-Id，用于幂等与链路追踪） */
    private String requestId;

    /** 解密网关内部批量任务 ID（可用于查询执行结果） */
    private String batchTaskId;

    /** 状态：ACCEPTED / REJECTED / ERROR / DECRYPT_FAILED / BAD_JSON / VALIDATION_FAILED / TARGET_NOT_FOUND / UNSUPPORTED_CAPABILITY / DUPLICATE_REQUEST */
    private String status;

    /** 数字状态码（与 {@link SecureStatusCode#code()} 对齐） */
    private Integer code;

    /** 消息 */
    private String message;

    /** 内部映射后的 capability 名称 */
    private String mappedCapability;

    // ──────────────── 静态工厂 ────────────────

    public static SecureControlResponse accepted(String commandTaskId, String batchTaskId, String capability) {
        SecureControlResponse resp = new SecureControlResponse();
        resp.accepted = true;
        resp.commandTaskId = commandTaskId;
        resp.batchTaskId = batchTaskId;
        resp.mappedCapability = capability;
        resp.status = SecureStatusCode.ACCEPTED.status();
        resp.code = SecureStatusCode.ACCEPTED.code();
        resp.message = "控制指令已接受并下发执行";
        return resp;
    }

    public static SecureControlResponse rejected(String commandTaskId, String reason) {
        SecureControlResponse resp = new SecureControlResponse();
        resp.accepted = false;
        resp.commandTaskId = commandTaskId;
        resp.status = SecureStatusCode.REJECTED.status();
        resp.code = SecureStatusCode.REJECTED.code();
        resp.message = reason;
        return resp;
    }

    /**
     * 带结构化状态码的拒绝工厂，用于解密失败 / JSON 解析失败 / 参数校验失败 /
     * 目标设备缺失 / 能力不支持等场景。
     */
    public static SecureControlResponse rejected(String commandTaskId, SecureStatusCode statusCode, String reason) {
        SecureControlResponse resp = new SecureControlResponse();
        resp.accepted = false;
        resp.commandTaskId = commandTaskId;
        resp.status = statusCode.status();
        resp.code = statusCode.code();
        resp.message = reason != null ? reason : statusCode.defaultLabel();
        return resp;
    }

    public static SecureControlResponse error(String commandTaskId, String errorMsg) {
        SecureControlResponse resp = new SecureControlResponse();
        resp.accepted = false;
        resp.commandTaskId = commandTaskId;
        resp.status = SecureStatusCode.ERROR.status();
        resp.code = SecureStatusCode.ERROR.code();
        resp.message = errorMsg;
        return resp;
    }

    /**
     * 幂等命中：回放已存在的 batchTaskId，避免重复下发设备命令。
     */
    public static SecureControlResponse duplicate(String commandTaskId, String batchTaskId,
                                                   String capability, String message) {
        SecureControlResponse resp = new SecureControlResponse();
        resp.accepted = true;
        resp.commandTaskId = commandTaskId;
        resp.batchTaskId = batchTaskId;
        resp.mappedCapability = capability;
        resp.status = SecureStatusCode.DUPLICATE_REQUEST.status();
        resp.code = SecureStatusCode.DUPLICATE_REQUEST.code();
        resp.message = message != null ? message : "重复请求已忽略";
        return resp;
    }
}
