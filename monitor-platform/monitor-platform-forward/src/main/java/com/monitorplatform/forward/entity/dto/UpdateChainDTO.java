package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 更新任务链路请求DTO
 */
@Data
public class UpdateChainDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 链路ID
     */
    @NotNull(message = "链路ID不能为空")
    private Long id;

    /**
     * 链路名称
     */
    @NotBlank(message = "链路名称不能为空")
    private String chainName;

    /**
     * 链路描述
     */
    private String chainDesc;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 主节点列表（发布服务器 + 发布加密网关）
     */
    @NotEmpty(message = "主节点列表不能为空")
    private List<ChainNodeDTO> mainNodes;


}
