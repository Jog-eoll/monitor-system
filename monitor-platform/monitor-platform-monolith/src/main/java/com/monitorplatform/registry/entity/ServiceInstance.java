package com.monitorplatform.registry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("unified_device_info")
public class ServiceInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    private Integer port;
    private String mac;
    private String status;
    private String location;
    private Long regionId;
    private Double longitude;
    private Double latitude;
    private String version;
    private String manufacturer;
    private String model;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
    private String extraInfo;

    public String getClientId() {
        return deviceId;
    }

    public void setClientId(String clientId) {
        this.deviceId = clientId;
    }

    public String getInstanceId() {
        return deviceId;
    }

    public void setInstanceId(String instanceId) {
        this.deviceId = instanceId;
    }

    public String getServiceName() {
        return deviceType;
    }

    public void setServiceName(String serviceName) {
        this.deviceType = serviceName;
    }

    public String getHost() {
        return ipAddress;
    }

    public void setHost(String host) {
        this.ipAddress = host;
    }

    public String getMacAddress() {
        return mac;
    }

    public void setMacAddress(String macAddress) {
        this.mac = macAddress;
    }

    public LocalDateTime getRegisterTime() {
        return createTime;
    }

    public void setRegisterTime(LocalDateTime registerTime) {
        this.createTime = registerTime;
    }

    public LocalDateTime getLastHeartbeatTime() {
        return lastOnlineTime;
    }

    public void setLastHeartbeatTime(LocalDateTime lastHeartbeatTime) {
        this.lastOnlineTime = lastHeartbeatTime;
    }

    public Integer getWeight() {
        return 1;
    }

    public void setWeight(Integer weight) {
        // unified_device_info has no weight column. Keep this setter for API compatibility.
    }

    public String getClusterName() {
        return "default";
    }

    public void setClusterName(String clusterName) {
        // unified_device_info has no cluster column. Keep this setter for API compatibility.
    }
}
