package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 创建任务链路DTO（简化树形结构）
 */
@Data
public class CreateChainDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 链路名称
     */
    @NotBlank(message = "链路名称不能为空")
    private String chainName;

    /**
     * 链路编码（唯一标识）
     */
    @NotBlank(message = "链路编码不能为空")
    private String chainCode;

    /**
     * 链路描述
     */
    private String chainDesc;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled = 1;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 根节点列表（树形结构，通常只有一个发布服务器）
     * 节点通过 children 嵌套表示父子关系
     * 
     * 注意：
     * - 分步创建时：传空数组 []，后续通过 /chain/node/create 接口逐个添加节点
     * - 一次性创建时：传完整树形结构
     */
    @NotNull(message = "根节点不能为null，分步创建请传空数组[]")
    private List<ChainNodeDTO> rootNodes;
}
