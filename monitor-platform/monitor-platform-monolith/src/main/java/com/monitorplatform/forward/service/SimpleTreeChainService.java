package com.monitorplatform.forward.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.forward.entity.dto.ChainNodeDTO;
import com.monitorplatform.forward.entity.dto.ChainQueryDTO;
import com.monitorplatform.forward.entity.dto.CreateChainDTO;
import com.monitorplatform.forward.entity.vo.TaskChainNodeVO;
import com.monitorplatform.forward.entity.vo.TaskChainVO;

import java.util.List;

/**
 * 简化版任务链路服务接口（纯树形结构）
 */
public interface SimpleTreeChainService {

    // ==================== 链路级别操作 ====================

    /**
     * 创建任务链路（树形结构）
     * @param dto 创建DTO（包含根节点及children）
     * @return 链路ID
     */
    Long createChain(CreateChainDTO dto);

    /**
     * 删除任务链路（逻辑删除，级联删除所有节点）
     * @param chainId 链路ID
     * @return 是否成功
     */
    Boolean deleteChain(Long chainId);

    /**
     * 查询链路详情（完整树形结构）
     * @param chainId 链路ID
     * @return 链路详情（包含完整节点树）
     */
    TaskChainVO getChainTree(Long chainId);

    /**
     * 分页查询链路列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    Page<TaskChainVO> queryChainPage(ChainQueryDTO queryDTO);

    // ==================== 节点级别操作 ====================

    /**
     * 创建节点（添加到指定父节点下）
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
     * 移动节点（改变父节点）
     * @param nodeId 节点ID
     * @param newParentId 新父节点ID
     * @return 是否成功
     */
    Boolean moveNode(Long nodeId, Long newParentId);

    /**
     * 查询节点（含子树）
     * @param nodeId 节点ID
     * @return 节点及其子树
     */
    TaskChainNodeVO getNodeTree(Long nodeId);

    /**
     * 查询节点的所有子节点
     * @param nodeId 节点ID
     * @return 子节点列表
     */
    List<TaskChainNodeVO> getChildren(Long nodeId);

    // ==================== 业务规则校验 ====================

    /**
     * 校验链路结构合法性
     * @param chainId 链路ID
     * @return 校验结果
     */
    Boolean validateChainStructure(Long chainId);
}
