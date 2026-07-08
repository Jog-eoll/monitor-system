package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIAccount;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * JetFileII 厂商域凭据配置 —— 仅保留预置账号列表。
 *
 * <p>预置列表用于设备首次注册时的凭据尝试（厂商域），
 * 设备认证通过后的凭据持久化由 {@code DeviceAuthStore} 统一管理。</p>
 *
 * <p>预置列表通过 {@link #JetFileIICredentialStore(List)} 或 {@link #setPreconfigured(List)} 注入，
 * 由 Spring {@code @Configuration} 从 yaml 配置中读取。</p>
 */
@Slf4j
public class JetFileIICredentialStore {

    /**
     * -- GETTER --
     * 获取预配置凭据列表（不可变视图）。
     * 用于设备注册时密码遍历尝试。
     */
    @Getter
    private volatile List<JetFileIIAccount> preconfigured = Collections.emptyList();

    public JetFileIICredentialStore() {
    }

    /**
     * 带预置凭据列表构造。
     *
     * @param preconfigured 预置凭据列表（按顺序尝试）
     */
    public JetFileIICredentialStore(List<JetFileIIAccount> preconfigured) {
        setPreconfigured(preconfigured);
    }

    /**
     * 按 deviceId 获取凭据 —— 始终回退到预置列表首项。
     *
     * @param deviceId 设备唯一标识（当前仅用于日志上下文）
     * @return 预置首项或出厂默认
     */
    public JetFileIIAccount get(String deviceId) {
        return fallbackToPreconfiguredOrDefault();
    }

    /**
     * 设置预置凭据列表（线程安全，适合 Spring setter 注入或运行时更新）。
     */
    public void setPreconfigured(List<JetFileIIAccount> preconfigured) {
        this.preconfigured = preconfigured != null && !preconfigured.isEmpty()
                ? Collections.unmodifiableList(preconfigured)
                : Collections.emptyList();
        log.debug("JetFileII CredentialStore preconfigured accounts: {}", this.preconfigured.size());
    }

    private JetFileIIAccount fallbackToPreconfiguredOrDefault() {
        if (!preconfigured.isEmpty()) {
            return preconfigured.get(0);
        }
        return JetFileIIAccount.DEFAULT;
    }
}
