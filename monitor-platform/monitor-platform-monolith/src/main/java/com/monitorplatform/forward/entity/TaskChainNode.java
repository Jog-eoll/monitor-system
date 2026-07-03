package com.monitorplatform.forward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务链路节点实体
 */
@Data
@TableName("task_chain_node")
public class TaskChainNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 链路ID（外键关联task_chain_config）
     */
    private Long chainId;

    /**
     * 设备唯一标识（关联unified_device_info）
     */
    private String deviceId;

    /**
     * 设备名称（冗余字段）
     */
    private String deviceName;

    /**
     * 设备类型: publish_server(发布服务器), publish_gateway(发布加密网关), 
     * terminal_encrypt_gateway(终端加密网关), content_server(内容识别服务器), info_board(情报板)
     */
    private String deviceType;

    /**
     * 父节点ID（根节点为NULL）
     */
    private Long parentId;

    /**
     * 节点状态（来自设备状态）: 在线/离线/异常
     */
    private String nodeStatus;

    /**
     * 设备IP地址
     */
    private String deviceIp;

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
