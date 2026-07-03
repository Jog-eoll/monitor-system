package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 手动添加情报板 - 请求DTO
 */
@Data
public class InfoBoardManualAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备ID（可选，不传则自动生成） */
    private String deviceId;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    /** 型号编码（如A2K/A4K，用于查映射表获取厂商和默认端口） */
    @NotBlank(message = "型号编码不能为空")
    private String modelCode;

    /** IP地址 */
    @NotBlank(message = "IP地址不能为空")
    private String ipAddress;

    /** 端口号（可选，不传则使用映射表默认端口） */
    private Integer port;

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 位置/场景 */
    private String location;

    /** 解密网关地址（如 http://192.168.1.100:8093） */
    @NotBlank(message = "解密网关地址不能为空")
    private String terminalGatewayUrl;

    /** 加密网关地址（可选，不传则使用当前上下文中的默认加密网关） */
    private String publishGatewayUrl;

    /** 区域ID */
    private Long regionId;

    /** 备注 */
    private String remark;
}
