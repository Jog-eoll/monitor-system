package com.monitorplatform.ukey.service;

import com.monitorplatform.ukey.entity.PinHistory;
import com.monitorplatform.ukey.util.PasswordValidator;

import java.util.List;

/**
 * 密码策略服务接口
 * 
 * 实现安全规范要求：
 * - 密码复杂度校验（≥8位、字母+数字+特殊字符、无弱口令）
 * - 密码定期更换（可配置≤90天）
 * - 历史密码限制（禁止使用最近5次密码）
 */
public interface PasswordPolicyService {

    /**
     * 校验密码复杂度
     * @param password 明文密码
     * @return 校验结果
     */
    PasswordValidator.ValidationResult validatePasswordStrength(String password);

    /**
     * 检查密码是否过期
     * @param pinUpdateTime PIN码最后更新时间
     * @return true=已过期，false=未过期
     */
    boolean isPasswordExpired(java.time.LocalDateTime pinUpdateTime);

    /**
     * 获取密码剩余有效天数
     * @param pinUpdateTime PIN码最后更新时间
     * @return 剩余天数，负数表示已过期
     */
    int getRemainingValidDays(java.time.LocalDateTime pinUpdateTime);

    /**
     * 检查密码是否在历史记录中（禁止重复使用）
     * @param certSerialNo 证书序列号
     * @param password 明文密码
     * @return true=密码已存在于历史记录，false=可以使用
     */
    boolean isPasswordInHistory(String certSerialNo, String password);

    /**
     * 保存密码到历史记录
     * @param certSerialNo 证书序列号
     * @param pinHash 密码哈希
     * @param pinSalt 密码盐值
     */
    void savePasswordHistory(String certSerialNo, String pinHash, String pinSalt);

    /**
     * 获取密码有效期配置（天数）
     */
    int getPasswordValidDays();

    /**
     * 获取历史密码保留数量配置
     */
    int getHistoryKeepCount();
}
