package com.infopublish.client.service;

import com.infopublish.client.entity.dto.control.ControlCommandRequest;
import com.infopublish.client.entity.dto.control.ControlCommandResponse;

import java.util.Map;

/**
 * 控制指令服务 —— 负责安全检查、参数校验、调用加密网关完成控制指令投递。
 */
public interface ControlCommandService {

    /**
     * 执行控制指令。
     *
     * @param request 控制指令请求
     * @return 执行结果
     */
    ControlCommandResponse execute(ControlCommandRequest request);

    /**
     * 查询控制任务状态。
     *
     * @param commandTaskId 控制任务 ID
     * @return 任务状态信息
     */
    Map<String, Object> getTaskStatus(String commandTaskId);
}
