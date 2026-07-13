package com.monitorplatform.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容监看记录实体
 * 管控平台存储的内容监看记录
 */
@Data
@TableName("t_content_monitor")
public class ContentMonitor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内容ID，来自加密网关 */
    private String contentId;

    /** 网关ID */
    private String gatewayId;

    /** 链路ID */
    private Long chainId;

    /** 内容类型：image/text */
    private String contentType;

    /** 内容数据（base64或文本） */
    private String data;

    /** MinIO 对象路径（迁移后填充） */
    private String minioPath;

    /** 文件名 */
    private String fileName;

    /** 描述 */
    private String description;

    /** 缩略图（base64） */
    private String thumbnail;

    /** 来源IP */
    private String sourceIp;

    /** 情报板IP（转发目标IP） */
    private String boardIp;

    /** 情报板端口（转发目标端口） */
    private Integer boardPort;

    /** Sigma playlist batch ID. */
    private String playBatchId;

    /** Sequence inside the playlist batch, starting from 0. */
    private Integer playBatchSeq;

    /** Total item count of the playlist batch. */
    private Integer playBatchSize;

    /** Original publish request ID for matching diagnostic_event_log.trace_id. */
    private String publishRequestId;

    /** 状态：pending-待识别，normal-正常，violation-违规，stopped-已切断 */
    private String status;

    /** 是否违规：0-否，1-是 */
    private Integer isViolation;

    /** 违规类型 */
    private String violationType;

    /** 识别置信度 */
    private Double confidence;

    /** 命中的敏感词 */
    private String keywords;

    /** AI判定依据 */
    private String reason;

    /** 阿里云请求追踪ID */
    private String requestId;

    /** 检测失败错误信息 */
    private String errorMessage;

    /** 接收时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime receiveTime;

    /** 识别时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime recognitionTime;

    /** 处理时间（切断/恢复） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime handleTime;

    /** 处理人 */
    private String handleBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;

}
