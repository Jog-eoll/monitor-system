package com.monitorplatform.forward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务链路配置实体
 */
@Data
@TableName("task_chain_config")
public class TaskChainConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 链路名称
     */
    private String chainName;

    /**
     * 链路编码（唯一标识）
     */
    private String chainCode;

    /**
     * 链路描述
     */
    private String chainDesc;

    /**
     * 链路状态: 0-离线/不可用, 1-在线/可用, 2-部分可用（可选设备不可用）
     */
    private Integer status;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 逻辑删除: 0-未删除, 1-已删除
     */
    private Integer deleted;

    /**
     * 配置版本号
     */
    private Integer version;

    /**
     * 父版本ID（用于版本历史追踪）
     */
    private Long parentVersionId;

    /**
     * 校验状态: 0-未校验, 1-校验通过, 2-校验失败
     */
    private Integer validationStatus;

    /**
     * 校验消息（记录校验失败原因）
     */
    private String validationMessage;

    /**
     * 最后校验时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastValidationTime;

    /**
     * 总节点数
     */
    private Integer totalNodes;

    /**
     * 核心节点数
     */
    private Integer coreNodes;

    /**
     * 可选节点数
     */
    private Integer optionalNodes;

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
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 健康度评分（0-100分）
     */
    private Integer healthScore;

    /**
     * 在线节点数
     */
    private Integer onlineNodes;

    /**
     * 离线节点数
     */
    private Integer offlineNodes;

    /**
     * 异常节点数
     */
    private Integer errorNodes;

    /**
     * 最后健康检查时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastHealthCheckTime;

    /**
     * 备注信息
     */
    private String remark;
}

