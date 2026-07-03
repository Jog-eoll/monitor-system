package com.gateway.device.core.config.novastar;

import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreAccount;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ViplexCore SDK 配置属性。
 *
 * <p>配置前缀: {@code device.novastar.viplexcore}</p>
 *
 * <h3>目录约定</h3>
 * {@code sdkDir} 为 SDK 根目录，其下需有 {@code bin/} 子目录含主库及所有依赖：
 * <ul>
 *   <li>Windows: {@code {sdkDir}/bin/viplexcore.dll}</li>
 *   <li>Linux:   {@code {sdkDir}/bin/libviplexcore.so}</li>
 * </ul>
 */
@Data
@ConfigurationProperties("device.novastar.viplexcore")
public class NovaViplexCoreProperties {

    /**
     * SDK 根目录（包含 bin/ 子目录）
     */
    private String sdkDir;

    /**
     * SDK 数据目录（日志、数据库）
     */
    private String dataDir = "./data/novastar-sdk";

    /**
     * 公司名称（SDK 凭证）
     */
    private String company = "";

    /**
     * 联系电话（SDK 凭证）
     */
    private String phone = "";

    /**
     * 联系邮箱（SDK 凭证）
     */
    private String email = "";

    /**
     * 登录账号/密码列表（按顺序依次尝试登录）。
     *
     * <p>使用 {@link AccountProperties} 作为可绑定中间层，
     * 通过 {@link #toViplexCoreAccounts()} 转换为不可变的 {@link ViplexCoreAccount} 列表。</p>
     */
    private List<AccountProperties> accounts = Collections.singletonList(
            AccountProperties.from(ViplexCoreAccount.DEFAULT));

    /**
     * 转换缓存（配置加载后不变动）
     */
    private volatile List<ViplexCoreAccount> cachedAccounts;

    /**
     * 转换为不可变 {@link ViplexCoreAccount} 列表（首次调用后缓存）
     */
    public List<ViplexCoreAccount> toViplexCoreAccounts() {
        if (cachedAccounts == null) {
            cachedAccounts = resolveViplexCoreAccounts();
        }
        return cachedAccounts;
    }

    private List<ViplexCoreAccount> resolveViplexCoreAccounts() {
        if (CollectionUtils.isEmpty(accounts)) {
            return Collections.singletonList(ViplexCoreAccount.DEFAULT);
        }
        return accounts.stream()
                .map(AccountProperties::toAccount)
                .collect(Collectors.toList());
    }

    /**
     * 可绑定账号中间层（Spring Boot {@code @ConfigurationProperties} 需要 setter）
     */
    @Data
    public static class AccountProperties {
        private String accountId;
        private String password;

        static AccountProperties from(ViplexCoreAccount account) {
            AccountProperties p = new AccountProperties();
            p.setAccountId(account.getAccountId());
            p.setPassword(account.getPassword());
            return p;
        }

        ViplexCoreAccount toAccount() {
            return ViplexCoreAccount.builder()
                    .accountId(accountId)
                    .password(password)
                    .build();
        }
    }
}
