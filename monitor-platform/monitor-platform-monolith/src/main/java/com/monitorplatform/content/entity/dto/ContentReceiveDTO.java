package com.monitorplatform.content.entity.dto;

import lombok.Data;

/**
 * 内容接收DTO
 * 加密网关分发内容到管控平台
 */
@Data
public class ContentReceiveDTO {

    /** 内容ID */
    private String contentId;

    /** 网关ID */
    private String gatewayId;

    /** 链路ID（从发布网关显式传递） */
    private Long chainId;

    /** 内容类型：image/text */
    private String contentType;

    /** 内容数据（base64或文本）- 与 minioPath 二选一 */
    private String data;

    /**
     * MinIO 对象路径（如 images/2026/03/20/uuid.jpg）
     * 与 data 二选一：优先使用此字段
     */
    private String minioPath;

    /** 文件名 */
    private String fileName;

    /** 描述 */
    private String description;

    /** 缩略图 */
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

    /** 时间戳 */
    private Long timestamp;   
}
