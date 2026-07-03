package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 设备自动注册请求 DTO
 * 字段与 registry-client 的 ServiceRegisterRequest 保持一致
 */
@Data
public class DeviceRegisterDTO {

    @NotBlank(message = "服务名称不能为空")
    private String serviceName;

    @NotBlank(message = "实例ID不能为空")
    private String instanceId;

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    private Integer port;

    private String macAddress;

    private String deviceType;

    // 新增字段（客户端 yml 配置传入）
    private String location;

    private String version;

    private String manufacturer;

    private String model;

    private String remark;
}
