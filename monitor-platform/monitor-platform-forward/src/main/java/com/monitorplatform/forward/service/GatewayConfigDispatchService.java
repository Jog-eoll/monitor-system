package com.monitorplatform.forward.service;

import com.monitorplatform.forward.entity.GatewayDispatchLog;
import com.monitorplatform.forward.entity.dto.GatewayBranchConfigDTO;

import java.util.List;

/**
 * 网关配置下发服务接口
 */
public interface GatewayConfigDispatchService {

    /**
     * 下发链路配置到网关（按分支粒度）
     * @param chainId 链路ID
     * @return 下发记录列表
     */
    List<GatewayDispatchLog> dispatchChainConfig(Long chainId);

    /**
     * 重发失败的配置
     * @param logId 下发记录ID
     * @return 是否成功
     */
    Boolean retryDispatch(Long logId);

    /**
     * 处理网关ACK回执
     * @param logId 下发记录ID
     * @param ackCode 回执码
     * @param ackMessage 回执信息
     * @return 是否成功
     */
    Boolean handleAck(Long logId, String ackCode, String ackMessage);

    /**
     * 网关主动拉取配置
     * @param gatewayDeviceId 网关设备ID
     * @return 该网关关联的所有分支配置
     */
    List<GatewayBranchConfigDTO> pullConfigByGateway(String gatewayDeviceId);

    /**
     * 查询链路的下发状态
     * @param chainId 链路ID
     * @return 下发记录列表
     */
    List<GatewayDispatchLog> queryDispatchStatus(Long chainId);

    /**
     * 批量重发失败的配置（定时任务调用）
     * @return 成功重发数量
     */
    Integer retryAllFailed();
}
