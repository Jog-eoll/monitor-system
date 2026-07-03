package com.publishgateway.udpproxy.entity.dto.delivery;

import lombok.Data;

import java.io.Serializable;

/**
 * 投递任务状态信息
 */
@Data
public class DeliveryTaskStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ACCEPTED / RUNNING / SUCCESS / FAILED / TIMEOUT / CANCELED */
    private String status;
    private String deliveryTaskId;
    private String sigmaPublishId;
    private String message;
    private long createdAt;
    private long updatedAt;
    private int totalFiles;
    private int downloadedFiles;
    private int deliveredFiles;

    /** 解密网关编排任务 ID，方便后续查询 /api/secure-command/publish-tasks/{orchestrationTaskId} */
    private String orchestrationTaskId;
}
