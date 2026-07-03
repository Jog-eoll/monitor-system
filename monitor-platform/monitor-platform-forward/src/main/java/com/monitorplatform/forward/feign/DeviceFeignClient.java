package com.monitorplatform.forward.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Device 服务 Feign 客户端
 * 用于查询设备详情（如情报板厂家信息）
 */
@FeignClient(name = "monitor-device", url = "${monitor.device.url:}")
public interface DeviceFeignClient {

    /**
     * 查询设备详情
     * 对应 device 服务：GET /device/unified/{deviceType}/{deviceId}/detail
     *
     * @param deviceType 设备类型（如 info_board）
     * @param deviceId   设备唯一标识
     * @return { code, msg, data: { deviceId, manufacturer, model, ... } }
     */
    @GetMapping("/device/unified/{deviceType}/{deviceId}/detail")
    Map<String, Object> getDeviceDetail(@PathVariable("deviceType") String deviceType,
                                        @PathVariable("deviceId") String deviceId);

    /**
     * 设备注册心跳
     * 对应 device 服务：POST /device/registry/heartbeat/{instanceId}
     */
    @PostMapping("/device/registry/heartbeat/{instanceId}")
    Map<String, Object> heartbeat(@PathVariable("instanceId") String instanceId);

    @PostMapping("/device/registry/auto-register")
    Map<String, Object> register(@RequestBody Map<String, Object> register);

    @PutMapping("/device/registry/status/{instanceId}/{status}")
    Map<String, Object> updateStatus(@PathVariable("instanceId") String instanceId,
                                     @PathVariable("status") String status);

    @DeleteMapping("/device/registry/deregister/{instanceId}")
    Map<String, Object> deregister(@PathVariable("instanceId") String instanceId);

    @PutMapping("/device/unified/ip")
    Map<String, Object> updateDeviceIp(@RequestParam("deviceId") String deviceId,
                                       @RequestParam("ip") String ip);
}
