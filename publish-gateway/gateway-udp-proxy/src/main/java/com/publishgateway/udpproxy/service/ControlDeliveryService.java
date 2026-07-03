package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.dto.control.*;

import java.util.Map;

/**
 * 控制指令投递服务 —— 加密 + 投递 + 状态查询。
 */
public interface ControlDeliveryService {

    /**
     * 加密明文控制任务 JSON。
     *
     * @param request 加密请求
     * @return 加密响应（含 Base64 密文）
     */
    ControlTaskEncryptResponse encryptControlTask(ControlTaskEncryptRequest request);

    /**
     * 创建控制任务投递。
     *
     * @param request 投递请求
     * @return 投递响应
     */
    ControlDeliveryTaskResponse createControlTask(ControlDeliveryTaskRequest request);

    /**
     * 查询控制任务状态。
     *
     * @param commandTaskId 控制任务 ID
     * @return 任务状态信息
     */
    Map<String, Object> getControlTaskStatus(String commandTaskId);
}
