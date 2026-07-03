package com.gateway.device.protocol.model.params.depend;

/**
 * 设备指令参数标记接口 —— 每个 {@code DeviceCapability} 对应一个具体的实现类。
 *
 * <p>替代 {@code Map<String, Object> params}，调用方通过
 * {@code DeviceCapability.paramsType()} 获知应构造哪类参数对象。</p>
 */
public interface CommandParams {
}
