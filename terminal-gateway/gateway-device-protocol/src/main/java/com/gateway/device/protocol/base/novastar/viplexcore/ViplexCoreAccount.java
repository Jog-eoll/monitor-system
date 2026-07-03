package com.gateway.device.protocol.base.novastar.viplexcore;

import lombok.Builder;
import lombok.Getter;

/**
 * ViplexCore SDK 登录账号配置（不可变，通过 {@link #builder()} 构造）。
 */
@Getter
@Builder
public class ViplexCoreAccount {

    /**
     * 默认账号
     */
    public static final ViplexCoreAccount DEFAULT = ViplexCoreAccount.builder()
            .accountId("admin").password("SN2008@+").build();

    private final String accountId;
    private final String password;

    /**
     * 判断与给定凭据是否匹配（用于检测已登录 session 与请求凭据是否一致）。
     */
    public boolean credentialsMatch(String accountId, String password) {
        return this.accountId != null && this.accountId.equals(accountId)
                && this.password != null && this.password.equals(password);
    }
}
