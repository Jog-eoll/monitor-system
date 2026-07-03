package com.gateway.device.protocol.common;

import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * 设备上下文校验工具 —— 确保单一可寻址设备，拒绝批量/广播。
 */
public final class DeviceValidator {

    private DeviceValidator() {
    }

    /**
     * 严格校验：参数 {@code expectedDeviceId} 必须与参考上下文的 deviceId/SN/MAC 之一匹配。
     *
     * @param reference        参考设备上下文（来自注册表，视为可信源）
     * @param expectedDeviceId 参数中声明的目标设备 ID（必填）
     * @return left=是否通过，right=未通过的异常（通过时为 null）
     */
    public static ImmutablePair<Boolean, CommandResult> requireSingleDevice(
            DeviceContext reference, String expectedDeviceId) {
        if (reference == null) {
            return ImmutablePair.of(false,
                    CommandResult.failure(StandardErrorCode.INVALID_PARAM, "参考设备上下文缺失"));
        }
        if (StringUtils.isBlank(expectedDeviceId)) {
            return ImmutablePair.of(false,
                    CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            "参数 deviceId 缺失，必须指定单一目标设备"));
        }
        boolean matched = expectedDeviceId.equals(reference.getDeviceId())
                || expectedDeviceId.equals(reference.getSn())
                || expectedDeviceId.equals(reference.getMacAddr());
        if (!matched) {
            return ImmutablePair.of(false,
                    CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            String.format("设备不匹配，参数 deviceId: %s，参考 deviceId: %s，SN: %s，MAC: %s",
                                    expectedDeviceId,
                                    reference.getDeviceId(),
                                    reference.getSn(),
                                    reference.getMacAddr())));
        }
        return ImmutablePair.of(true, null);
    }
}
