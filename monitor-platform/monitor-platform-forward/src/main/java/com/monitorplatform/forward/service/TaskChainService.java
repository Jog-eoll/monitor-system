package com.monitorplatform.forward.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.forward.entity.dto.*;
import com.monitorplatform.forward.entity.vo.ChainHealthVO;
import com.monitorplatform.forward.entity.vo.DeploySummaryVO;
import com.monitorplatform.forward.entity.vo.InfoBoardInfoVO;
import com.monitorplatform.forward.entity.vo.StopGatewayProxyResultVO;
import com.monitorplatform.forward.entity.vo.TaskChainNodeVO;
import com.monitorplatform.forward.entity.vo.TaskChainVO;
import com.monitorplatform.forward.entity.vo.TerminalGatewayInfoVO;

import java.util.List;

/**
 * 任务链路配置服务接口
 */
public interface TaskChainService {

    /**
     * 创建任务链路
     * @param dto 创建DTO
     * @return 链路ID
     */
    Long createChain(CreateChainDTO dto);


    /**
     * 为已存在的链路创建节点拓扑结构
    **/
    Boolean createChainNodes(CreateChainNodesDTO dto);
    /**
     * 更新任务链路
     * @param dto 更新DTO
     * @return 是否成功
     */
    Boolean updateChain(UpdateChainDTO dto);

    /**
     * 删除任务链路（逻辑删除）
     * @param chainId 链路ID
     * @return 是否成功
     */
    Boolean deleteChain(Long chainId);

    /**
     * 根据ID查询链路详情
     * @param chainId 链路ID
     * @return 链路详情
     */
    TaskChainVO getChainById(Long chainId);

    /**
     * 手动下发链路配置到网关
     * @param chainId 链路ID
     * @return 下发结果统计
     */
    DeploySummaryVO manualDeployConfig(Long chainId);

    /**
     * 分页查询链路列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    Page<TaskChainVO> queryChainPage(ChainQueryDTO queryDTO);

    /**
     * 校验链路合法性
     * @param chainId 链路ID
     * @return 校验结果
     */
    Boolean validateChain(Long chainId);

    /**
     * 同步链路状态（根据节点状态更新链路状态）
     * @param chainId 链路ID
     * @return 是否成功
     */
    Boolean syncChainStatus(Long chainId);

    /**
     * 批量同步所有链路状态
     * @return 同步数量
     */
    Integer syncAllChainStatus();

    /**
     * 查询设备被占用情况
     * @param deviceId 设备ID
     * @return 占用该设备的链路列表
     */
    List<TaskChainVO> queryChainsByDevice(String deviceId);

    /**
     * 检查设备是否可用（未被占用且状态正常）
     * @param deviceId 设备ID
     * @return 是否可用
     */
    Boolean checkDeviceAvailable(String deviceId);

    // ==================== 树形节点操作 ====================

    /**
     * 创建节点（树形结构）
     * @param chainId 链路ID
     * @param parentId 父节点ID（null表示根节点）
     * @param nodeDTO 节点数据
     * @return 节点ID
     */
    Long createNode(Long chainId, Long parentId, ChainNodeDTO nodeDTO);

    /**
     * 更新节点
     * @param nodeId 节点ID
     * @param nodeDTO 节点数据
     * @return 是否成功
     */
    Boolean updateNode(Long nodeId, ChainNodeDTO nodeDTO);

    /**
     * 删除节点（级联删除所有子节点）
     * @param nodeId 节点ID
     * @return 是否成功
     */
    Boolean deleteNode(Long nodeId);

    /**
     * 移动节点到新的父节点下
     * @param nodeId 节点ID
     * @param newParentId 新父节点ID（null表示移动到根层级）
     * @return 是否成功
     */
    Boolean moveNode(Long nodeId, Long newParentId);

    /**
     * 查询节点详情
     * @param nodeId 节点ID
     * @return 节点详情
     */
    TaskChainNodeVO getNodeById(Long nodeId);

    /**
     * 获取链路树形结构
     * @param chainId 链路ID
     * @return 链路详情（包含树形节点）
     */
    TaskChainVO getChainTree(Long chainId);

    /**
     * 校验链路结构
     * @param chainId 链路ID
     * @return 校验结果
     */
    Boolean validateChainStructure(Long chainId);
    
    /**
     * 停止终端网关代理规则
     * @param chainId 链路ID
     * @param params 可选参数（reason, operator等）
     * @return 停止结果
     */
    StopGatewayProxyResultVO  stopTerminalGatewayProxy(Long chainId, StopGatewayProxyRequestDTO params);

    /**
     * 通过情报板 IP 查询对应的终端网关 IP 和端口
     *
     * 查询逻辑：
     *   1. 在 task_chain_node 中找 device_type='info_board' AND device_ip=boardIp 的节点
     *   2. 通过该节点的 parent_node_id 找到父节点（terminal_encrypt_gateway）
     *   3. 返回父节点的 device_ip 作为终端网关 IP
     *
     * @param boardIp 情报板 IP
     * @return Map 包含 terminalGatewayIp、terminalGatewayPort、boardIp、chainId 等
     *         若未找到则 success=false
     */
    TerminalGatewayInfoVO getTerminalGatewayByBoardIp(String boardIp);

    /**
     * 通过链路 ID 查询情报板 IP
     *
     *
     * @param chainId 链路 ID
     * @return Map 包含 infoBoardIp、chainId 等
     *         若未找到则 success=false
     */
    InfoBoardInfoVO getInfoBoardByChainId(Long chainId);

    /**
     * 启用/停用链路
     * 1. 更新 task_chain_config 的 enabled 和 status 字段
     * 2. 向两个网关下发 ENABLE/DISABLE 指令
     *
     * @param chainId 链路ID
     * @param enabled 1-启用 0-停用
     * @return true-成功
     */
    Boolean setChainEnabled(Long chainId, Integer enabled);

    // ==================== 链路健康度监控 ====================

    /**
     * 检查单条链路的健康度
     * 遍历所有节点，探测设备状态，计算健康度评分
     *
     * @param chainId 链路ID
     * @return 健康度检查结果
     */
    ChainHealthVO checkChainHealth(Long chainId);

    /**
     * 批量检查所有启用链路的健康度
     * 用于定时巡检任务
     *
     * @return 检查链路数量
     */
    Integer checkAllChainHealth();
}
