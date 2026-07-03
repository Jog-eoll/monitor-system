package com.publishgateway.udpproxy.entity.dto.delivery;

import lombok.Data;

import java.io.Serializable;

/**
 * 安全投递任务响应
 */
@Data
public class SecureDeliveryTaskResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deliveryTaskId;
    private String status;
    private String message;
    private long createdAt;

    public static SecureDeliveryTaskResponse created(String deliveryTaskId) {
        SecureDeliveryTaskResponse r = new SecureDeliveryTaskResponse();
        r.deliveryTaskId = deliveryTaskId;
        r.status = "ACCEPTED";
        r.createdAt = System.currentTimeMillis();
        return r;
    }

    public static SecureDeliveryTaskResponse error(String message) {
        SecureDeliveryTaskResponse r = new SecureDeliveryTaskResponse();
        r.status = "FAILED";
        r.message = message;
        r.createdAt = System.currentTimeMillis();
        return r;
    }
}
