package com.gateway.device.core.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 标准化控制包 —— 加密网关加密投递、解密网关解密后解析的控制指令信封。
 * <p>
 * 明文结构（加密前）：
 * <pre>
 * {
 *   "taskId": "CMD-xxx",
 *   "action": "CONTROL_SCREEN",
 *   "source": { "clientId": "...", "operatorId": "..." },
 *   "target": { "deviceId": "...", "ip": "...", "port": 8093, "vendorHint": "QINGSONG" },
 *   "command": "BRIGHTNESS",
 *   "params": { "brightness": 80 }
 * }
 * </pre>
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StandardizedControlPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户端生成的控制任务 ID */
    private String taskId;

    /** 动作类型，固定值 CONTROL_SCREEN */
    private String action;

    /** 来源信息 */
    private SourceInfo source;

    /** 目标设备 */
    private TargetInfo target;

    /** 控制指令（英文大写）：BRIGHTNESS / POWER / BLACKOUT / TIME_SYNC / QUERY_STATUS / REBOOT */
    private String command;

    /** 指令参数 */
    private Map<String, Object> params;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String clientId;
        private String clientIp;
        private String hostName;
        private String operatorId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TargetInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        /** 可选厂商提示，解密网关优先使用设备注册信息，vendorHint 作为兜底 */
        private String vendorHint;
    }
}
