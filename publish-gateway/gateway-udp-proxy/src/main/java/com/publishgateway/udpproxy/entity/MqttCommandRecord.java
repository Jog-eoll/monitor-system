package com.publishgateway.udpproxy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MQTT 命令幂等记录 —— 持久化到本地 MySQL，防止容器重启后重复 messageId 防重失效。
 */
@Data
@TableName("mqtt_command_record")
public class MqttCommandRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private String messageId;

    private String command;

    @TableField("first_seen_at")
    private LocalDateTime firstSeenAt;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
