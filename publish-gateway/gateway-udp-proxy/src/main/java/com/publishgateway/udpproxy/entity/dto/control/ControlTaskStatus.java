package com.publishgateway.udpproxy.entity.dto.control;

import lombok.Data;

import java.io.Serializable;

/**
 * 控制任务状态 —— 内存存储，跟踪投递进度。
 */
@Data
public class ControlTaskStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 控制任务 ID */
    private String commandTaskId;

    /** 投递任务 ID */
    private String deliveryTaskId;

    /** 状态：ACCEPTED / RUNNING / SUCCESS / FAILED / TIMEOUT */
    private String status;

    /** 控制指令 */
    private String command;

    /** 解密网关内部批量任务 ID */
    private String batchTaskId;

    /** 解密网关映射后的内部能力 */
    private String mappedCapability;

    /** 解密网关最近一次状态 */
    private String terminalStatus;

    /** 消息 */
    private String message;

    /** 创建时间 */
    private long createdAt;

    /** 更新时间 */
    private long updatedAt;

    /** 解密网关返回的完整任务数据 */
    private Object terminalTask;

    /** 解密网关返回的设备执行结果列表 */
    private Object terminalResults;
}
