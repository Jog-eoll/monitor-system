package com.monitorplatform.registry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gateway_deploy_log")
public class GatewayDeployLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String sourceType;
    private String logLevel;
    private String message;
    private String contextJson;
    private String clientIp;
    private LocalDateTime reportTime;
    private LocalDateTime createTime;
}
