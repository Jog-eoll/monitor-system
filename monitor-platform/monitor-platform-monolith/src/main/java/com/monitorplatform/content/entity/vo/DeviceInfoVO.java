package com.monitorplatform.content.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceInfoVO {
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String deviceTypeLabel;
    private String ipAddress;
    private Integer port;
    private String mac;
    private String status;
    private String location;
    private Long regionId;
    private Double longitude;
    private Double latitude;
    private String manufacturer;
    private String version;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime lastOnlineTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
    private String remark;
    private String extra;
}
