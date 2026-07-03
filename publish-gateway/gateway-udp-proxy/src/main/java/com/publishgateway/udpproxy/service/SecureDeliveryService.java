package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.dto.delivery.DeliveryTaskStatus;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskResponse;

/**
 * 安全投递服务
 */
public interface SecureDeliveryService {

    /**
     * 创建投递任务（异步执行）
     */
    SecureDeliveryTaskResponse createTask(SecureDeliveryTaskRequest request);

    /**
     * 查询任务状态
     */
    DeliveryTaskStatus getTaskStatus(String deliveryTaskId);

    /**
     * 健康检查
     */
    boolean isHealthy();
}
