package com.gateway.device.protocol.api;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;

/**
 * 能力处理器 —— 单一设备能力的执行逻辑。
 *
 * <p>每个 {@link DeviceCapability} 对应一个实现类，由厂商适配器注册，
 * 执行时通过 DeviceCapability 查表分发。</p>
 *
 * @param <P> 本能力对应的参数类型，由 {@link #capability()} 返回值编译期约束
 */
public interface CapabilityHandler<P extends CommandParams> {

    /**
     * 本处理器对应的能力（已携带参数类型 P，调用方无需额外声明类型）
     */
    DeviceCapability<P> capability();

    /**
     * 执行该能力，返回统一结果。
     *
     * @param params 类型安全的参数对象，编译期由 DeviceCapability<P> 保证类型正确
     */
    CommandResult execute(DeviceContext device, P params);
}
