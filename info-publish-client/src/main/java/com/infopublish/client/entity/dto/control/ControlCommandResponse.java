package com.infopublish.client.entity.dto.control;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 控制指令执行响应 —— 客户端返回给 Sigma 的调用结果。
 */
@Data
public class ControlCommandResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否接受 */
    private boolean accepted;

    /** 兼容字段：接口是否处理成功 */
    private boolean success;

    /** 兼容字段：是否允许执行 */
    private boolean allowed;

    /** 控制任务 ID */
    private String commandTaskId;

    /** 状态：ACCEPTED / REJECTED / ERROR */
    private String status;

    /** 兼容字段：ACCEPTED / REJECTED / ERROR */
    private String state;

    /** 错误码 */
    private String code;

    /** 消息 */
    private String message;

    /** 加密网关投递任务 ID（投递成功后回填） */
    private String deliveryTaskId;

    private String requestId;

    private String command;

    private Boolean finalResult;

    private Map<String, Object> target;

    private Map<String, Object> deviceResult;

    private Map<String, Object> deviceInfo;

    private Long timestamp;

    // ──────────────── 静态工厂 ────────────────

    public static ControlCommandResponse accepted(String commandTaskId, String deliveryTaskId) {
        ControlCommandResponse resp = new ControlCommandResponse();
        resp.accepted = true;
        resp.success = true;
        resp.allowed = true;
        resp.commandTaskId = commandTaskId;
        resp.deliveryTaskId = deliveryTaskId;
        resp.status = "ACCEPTED";
        resp.state = "ACCEPTED";
        resp.finalResult = false;
        resp.timestamp = System.currentTimeMillis();
        resp.message = "控制指令已提交";
        return resp;
    }

    public static ControlCommandResponse rejected(String commandTaskId, String reason) {
        return rejected(commandTaskId, "COMMAND_REJECTED", reason);
    }

    public static ControlCommandResponse rejected(String commandTaskId, String code, String reason) {
        ControlCommandResponse resp = new ControlCommandResponse();
        resp.accepted = false;
        resp.success = false;
        resp.allowed = false;
        resp.commandTaskId = commandTaskId;
        resp.status = "REJECTED";
        resp.state = "REJECTED";
        resp.finalResult = true;
        resp.code = code;
        resp.message = reason;
        resp.timestamp = System.currentTimeMillis();
        return resp;
    }

    public static ControlCommandResponse error(String commandTaskId, String errorMsg) {
        return error(commandTaskId, "COMMAND_EXECUTE_FAILED", errorMsg);
    }

    public static ControlCommandResponse error(String commandTaskId, String code, String errorMsg) {
        ControlCommandResponse resp = new ControlCommandResponse();
        resp.accepted = false;
        resp.success = false;
        resp.allowed = false;
        resp.commandTaskId = commandTaskId;
        resp.status = "ERROR";
        resp.state = "ERROR";
        resp.finalResult = true;
        resp.code = code;
        resp.message = errorMsg;
        resp.timestamp = System.currentTimeMillis();
        return resp;
    }
}
