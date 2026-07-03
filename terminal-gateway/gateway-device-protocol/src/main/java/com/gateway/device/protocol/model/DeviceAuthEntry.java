package com.gateway.device.protocol.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 设备认证凭据。
 *
 * <p>以 deviceId 为唯一标识存储于 {@code DeviceAuthStore}。
 * Authorization 头生成优先级: authorization &gt; token &gt; accountId+password</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAuthEntry {

    /**
     * 账号标识（Basic Auth 用户名）
     */
    private String accountId;
    /**
     * 密码
     */
    private String password;
    /**
     * 完整 Authorization 头值（最高优先级，设置后直接透传）
     */
    private String authorization;
    /**
     * Bearer token
     */
    private String token;

    /**
     * 生成 HTTP Authorization 头值。
     *
     * @return Authorization 头值，无法生成时返回 null
     */
    public String toAuthorizationHeader() {
        if (authorization != null && !authorization.isEmpty()) {
            return authorization;
        }
        if (token != null && !token.isEmpty()) {
            return "Bearer " + token;
        }
        if (accountId != null && !accountId.isEmpty()) {
            String auth = accountId + ":" + (password != null ? password : "");
            return "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    /**
     * 凭据匹配（用于去重、变更检测）。
     */
    public boolean credentialsMatch(String accountId, String password) {
        return this.accountId != null && this.accountId.equals(accountId)
                && this.password != null && this.password.equals(password);
    }
}
