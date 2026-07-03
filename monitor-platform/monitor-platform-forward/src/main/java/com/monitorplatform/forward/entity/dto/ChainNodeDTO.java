package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 链路节点DTO（递归树形结构）
 */
@Data
public class ChainNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备唯一标识
     */
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;


    /**
     * 父节点ID（用于明确父子关系）
     * - 根节点：parentId = null 或 0
     * - 子节点：parentId = 父节点的设备ID或节点ID
     *
     * 注意：这个字段在前端传递时使用，后端会根据树形结构自动计算实际的parent_id
     */
    private String parentId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型
     */
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    /**
     * 设备IP地址
     */
    private String deviceIp;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 子节点列表（递归结构）
     */
    private List<ChainNodeDTO> children;
}
