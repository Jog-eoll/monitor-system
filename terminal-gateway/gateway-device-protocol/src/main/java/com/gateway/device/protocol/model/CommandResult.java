package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CommandResult {

    private boolean success;

    private String code;

    private String message;

    private Object data;

    private long costMillis;

    public static CommandResult success() {
        return CommandResult.builder()
                .success(true)
                .code(StandardErrorCode.SUCCESS)
                .message("success")
                .build();
    }

    public static CommandResult success(Object data) {
        return CommandResult.builder()
                .success(true)
                .code(StandardErrorCode.SUCCESS)
                .message("success")
                .data(data)
                .build();
    }

    public static CommandResult failure(String code, String message) {
        return CommandResult.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    public static CommandResult timeout() {
        return failure(StandardErrorCode.TIMEOUT, "设备响应超时");
    }

    public static CommandResult transportError(String detail) {
        return failure(StandardErrorCode.TRANSPORT_ERROR, detail);
    }

    public static CommandResult unsupported(DeviceCapability<?> cap) {
        return failure(StandardErrorCode.UNSUPPORTED_CAPABILITY,
                "设备不支持能力: " + (cap != null ? cap.name() : "null"));
    }
}
