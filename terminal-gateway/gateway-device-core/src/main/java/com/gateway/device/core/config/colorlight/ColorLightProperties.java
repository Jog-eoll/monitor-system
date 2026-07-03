package com.gateway.device.core.config.colorlight;

import com.gateway.device.protocol.base.colorlight.standard.ColorLightAccount;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ColorLight HTTP 协议配置属性。
 *
 * <p>配置前缀: {@code device.colorlight}</p>
 *
 * <h3>yaml 示例</h3>
 * <pre>
 * device:
 *   colorlight:
 *     accounts:
 *       - accountId: admin
 *         password: console
 *       - accountId: custom
 *         password: custom123
 * </pre>
 */
@Data
@ConfigurationProperties("device.colorlight")
public class ColorLightProperties {

    /**
     * 登录账号/密码预置列表（全局默认按顺序尝试）。
     *
     * <p>使用 {@link AccountProperties} 作为可绑定中间层，
     * 通过 {@link #toColorLightAccounts()} 转换为不可变的 {@link ColorLightAccount} 列表。</p>
     */
    private List<AccountProperties> accounts = Collections.singletonList(
            AccountProperties.from(ColorLightAccount.DEFAULT));

    private volatile List<ColorLightAccount> cachedAccounts;

    /**
     * 转换为不可变 {@link ColorLightAccount} 列表（首次调用后缓存）。
     */
    public List<ColorLightAccount> toColorLightAccounts() {
        if (cachedAccounts == null) {
            cachedAccounts = resolveAccounts();
        }
        return cachedAccounts;
    }

    private List<ColorLightAccount> resolveAccounts() {
        if (CollectionUtils.isEmpty(accounts)) {
            return Collections.singletonList(ColorLightAccount.DEFAULT);
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

        static AccountProperties from(ColorLightAccount account) {
            AccountProperties p = new AccountProperties();
            p.setAccountId(account.getAccountId());
            p.setPassword(account.getPassword());
            return p;
        }

        ColorLightAccount toAccount() {
            return ColorLightAccount.builder()
                    .accountId(accountId)
                    .password(password)
                    .build();
        }
    }
}
