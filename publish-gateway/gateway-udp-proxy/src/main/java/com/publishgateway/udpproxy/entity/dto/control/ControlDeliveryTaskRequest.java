package com.publishgateway.udpproxy.entity.dto.control;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Map;

/**
 * 控制任务投递请求 —— 客户端将加密包投递到加密网关，由加密网关转发至解密网关。
 */
@Data
public class ControlDeliveryTaskRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 控制任务 ID */
    @NotBlank(message = "commandTaskId 不能为空")
    private String commandTaskId;

    /** 加密包 ID（加密步骤返回的 ID） */
    private String encryptedCommandPackageId;

    /** 加密后的控制任务包（Base64 编码） */
    @NotBlank(message = "encryptedCommandPackage 不能为空")
    private String encryptedCommandPackage;

    /** 目标设备信息 */
    private TargetRef target;

    /** 控制指令（如 BRIGHTNESS / POWER） */
    private String command;

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendorHint;
    }
}
