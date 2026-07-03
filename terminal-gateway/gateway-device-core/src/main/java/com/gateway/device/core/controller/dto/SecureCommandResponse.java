package com.gateway.device.core.controller.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 密文发布响应 —— 解密网关返回给加密网关的执行结果。
 */
@Data
public class SecureCommandResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否接受（解密 + 解析 + 编排启动成功） */
    private boolean accepted;

    /** 投递任务 ID（回传） */
    private String deliveryTaskId;

    /** 请求 ID（回显 X-Request-Id，用于幂等与链路追踪） */
    private String requestId;

    /** 编排任务 ID（解密网关内部任务，用于后续查询） */
    private String orchestrationTaskId;

    /** 状态：ACCEPTED / REJECTED / ERROR / FAILED / DECRYPT_FAILED / BAD_JSON / VALIDATION_FAILED / TARGET_NOT_FOUND / DUPLICATE_REQUEST */
    private String status;

    /** 数字状态码（与 {@link SecureStatusCode#code()} 对齐） */
    private Integer code;

    /** 消息 / 拒绝原因 */
    private String message;

    /** 子步骤任务 ID 列表（每个文件上传、清除、播放列表设置各产生一个 batchTask） */
    private List<StepResult> steps;

    // ──────────────── 静态工厂 ────────────────

    public static SecureCommandResponse accepted(String deliveryTaskId, String orchestrationTaskId) {
        SecureCommandResponse resp = new SecureCommandResponse();
        resp.accepted = true;
        resp.deliveryTaskId = deliveryTaskId;
        resp.orchestrationTaskId = orchestrationTaskId;
        resp.status = SecureStatusCode.ACCEPTED.status();
        resp.code = SecureStatusCode.ACCEPTED.code();
        resp.message = "密文包已解密并进入编排执行";
        return resp;
    }

    public static SecureCommandResponse rejected(String deliveryTaskId, String reason) {
        SecureCommandResponse resp = new SecureCommandResponse();
        resp.accepted = false;
        resp.deliveryTaskId = deliveryTaskId;
        resp.status = SecureStatusCode.REJECTED.status();
        resp.code = SecureStatusCode.REJECTED.code();
        resp.message = reason;
        return resp;
    }

    /**
     * 带结构化状态码的拒绝工厂，用于解密失败 / JSON 解析失败 / 参数校验失败等场景。
     */
    public static SecureCommandResponse rejected(String deliveryTaskId, SecureStatusCode statusCode, String reason) {
        SecureCommandResponse resp = new SecureCommandResponse();
        resp.accepted = false;
        resp.deliveryTaskId = deliveryTaskId;
        resp.status = statusCode.status();
        resp.code = statusCode.code();
        resp.message = reason != null ? reason : statusCode.defaultLabel();
        return resp;
    }

    public static SecureCommandResponse error(String deliveryTaskId, String errorMsg) {
        SecureCommandResponse resp = new SecureCommandResponse();
        resp.accepted = false;
        resp.deliveryTaskId = deliveryTaskId;
        resp.status = SecureStatusCode.ERROR.status();
        resp.code = SecureStatusCode.ERROR.code();
        resp.message = errorMsg;
        return resp;
    }

    public static SecureCommandResponse failed(String deliveryTaskId, String orchestrationTaskId, String message) {
        SecureCommandResponse resp = new SecureCommandResponse();
        resp.accepted = false;
        resp.deliveryTaskId = deliveryTaskId;
        resp.orchestrationTaskId = orchestrationTaskId;
        resp.status = SecureStatusCode.FAILED.status();
        resp.code = SecureStatusCode.FAILED.code();
        resp.message = message;
        return resp;
    }

    /**
     * 幂等命中：回放已存在的 orchestrationTaskId，避免重复下发设备命令。
     */
    public static SecureCommandResponse duplicate(String deliveryTaskId, String orchestrationTaskId, String message) {
        SecureCommandResponse resp = new SecureCommandResponse();
        resp.accepted = true;
        resp.deliveryTaskId = deliveryTaskId;
        resp.orchestrationTaskId = orchestrationTaskId;
        resp.status = SecureStatusCode.DUPLICATE_REQUEST.status();
        resp.code = SecureStatusCode.DUPLICATE_REQUEST.code();
        resp.message = message != null ? message : "重复请求已忽略";
        return resp;
    }

    // ──────────────── 子步骤结果 ────────────────

    @Data
    public static class StepResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 步骤名称：CLEAR / FILE_UPLOAD / PLAYLIST_SET */
        private String step;
        /** 文件序号（仅 FILE_UPLOAD 步骤有值） */
        private Integer fileOrderNo;
        /** 文件名 */
        private String fileName;
        /** 内部批量任务 ID */
        private String batchTaskId;
        /** 状态 */
        private String status;
        /** 失败原因或设备返回消息 */
        private String message;
        /** 上传后设备侧真实路径 */
        private String devicePath;
    }
}
