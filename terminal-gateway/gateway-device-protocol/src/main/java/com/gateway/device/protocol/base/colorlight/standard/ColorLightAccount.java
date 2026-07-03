package com.gateway.device.protocol.base.colorlight.standard;

import lombok.Builder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * ColorLight 厂商预置账号配置（不可变，通过 {@link #builder()} 构造）。
 *
 * <p>默认账号为 ColorLight 出厂设置 admin:console。
 * 仅用于设备首次注册时的凭据尝试，认证通过后的持久化由 {@code DeviceAuthStore} 管理。</p>
 */
@Getter
@Builder
public class ColorLightAccount {

    /**
     * 默认账号（出厂设置）
     */
    public static final ColorLightAccount DEFAULT = ColorLightAccount.builder()
            .accountId("admin").password("console").build();

    private final String accountId;
    private final String password;

    /**
     * 生成 HTTP Basic Authorization 头值。
     */
    public String toAuthorizationHeader() {
        if (accountId == null || accountId.isEmpty()) return null;
        String auth = accountId + ":" + (password != null ? password : "");
        return "Basic " + Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 判断与给定凭据是否匹配。
     */
    public boolean credentialsMatch(String accountId, String password) {
        return this.accountId != null && this.accountId.equals(accountId)
                && this.password != null && this.password.equals(password);
    }
}
