package com.monitorplatform.alarm.feign;

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
@Service("alarmDeviceFeignClient")
public class DeviceFeignClient {

    @Resource
    private UnifiedDeviceService unifiedDeviceService;

    /**
     * 通过 IP 地址更新情报板状态
     */
    public Map<String, Object> updateStatusByIp(String ip, String status) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean ok = unifiedDeviceService.updateStatusByIp(ip, status);
            result.put("code", ok ? 200 : 404);
            result.put("msg", ok ? "状态更新成功" : "未找到情报板设备: ip=" + ip);
        } catch (Exception e) {
            log.error("更新情报板状态失败: ip={}, status={}", ip, status, e);
            result.put("code", 500);
            result.put("msg", "更新状态失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 通过 IP 查询情报板经纬度
     */
    public Map<String, Object> getLocationByIp(String ip) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> location = unifiedDeviceService.getInfoBoardLocationByIp(ip);
            result.put("code", 200);
            result.put("data", location);
        } catch (Exception e) {
            log.error("查询情报板经纬度失败: ip={}", ip, e);
            result.put("code", 500);
            result.put("msg", "查询经纬度失败: " + e.getMessage());
        }
        return result;
    }
}
