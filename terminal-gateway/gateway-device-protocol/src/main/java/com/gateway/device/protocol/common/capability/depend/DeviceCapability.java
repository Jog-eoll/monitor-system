package com.gateway.device.protocol.common.capability.depend;

import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.DeviceCommand;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.DeviceSelector;
import com.gateway.device.protocol.model.params.EmptyParams;
import com.gateway.device.protocol.model.params.depend.CommandParams;

/**
 * 设备能力定义 —— 常量化替代枚举，携带参数类型 P。
 *
 * <p>每个能力一个 {@code DeviceCapability} 实例，通过静态常量引用。
 * 泛型 P 编译期约束调用方传入正确类型的 params。
 * Handler 由各 Adapter 独立维护，避免共享常量被多 Adapter 覆盖。</p>
 *
 * <p>{@code paramsType} 可供 Adapter 在运行时做安全的参数类型转换
 * （{@link Class#cast(Object)}），无需 unchecked cast。</p>
 */
public final class DeviceCapability<P extends CommandParams> {

    private final String name;
    private final Class<P> paramsType;

    private DeviceCapability(String name, Class<P> paramsType) {
        this.name = name;
        this.paramsType = paramsType;
    }

    /**
     * 创建有参能力
     */
    public static <P extends CommandParams> DeviceCapability<P> of(
            String name, Class<P> paramsType) {
        return new DeviceCapability<>(name, paramsType);
    }

    /**
     * 创建无参能力
     */
    public static DeviceCapability<EmptyParams> of(String name) {
        return new DeviceCapability<>(name, EmptyParams.class);
    }

    // ── 入口工厂：收紧 capability 与 params 的绑定 ──

    /**
     * 创建与此能力绑定的批处理请求 —— 编译期保证 params 类型正确。
     *
     * <pre>{@code
     * BatchCommandRequest req = CommonDeviceCapability.TEXT_UPLOAD.request(selector, textParams);
     * // 如果 textParams 不是 TextUploadParams，编译报错
     * }</pre>
     */
    public BatchCommandRequest request(DeviceSelector selector, P params) {
        return BatchCommandRequest.builder()
                .capability(this)
                .selector(selector)
                .params(params)
                .build();
    }

    /**
     * 创建与此能力绑定的设备命令（单设备场景）。
     */
    public DeviceCommand command(DeviceContext target, P params) {
        return DeviceCommand.builder()
                .capability(this)
                .target(target)
                .params(params)
                .build();
    }

    // ── getters ──

    /**
     * 能力标识名
     */
    public String name() {
        return name;
    }

    /**
     * 此能力对应的参数类型 —— 调用方通过此方法获知应构造哪类参数对象。
     * 无参数能力返回 {@link EmptyParams#INSTANCE} 的类型。
     */
    public Class<P> paramsType() {
        return paramsType;
    }

    @Override
    public String toString() {
        return name;
    }
}
