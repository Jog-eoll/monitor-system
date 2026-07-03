package com.monitorplatform.registry.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
public class GatewayRegisterRequest {

    @NotBlank(message = "deviceId cannot be blank")
    private String deviceId;

    @NotBlank(message = "ip cannot be blank")
    private String ip;

    @NotNull(message = "port cannot be null")
    @Min(value = 1, message = "port must be greater than 0")
    @Max(value = 65535, message = "port must be less than 65536")
    private Integer port;

    @NotBlank(message = "role cannot be blank")
    private String role;

    @NotBlank(message = "certSerialNo cannot be blank")
    private String certSerialNo;

    @NotBlank(message = "ukeySn cannot be blank")
    @JsonAlias({"ukeySN", "uKeySn"})
    private String ukeySn;

    private String certificateContent;
    private String macAddress;
    private String deviceName;
    private String version;
    private String manufacturer;
    private String model;
    private Map<String, Object> capabilities;

    public void setHost(String host) {
        this.ip = host;
    }

    public void setIpAddress(String ipAddress) {
        this.ip = ipAddress;
    }

    public void setMac(String mac) {
        this.macAddress = mac;
    }

    public void setUkeySn(String ukeySn) {
        this.ukeySn = ukeySn;
    }
}
