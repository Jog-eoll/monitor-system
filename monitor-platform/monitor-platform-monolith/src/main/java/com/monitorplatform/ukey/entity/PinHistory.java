package com.monitorplatform.ukey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PIN码历史记录实体
 * 用于实现历史密码限制（禁止使用最近5次密码）
 */
@Data
@TableName("pin_history")
public class PinHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的证书序列号 */
    private String certSerialNo;

    /** 历史PIN码哈希值 */
    private String pinHash;

    /** PIN码盐值 */
    private String pinSalt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
