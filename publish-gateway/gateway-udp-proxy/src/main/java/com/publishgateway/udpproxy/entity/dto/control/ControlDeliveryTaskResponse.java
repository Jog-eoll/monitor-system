package com.publishgateway.udpproxy.entity.dto.control;

import lombok.Data;

import java.io.Serializable;

/**
 * 控制任务投递响应
 */
@Data
public class ControlDeliveryTaskResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deliveryTaskId;
    private String status;
    private String message;
    private long createdAt;

    public static ControlDeliveryTaskResponse created(String deliveryTaskId) {
        ControlDeliveryTaskResponse resp = new ControlDeliveryTaskResponse();
        resp.deliveryTaskId = deliveryTaskId;
        resp.status = "ACCEPTED";
        resp.message = "控制任务已接受，正在投递到解密网关";
        resp.createdAt = System.currentTimeMillis();
        return resp;
    }

    public static ControlDeliveryTaskResponse error(String message) {
        ControlDeliveryTaskResponse resp = new ControlDeliveryTaskResponse();
        resp.status = "ERROR";
        resp.message = message;
        resp.createdAt = System.currentTimeMillis();
        return resp;
    }
}
