package com.monitorplatform.device.service;

import com.monitorplatform.device.entity.dto.ForwardChannelConfigDTO;

/**
 * 网关转发配置服务接口
 * 管控平台向加密网关下发转发配置
 */
public interface GatewayForwardConfigService {

    /**
     * 下发转发配置到指定网关
     * @param dto 转发配置信息
     * @return 是否成功
     */
    boolean deployConfigToGateway(ForwardChannelConfigDTO dto);

    /**
     * 批量下发配置到指定网关
     * @param gatewaySn 网关序列号
     * @param configs 配置列表
     * @return 成功下发的数量
     */
    int batchDeployConfigs(String gatewaySn, ForwardChannelConfigDTO[] configs);

    /**
     * 更新网关转发配置
     * @param dto 转发配置信息
     * @return 是否成功
     */
    boolean updateConfigToGateway(ForwardChannelConfigDTO dto);

    /**
     * 删除网关转发配置
     * @param gatewaySn 网关序列号
     * @param configId 配置ID
     * @return 是否成功
     */
    boolean deleteConfigFromGateway(String gatewaySn, Long configId);

    /**
     * 控制网关转发通道（启动/停止/重启）
     * @param gatewaySn 网关序列号
     * @param configId 配置ID
     * @param action 操作类型：start, stop, restart
     * @return 是否成功
     */
    boolean controlChannel(String gatewaySn, Long configId, String action);

    /**
     * 查询网关转发配置列表
     * @param gatewaySn 网关序列号
     * @return 配置列表
     */
    Object getConfigsFromGateway(String gatewaySn);

    /**
     * 测试网关转发通道连通性
     * @param gatewaySn 网关序列号
     * @param configId 配置ID
     * @return 是否连通
     */
    boolean testConnectionFromGateway(String gatewaySn, Long configId);
}
