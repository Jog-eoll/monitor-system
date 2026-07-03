package com.gateway.device.protocol.api;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceCommand;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 厂商协议适配器 —— 单台设备协议下发与响应解析。
 *
 * <p>每个厂商实现一个适配器，封装该厂商的编解码、命令映射、结果映射。
 * 适配器不管理 Socket/连接，传输委托给 {@code NettyTransportManager}。</p>
 */
public interface VendorProtocolAdapter {

    /**
     * 厂商编码
     */
    DeviceVendor vendor();

    /**
     * 编解码器
     */
    ProtocolCodec<?, ?> codec();

    /**
     * 传输协议类型
     */
    TransportType transportType();

    Map<DeviceCapability<?>, CapabilityHandler<?>> getHandlers();

    Map<DeviceCapability<?>, BiFunction<DeviceContext, CommandParams, CommandResult>> getDispatchers();

    /**
     * 本适配器支持的完整能力集合
     */
    default Set<DeviceCapability<?>> capabilities() {
        return Collections.unmodifiableSet(getHandlers().keySet());
    }

    /**
     * 判断是否支持指定设备 + 能力组合
     */
    default boolean supports(DeviceContext device, DeviceCapability<?> capability) {
        if (!vendor().equals(device.getVendor())) return false;
        if (capability == null) return false;
        return getHandlers().containsKey(capability);
    }

    /**
     * 执行单台设备命令
     */
    default CommandResult execute(DeviceContext device, DeviceCommand command) {
        DeviceCapability<?> cap = command.getCapability();
        BiFunction<DeviceContext, CommandParams, CommandResult> dispatcher = getDispatchers().get(cap);
        if (dispatcher == null) {
            return CommandResult.unsupported(cap);
        }
        return dispatcher.apply(device, command.getParams());
    }

    /**
     * 注册 handler 并创建类型安全的 dispatch 闭包。
     */
    default <P extends CommandParams> void register(CapabilityHandler<P> handler) {
        DeviceCapability<P> cap = handler.capability();
        getHandlers().put(cap, handler);

        BiFunction<DeviceContext, CommandParams, CommandResult> dispatcher = (device, params) -> {
            P typedParams;
            try {
                typedParams = cap.paramsType().cast(params);
            } catch (ClassCastException e) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        String.format("参数类型不匹配: 能力 %s 期望 %s, 实际 %s",
                                cap.name(),
                                cap.paramsType().getSimpleName(),
                                params.getClass().getSimpleName()));
            }
            return handler.execute(device, typedParams);
        };
        getDispatchers().put(cap, dispatcher);
    }
}
