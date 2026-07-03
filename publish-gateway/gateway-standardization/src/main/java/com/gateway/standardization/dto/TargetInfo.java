package com.gateway.standardization.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 目标设备信息（结构化对象）
 * <p>
 * 对应 Sigma 对接文档中加密端需补齐的 target 字段。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标设备标识 */
    private String deviceId;

    /** 目标设备 IP */
    private String ip;

    /** 目标设备端口 */
    private Integer port;
}
