package com.monitorplatform.forward.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQTT 命令下发记录实体
 * <p>
 * 记录平台通过 MQTT 下发给现场网关的每一条命令，用于跟踪命令生命周期
 * （创建 → 已发布 → 已接收 → 执行中 → 成功/失败/超时/取消）。
 * </p>
 */
@Data
@TableName("device_mqtt_command")
public class DeviceMqttCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息ID（UUID，与 MqttEnvelope.messageId 一致，用于幂等和回执关联）
     */
    private String messageId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 站点ID
     */
    private String siteId;

    /**
     * 网关设备ID（命令下发的目标网关）
     */
    private String gatewayDeviceId;

    /**
     * 目标设备ID（命令最终作用的设备，可为网关自身）
     */
    private String targetDeviceId;

    /**
     * 命令名称：APPLY_CHAIN_CONFIG / STOP_CHAIN / SET_CHAIN_STATUS 等
     */
    private String command;

    /**
     * 消息类型：PROXY_COMMAND
     */
    private String messageType;

    /**
     * MQTT Topic（下发时使用的完整 Topic）
     */
    private String topic;

    /**
     * 消息体 JSON（MqttCommandMessage 序列化后的 JSON 字符串）
     */
    private String payloadJson;

    /**
     * 命令状态：CREATED / PUBLISHED / RECEIVED / PROCESSING / SUCCESS / FAILED / TIMEOUT / CANCELED
     */
    private String status;

    /**
     * QoS 级别（0/1/2）
     */
    private Integer qos;

    /**
     * 当前重试次数
     */
    private Integer retryCount;

    /**
     * 超时时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timeoutAt;

    /**
     * 发布时间（MQTT 发送成功的时间）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedAt;

    /**
     * 回执时间（收到网关回执的时间）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime ackTime;

    /**
     * 错误信息（执行失败或超时时的描述）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 命令状态常量 =====

    /** 已创建（尚未发布） */
    public static final String STATUS_CREATED = "CREATED";

    /** 已发布（MQTT 发送成功） */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 已接收（网关已确认收到命令） */
    public static final String STATUS_RECEIVED = "RECEIVED";

    /** 执行中（网关正在处理命令） */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 执行成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 执行失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 超时（超过 commandTimeoutSec 未收到回执） */
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    /** 已取消 */
    public static final String STATUS_CANCELED = "CANCELED";

    /**
     * 构造时初始化 retryCount 默认值
     */
    public DeviceMqttCommand() {
        this.retryCount = 0;
    }
}
