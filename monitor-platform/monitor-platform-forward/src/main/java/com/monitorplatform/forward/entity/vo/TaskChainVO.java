package com.monitorplatform.forward.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务链路详情VO
 */
@Data
public class TaskChainVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 链路名称
     */
    private String chainName;

    /**
     * 链路编码
     */
    private String chainCode;

    /**
     * 链路描述
     */
    @JsonIgnore
    private String chainDesc;

    /**
     * 链路状态: 0-离线/不可用, 1-在线/可用, 2-部分可用
     */
    private Integer status;

    /**
     * 状态描述
     */
    @JsonIgnore
    private String statusDesc;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 配置版本号
     */
    @JsonIgnore
    private Integer version;

    /**
     * 校验状态: 0-未校验, 1-校验通过, 2-校验失败
     */
    @JsonIgnore
    private Integer validationStatus;

    /**
     * 校验消息
     */
    @JsonIgnore
    private String validationMessage;

    /**
     * 最后校验时间
     */
    @JsonIgnore
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastValidationTime;

    /**
     * 总节点数
     */
    @JsonIgnore
    private Integer totalNodes;

    /**
     * 核心节点数
     */
    @JsonIgnore
    private Integer coreNodes;

    /**
     * 可选节点数
     */
    @JsonIgnore
    private Integer optionalNodes;

    /**
     * 健康度评分（0-100）
     */
    private Integer healthScore;

    /**
     * 健康等级: A/B/C/D/F
     */
    private String healthLevel;

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
    @JsonIgnore
    private String createdBy;

    /**
     * 更新人
     */
    @JsonIgnore
    private String updatedBy;

    /**
     * 备注信息
     */
    @JsonIgnore
    private String remark;

    /**
     * 节点列表（树形结构的根节点）
     */
    private List<TaskChainNodeVO> nodes;
}
