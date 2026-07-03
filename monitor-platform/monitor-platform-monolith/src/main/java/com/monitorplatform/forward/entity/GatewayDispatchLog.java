package com.monitorplatform.forward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关配置下发记录实体
 */
@Data
@TableName("gateway_dispatch_log")
public class GatewayDispatchLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 链路ID
     */
    private Long chainId;

    /**
     * 链路编码
     */
    private String chainCode;

    /**
     * 分支编码（B1/B2/B3...）
     */
    private String branchCode;

    /**
     * 发布加密网关设备ID
     */
    private String gatewayDeviceId;

    /**
     * 网关IP地址
     */
    private String gatewayIp;

    /**
     * 网关端口
     */
    private Integer gatewayPort;

    /**
     * 配置版本号
     */
    private Integer configVersion;

    /**
     * 下发内容JSON
     */
    private String dispatchContent;

    /**
     * 下发状态: 0-待下发, 1-已下发待确认, 2-已确认生效, 3-下发失败, 4-已过期
     */
    private Integer dispatchStatus;

    /**
     * 当前重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime nextRetryTime;

    /**
     * 网关回执码
     */
    private String ackCode;

    /**
     * 网关回执信息
     */
    private String ackMessage;

    /**
     * 下发时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dispatchTime;

    /**
     * 确认时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime ackTime;

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

    // ===== 下发状态常量 =====
    
    public static final int STATUS_PENDING = 0;          // 待下发
    public static final int STATUS_DISPATCHED = 1;       // 已下发待确认
    public static final int STATUS_CONFIRMED = 2;        // 已确认生效
    public static final int STATUS_FAILED = 3;           // 下发失败
    public static final int STATUS_EXPIRED = 4;          // 已过期
}
