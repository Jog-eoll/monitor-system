package com.monitorplatform.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一设备信息实体
 * 包含所有类型的公共字段，特有字段存储在 extraInfo (JSON) 中
 */
@Data
@TableName("unified_device_info")
public class UnifiedDevice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备唯一标识（如 SN, AppId, CameraId）
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备种类:  publish_server(信息发布服务器), publish_gateway(发布端加密网关), terminal_encrypt_gateway(终端加密网关), content_server(内容识别服务器), info_board(情报板)
     */
    private String deviceType;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * MAC地址（发布网关等设备使用，格式: AA-BB-CC-DD-EE-FF）
     */
    private String mac;

    /**
     * 状态: 在线/离线/告警/故障/正常/异常
     */
    private String status;

    /**
     * 场景/位置
     */
    private String location;

    private Long regionId;

    /**
     * 设备经度
     */
    private Double longitude;

    /**
     * 设备纬度
     */
    private Double latitude;

    /**
     * 版本号（固件或软件版本）
     */
    private String version;

    /**
     * 制造商
     */
    private String manufacturer;

    /**
     * 型号
     */
    private String model;

    /**
     * 最后在线/心跳时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastOnlineTime;

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
     * 备注/描述
     */
    private String remark;

    /**
     * 设备特有属性（JSON格式存储）
     * 例如: 码率、流地址、UKey编号、绑定网关等
     */
    private String extraInfo;
}
