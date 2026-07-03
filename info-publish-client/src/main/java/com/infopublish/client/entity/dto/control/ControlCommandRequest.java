package com.infopublish.client.entity.dto.control;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;

/**
 * 控制指令执行请求 —— Sigma 客户端下发控制指令的入口 DTO。
 * <p>
 * 支持的 command 白名单：
 * QUERY_STATUS、BRIGHTNESS、BLACKOUT、REBOOT、TIME_SYNC、NTP_SET、DEVICE_IP_SET、SCREEN_ATTRIBUTE_SET
 * </p>
 */
@Data
public class ControlCommandRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求 ID（幂等键） */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** 操作人 ID */
    @NotBlank(message = "operatorId 不能为空")
    private String operatorId;

    /** 目标设备 */
    @NotNull(message = "target 不能为空")
    private TargetRef target;

    /** 控制指令（英文大写），如 QUERY_STATUS / BRIGHTNESS / BLACKOUT / REBOOT / TIME_SYNC / NTP_SET / DEVICE_IP_SET / SCREEN_ATTRIBUTE_SET */
    @NotBlank(message = "command 不能为空")
    private String command;

    /** 指令参数 */
    private Map<String, Object> params;

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendorHint;
    }
}
