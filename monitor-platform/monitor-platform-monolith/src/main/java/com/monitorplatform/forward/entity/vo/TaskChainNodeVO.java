package com.monitorplatform.forward.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务链路节点VO
 */
@Data
public class TaskChainNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 链路ID
     */
    private Long chainId;

    /**
     * 设备唯一标识
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 设备类型描述
     */
    private String deviceTypeDesc;

    /**
     * 父节点ID（根节点为NULL）
     */
    private Long parentId;

    /**
     * 子节点列表（树形结构）
     */
    private List<TaskChainNodeVO> children;

    /**
     * 节点状态
     */
    private String nodeStatus;

    /**
     * 设备IP地址（来自设备信息）
     */
    private String ipAddress;

    /**
     * 设备端口（来自设备信息）
     */
    private Integer port;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 备注信息
     */
    private String remark;
}
