package com.monitorplatform.registry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("registry_client_config")
public class ClientConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientId;
    private String serviceName;
    private String configContent;
    private Long configVersion;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
