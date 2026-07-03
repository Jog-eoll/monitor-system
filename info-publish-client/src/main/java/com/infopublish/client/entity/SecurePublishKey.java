package com.infopublish.client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("secure_publish_key")
public class SecurePublishKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("key_id")
    private String keyId;

    @TableField("key_role")
    private String keyRole;

    private String algorithm;

    @TableField("private_key_pem")
    private String privateKeyPem;

    @TableField("public_key_pem")
    private String publicKeyPem;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
