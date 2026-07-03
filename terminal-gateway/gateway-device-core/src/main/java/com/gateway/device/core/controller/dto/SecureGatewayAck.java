package com.gateway.device.core.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一响应 DTO —— 发布包与控制指令返回给加密网关的稳定信封。
 * <p>
 * 发布网关只依赖以下稳定字段判读结果，不再因为发布与控制两条链路返回结构不同而误判：
 * <ul>
 *   <li>{@code accepted} —— 是否已接受</li>
 *   <li>{@code status} —— {@link SecureStatusCode} 取值</li>
 *   <li>{@code code} —— 与 status 对应的数字码</li>
 *   <li>{@code message} —— 人类可读说明</li>
 *   <li>{@code requestId} / {@code taskId} —— 回显请求标识</li>
 *   <li>{@code batchTaskId} / {@code orchestrationTaskId} —— 解密网关内部任务 ID</li>
 *   <li>{@code mappedCapability} —— 映射后的内部能力名</li>
 *   <li>{@code steps} —— 发布包子步骤结果</li>
 * </ul>
 * </p>
 *
 * <p>本类不替代 {@link SecureCommandResponse} / {@link SecureControlResponse}，
 * 而是作为统一渲染层：Controller 仍返回各自响应，内部字段与 {@code SecureGatewayAck}
 * 字段名保持一致，发布网关可按统一字段解析。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecureGatewayAck implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean accepted;
    private String status;
    private Integer code;
    private String message;

    private String requestId;
    /** 任务 ID：控制指令回显 commandTaskId，发布包回显 deliveryTaskId */
    private String taskId;
    /** 控制指令解密网关内部批量任务 ID */
    private String batchTaskId;
    /** 发布包解密网关内部编排任务 ID */
    private String orchestrationTaskId;
    /** 映射后的内部能力名 */
    private String mappedCapability;
    /** 发布包子步骤结果 */
    private List<SecureCommandResponse.StepResult> steps;

    public static SecureGatewayAck of(SecureStatusCode statusCode, String message) {
        SecureGatewayAck ack = new SecureGatewayAck();
        ack.accepted = statusCode.isAccepted();
        ack.status = statusCode.status();
        ack.code = statusCode.code();
        ack.message = message != null ? message : statusCode.defaultLabel();
        return ack;
    }

    public static SecureGatewayAck accepted(String message) {
        return of(SecureStatusCode.ACCEPTED, message);
    }

    public static SecureGatewayAck rejected(SecureStatusCode statusCode, String message) {
        return of(statusCode, message);
    }

    public SecureGatewayAck requestId(String value) {
        this.requestId = value;
        return this;
    }

    public SecureGatewayAck taskId(String value) {
        this.taskId = value;
        return this;
    }

    public SecureGatewayAck batchTaskId(String value) {
        this.batchTaskId = value;
        return this;
    }

    public SecureGatewayAck orchestrationTaskId(String value) {
        this.orchestrationTaskId = value;
        return this;
    }

    public SecureGatewayAck mappedCapability(String value) {
        this.mappedCapability = value;
        return this;
    }

    public SecureGatewayAck steps(List<SecureCommandResponse.StepResult> value) {
        this.steps = value;
        return this;
    }
}
