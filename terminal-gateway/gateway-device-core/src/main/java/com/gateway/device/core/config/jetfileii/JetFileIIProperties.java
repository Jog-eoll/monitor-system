package com.gateway.device.core.config.jetfileii;

import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIAccount;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JetFileII 协议配置属性。
 *
 * <p>配置前缀: {@code device.jetfileii}</p>
 *
 * <h3>yaml 示例</h3>
 * <pre>
 * device:
 *   jetfileii:
 *     accounts:
 *       - accountId: administrator
 *         password: "123456"
 *       - accountId: custom
 *         password: custom123
 * </pre>
 */
@Data
@ConfigurationProperties("device.jetfileii")
public class JetFileIIProperties {

    /**
     * 登录账号/密码预置列表（全局默认按顺序尝试）。
     *
     * <p>使用 {@link AccountProperties} 作为可绑定中间层，
     * 通过 {@link #toJetFileIIAccounts()} 转换为不可变的 {@link JetFileIIAccount} 列表。</p>
     */
    private List<AccountProperties> accounts = Collections.singletonList(
            AccountProperties.from(JetFileIIAccount.DEFAULT));

    private volatile List<JetFileIIAccount> cachedAccounts;

    /**
     * 转换为不可变 {@link JetFileIIAccount} 列表（首次调用后缓存）。
     */
    public List<JetFileIIAccount> toJetFileIIAccounts() {
        if (cachedAccounts == null) {
            cachedAccounts = resolveAccounts();
        }
        return cachedAccounts;
    }

    private List<JetFileIIAccount> resolveAccounts() {
        if (CollectionUtils.isEmpty(accounts)) {
            return Collections.singletonList(JetFileIIAccount.DEFAULT);
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

        static AccountProperties from(JetFileIIAccount account) {
            AccountProperties p = new AccountProperties();
            p.setAccountId(account.getAccountId());
            p.setPassword(account.getPassword());
            return p;
        }

        JetFileIIAccount toAccount() {
            return JetFileIIAccount.builder()
                    .accountId(accountId)
                    .password(password)
                    .build();
        }
    }
}
