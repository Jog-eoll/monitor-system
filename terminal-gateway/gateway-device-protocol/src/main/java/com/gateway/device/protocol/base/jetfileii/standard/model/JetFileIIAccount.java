package com.gateway.device.protocol.base.jetfileii.standard.model;

import lombok.Builder;
import lombok.Getter;

/**
 * JetFileII 厂商预置账号配置（不可变，通过 {@link #builder()} 构造）。
 *
 * <p>默认账号为 JetFileII 出厂设置 administrator:123456（抓包确认）。
 * 仅用于设备首次注册时的凭据尝试，认证通过后的持久化由 {@code DeviceAuthStore} 管理。</p>
 */
@Getter
@Builder
public class JetFileIIAccount {

    /**
     * 默认账号（出厂设置，抓包确认）
     */
    public static final JetFileIIAccount DEFAULT = JetFileIIAccount.builder()
            .accountId("administrator").password("123456").build();

    /**
     * 登录账号
     */
    private final String accountId;

    /**
     * 登录密码
     */
    private final String password;

    /**
     * 判断与给定凭据是否匹配。
     *
     * @param accountId 待匹配的账号
     * @param password  待匹配的密码
     * @return true 如果账号和密码均匹配
     */
    public boolean credentialsMatch(String accountId, String password) {
        return this.accountId != null && this.accountId.equals(accountId)
                && this.password != null && this.password.equals(password);
    }
}
