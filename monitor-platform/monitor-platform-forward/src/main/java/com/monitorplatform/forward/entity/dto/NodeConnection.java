package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 节点连线关系
 */
@Data
public class NodeConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 起始节点临时ID
     */
    @NotBlank(message = "起始节点ID不能为空")
    private String fromNodeId;

    /**
     * 目标节点临时ID
     */
    @NotBlank(message = "目标节点ID不能为空")
    private String toNodeId;

    /**
     * 分支编码（用于区分不同分支，如 "B1", "B2"）
     * 从发布网关到终端网关时必填
     */
    private String branchCode;

    /**
     * 分支名称（如：北区分支、南区分支）
     */
    private String branchName;
}
