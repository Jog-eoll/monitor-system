package com.gateway.device.protocol.api;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceAuthEntry;
import com.gateway.device.protocol.model.DeviceContext;

/**
 * 设备注册信息提供者 —— 独立于  命令体系。
 *
 * <p>由 {@code AutoDiscoveryService} 在注册阶段直接调用，
 * 各厂商各自实现所需 API 调用编排（与对外查询命令无委托关系）。</p>
 */
public interface DeviceRegistrationProvider {

    /**
     * fetchRegistrationInfo 暂存已验证凭据的属性键。
     * Provider 在获取注册信息时将 {@link DeviceAuthEntry} 放入 device.attributes 此键下，
     * 由 {@link #persistAuth} 在注册流水线末尾统一持久化。
     */
    String ATTR_AUTH_ENTRY = "__authEntry__";

    DeviceVendor vendor();

    /**
     * 获取设备注册所需原始信息，返回 {@code byte[]}（二进制协议）或 {@code JsonNode}（HTTP/SDK 协议）。
     *
     * <p>实现方应将验证通过的凭据以 {@link DeviceAuthEntry} 形式暂存到
     * {@code device.getAttributes().put(ATTR_AUTH_ENTRY, authEntry)}，
     * 由调用方在注册流水线末尾通过 {@link #persistAuth} 统一持久化。</p>
     */
    Object fetchRegistrationInfo(DeviceContext device);

    /**
     * 持久化注册阶段验证的凭据 —— 在注册流水线末尾（合规校验 + enrichment 全部成功后）调用。
     * 默认从 device.attributes 读取 {@link DeviceAuthEntry}，写入 authStore。
     *
     * <p>Provider 无需覆盖此方法，只需在 {@link #fetchRegistrationInfo} 中将构建好的
     * {@link DeviceAuthEntry} 放入 {@code device.attributes[ATTR_AUTH_ENTRY]}。</p>
     */
    default void persistAuth(DeviceContext device, DeviceAuthStore authStore) {
        if (authStore == null || device.getAttributes() == null) return;
        Object entry = device.getAttributes().get(ATTR_AUTH_ENTRY);
        if (entry instanceof DeviceAuthEntry) {
            authStore.update(device.getDeviceId(), (DeviceAuthEntry) entry);
        }
    }

    /**
     * 是否支持显式 IP 注册模式（直接通过 IP:端口连接设备获取注册信息）。
     * SDK 通道等非 IP 直连模式应返回 {@code false}。
     *
     * @return 默认 {@code true}
     */
    default boolean supportsExplicitIp() {
        return true;
    }

    /**
     * 设备登出 —— 仅终止会话，不删除 authStore 凭据或 devices 注册表条目。
     *
     * <p>需要显式登出握手的协议（如 JetFileII）覆盖此方法发送登出指令。
     * 其他协议默认返回 {@code true}（连接断开即视为登出）。</p>
     *
     * <p>登出后调用方通过 {@code DeviceRegistryManager.markLoggedOut} 将设备标记为未登录，
     * 设备条目和已验证凭据保留以便下次自动登录。</p>
     *
     * @param device 待登出的设备上下文
     * @return {@code true} 登出成功，{@code false} 登出失败
     */
    default boolean logout(DeviceContext device) {
        return true;
    }
}
