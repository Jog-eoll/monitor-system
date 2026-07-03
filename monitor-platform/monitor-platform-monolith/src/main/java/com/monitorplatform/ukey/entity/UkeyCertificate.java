package com.monitorplatform.ukey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UKey证书管理实体
 */
@Data
@TableName("ukey_certificate")
public class UkeyCertificate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 证书唯一编号（对应 UKey 中的 cerId/cerSn） */
    private String certSerialNo;

    /** 证书内容（PEM/DER 格式） */
    private String certificateContent;

    /** 证书生效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    /** 证书过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;

    /** 加密算法（SM2/RSA/SM9） */
    private String cryptoAlgorithm;

    /** 绑定的监管客户端ID */
    private String boundClientId;

    /** 证书状态：PENDING=待审核, NORMAL=正常, REVOKED=注销, LOST=挂失 */
    private String certStatus;

    /** 证书颁发机构 */
    private String issuer;

    /** 证书主体信息 */
    private String subject;

    /** UKey显示名称（在登录页展示，如"监管站-A号UKey"） */
    private String displayName;

    /** UKey PIN码哈希（SHA-256 + 随机盐，用于登录校验，不存明文） */
    private String pinHash;

    /** PIN码盐值（随机生成，用于防止彩虹表攻击） */
    private String pinSalt;

    /** PIN码最后更新时间（用于判断密码是否过期，可配置<=90天） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pinUpdateTime;

    /** 备注 */
    private String remark;

    /** 在线状态：ONLINE=已认证在线, OFFLINE=离线 */
    private String onlineStatus;

    /** 最近一次认证成功时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAuthTime;

    /** 最近一次离线时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastOfflineTime;

    /** 客户端最近一次心跳时间（用于判断客户端进程是否存活） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatTime;

    /** 客户端最近上报的 IP 地址 */
    private String clientIp;

    /** 录入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
