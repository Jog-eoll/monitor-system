package com.monitorplatform.forward.entity.dto;


import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 创建任务链路节点DTO
 * 用于在已存在的链路基础上，添加完整的节点拓扑结构
 *
 * 业务场景：用户在"任务链路拓扑图"页面拖拽完四个核心设备后，
 * 点击"确定"按钮时调用此接口，一次性创建所有节点
 *
 * @author monitor-platform
 * @date 2026-02-02
 */
@Data
public class CreateChainNodesDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 链路ID（必传，关联已创建的链路基本信息）
     */
    @NotNull(message = "链路ID不能为空")
    private Long chainId;


    @NotEmpty(message = "节点列表不能为空")
    private List<ChainNodeDTO> rootNodes;

    /**
     * 操作人
     */
    private String operatorBy;
}
