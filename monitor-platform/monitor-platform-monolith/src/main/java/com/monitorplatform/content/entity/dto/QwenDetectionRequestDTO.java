package com.monitorplatform.content.entity.dto;

import lombok.Data;

/**
 * 阿里云通义千问检测请求DTO
 */
@Data
public class QwenDetectionRequestDTO {
    
    /**
     * 业务唯一标识（设备ID+时间戳）
     */
    private String businessId;
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 截图Base64编码（WebP格式，不含前缀）
     * 与 minioPath 二选一：优先使用 minioPath
     */
    private String screenshotBase64;

    /**
     * MinIO 对象路径（如 images/2026/03/20/uuid.jpg）
     * 与 screenshotBase64 二选一：优先使用此字段
     */
    private String minioPath;
    
    /**
     * 采集时间
     */
    private String captureTime;

    /**
     * 文件名称（可选，前端展示用）
     */
    private String fileName;

    /**
     * 来源IP（可选，前端展示用）
     */
    private String sourceIp;

    /**
     * 描述信息（可选，前端展示用）
     */
    private String description;


    /**
     * 内容类型：image（图片检测）/ video（视频检测，传入的是视频 MinIO 路径）
     * 不传时默认按 image 处理（向后兼容）
     */
    private String contentType;

    /**
     * 链路 ID（可选）
     * 内容所属的任务链路 ID，用于应急处置时关联情报板
     * 由上报方（加密网关/截图服务）在请求时一并传入
     */
    private Long chainId;

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

    /** Original publish request ID; distinct from AI detection requestId. */
    private String publishRequestId;
}
