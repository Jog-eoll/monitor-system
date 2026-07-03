package com.monitorplatform.forward.feign;

import com.monitorplatform.device.entity.dto.DeviceDetailDTO;
import com.monitorplatform.device.entity.dto.DeviceRegisterDTO;
import com.monitorplatform.device.service.DeviceAutoRegisterService;
import com.monitorplatform.device.service.UnifiedDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Device 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service("forwardDeviceFeignClient")
public class DeviceFeignClient {

    @Resource
    private UnifiedDeviceService unifiedDeviceService;

    @Resource
    private DeviceAutoRegisterService deviceAutoRegisterService;

    /**
     * 查询设备详情
     */
    public Map<String, Object> getDeviceDetail(String deviceType, String deviceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            DeviceDetailDTO detail = unifiedDeviceService.getDeviceDetail(deviceType, deviceId);
            if (detail != null && detail.getDeviceId() != null) {
                result.put("code", 200);
                result.put("data", toDetailMap(detail));
            } else {
                result.put("code", 404);
                result.put("msg", "设备不存在");
            }
        } catch (Exception e) {
            log.error("获取设备详情失败: deviceType={}, deviceId={}", deviceType, deviceId, e);
            result.put("code", 500);
            result.put("msg", "获取设备详情失败: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> register(Map<String, Object> register) {
        Map<String, Object> result = new HashMap<>();
        try {
            DeviceRegisterDTO dto = toRegisterDTO(register);
            boolean success = deviceAutoRegisterService.register(dto);
            result.put("code", success ? 200 : 500);
            result.put("msg", success ? "success" : "register failed");
        } catch (Exception e) {
            log.error("设备注册失败: register={}", register, e);
            result.put("code", 500);
            result.put("msg", "设备注册失败: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> heartbeat(String instanceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = deviceAutoRegisterService.heartbeat(instanceId);
            result.put("code", success ? 200 : 500);
            result.put("msg", success ? "success" : "heartbeat failed");
        } catch (Exception e) {
            log.error("设备心跳失败: instanceId={}", instanceId, e);
            result.put("code", 500);
            result.put("msg", "设备心跳失败: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> updateStatus(String instanceId, String status) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = deviceAutoRegisterService.updateDeviceStatus(instanceId, status);
            result.put("code", success ? 200 : 500);
            result.put("msg", success ? "success" : "update status failed");
        } catch (Exception e) {
            log.error("设备状态更新失败: instanceId={}, status={}", instanceId, status, e);
            result.put("code", 500);
            result.put("msg", "设备状态更新失败: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> deregister(String instanceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = deviceAutoRegisterService.deregister(instanceId);
            result.put("code", success ? 200 : 500);
            result.put("msg", success ? "success" : "deregister failed");
        } catch (Exception e) {
            log.error("设备注销失败: instanceId={}", instanceId, e);
            result.put("code", 500);
            result.put("msg", "设备注销失败: " + e.getMessage());
        }
        return result;
    }

    private DeviceRegisterDTO toRegisterDTO(Map<String, Object> register) {
        DeviceRegisterDTO dto = new DeviceRegisterDTO();
        dto.setServiceName(toString(register.get("serviceName")));
        dto.setInstanceId(toString(register.get("instanceId")));
        dto.setHost(toString(register.get("host")));
        dto.setPort(toInteger(register.get("port")));
        dto.setMacAddress(toString(register.get("macAddress")));
        dto.setDeviceType(toString(register.get("deviceType")));
        dto.setLocation(toString(register.get("location")));
        dto.setVersion(toString(register.get("version")));
        dto.setManufacturer(toString(register.get("manufacturer")));
        dto.setModel(toString(register.get("model")));
        dto.setRemark(toString(register.get("remark")));
        return dto;
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    public Map<String, Object> updateDeviceIp(String deviceId, String ip) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = unifiedDeviceService.updateDeviceIp(deviceId, ip);
            result.put("code", success ? 200 : 500);
            result.put("msg", success ? "success" : "update device ip failed");
        } catch (Exception e) {
            log.error("[DeviceFeignClient-local] updateDeviceIp failed. deviceId={}, ip={}", deviceId, ip, e);
            result.put("code", 500);
            result.put("msg", "update device ip failed: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> toDetailMap(DeviceDetailDTO detail) {
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", detail.getDeviceId());
        data.put("deviceName", detail.getDeviceName());
        data.put("deviceType", detail.getDeviceType());
        data.put("deviceTypeLabel", detail.getDeviceTypeLabel());
        data.put("ipAddress", detail.getIpAddress());
        data.put("port", detail.getPort());
        data.put("mac", detail.getMac());
        data.put("status", detail.getStatus());
        data.put("location", detail.getLocation());
        data.put("regionId", detail.getRegionId());
        data.put("longitude", detail.getLongitude());
        data.put("latitude", detail.getLatitude());
        data.put("diskUsage", detail.getDiskUsage());
        data.put("temperature", detail.getTemperature());
        data.put("networkSpeed", detail.getNetworkSpeed());
        data.put("uptime", detail.getUptime());
        data.put("firmwareVersion", detail.getFirmwareVersion());
        data.put("softwareVersion", detail.getSoftwareVersion());
        data.put("manufacturer", detail.getManufacturer());
        data.put("model", detail.getModel());
        data.put("alarmCount", detail.getAlarmCount());
        data.put("lastAlarmTime", detail.getLastAlarmTime());
        data.put("lastAlarmType", detail.getLastAlarmType());
        data.put("lastOnlineTime", detail.getLastOnlineTime());
        data.put("lastHeartbeat", detail.getLastHeartbeat());
        data.put("createTime", detail.getCreateTime());
        data.put("updateTime", detail.getUpdateTime());
        data.put("remark", detail.getRemark());
        data.put("specificAttributes", detail.getSpecificAttributes());
        return data;
    }
}
