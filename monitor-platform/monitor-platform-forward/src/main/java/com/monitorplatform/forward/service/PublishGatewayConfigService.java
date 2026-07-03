package com.monitorplatform.forward.service;

import java.util.Map;

/**
 * 发布网关配置服务接口
 * 负责将任务链路配置下发到发布端加密网关
 */
public interface PublishGatewayConfigService {

    /**
     * 下发任务链路配置到发布网关（核心方法）
     * 默认为自动下发
     * 
     * @param chainId 链路ID
     * @return 下发结果（包含成功/失败数量、详细信息）
     */
    Map<String, Object> deployChainToPublishGateway(Long chainId);

    /**
     * 下发任务链路配置到发布网关（支持指定触发来源）
     * 
     * @param chainId 链路ID
     * @param triggerSource 触发来源："auto"=自动下发, "manual"=手动下发
     * @return 下发结果（包含成功/失败数量、详细信息）
     */
    Map<String, Object> deployChainToPublishGateway(Long chainId, String triggerSource);

    /**
     * 删除链路时通知两个网关停止并逻辑删除规则
     * 发布网关和终端网关的规则均执行逻辑删除（deleted=1）
     *
     * @param chainId 链路ID
     * @return 尚包含发布网关和终端网关的处理结果
     */
    Map<String, Object> stopChainRules(Long chainId);
    /**
     * 启用/停用链路：切换两个网关中该链路所有规则的 ENABLED/DISABLED 状态
     *
     * @param chainId 链路ID
     * @param enabled 1-启用 0-停用
     * @return 包含发布网关和终端网关处理结果
     */
    Map<String, Object> setChainEnabled(Long chainId, Integer enabled);
}
