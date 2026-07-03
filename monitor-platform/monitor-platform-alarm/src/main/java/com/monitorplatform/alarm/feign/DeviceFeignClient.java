package com.monitorplatform.alarm.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Device 服务 Feign 客户端
 * 用于更新情报板的状态和查询经纬度
 */
@FeignClient(name = "monitor-device")
public interface DeviceFeignClient {

    /**
     * 通过 IP 地址更新情报板状态
     * 对应 device 服务：PUT /device/unified/status-by-ip?ip=xxx&status=xxx
     *
     * @param ip     情报板 IP
     * @param status 目标状态（"告警" / "在线"）
     * @return { code, msg, data }
     */
    @PutMapping("/device/unified/status-by-ip")
    Map<String, Object> updateStatusByIp(@RequestParam("ip") String ip,
                                         @RequestParam("status") String status);

    /**
     * 通过 IP 查询情报板经纬度
     * 对应 device 服务：GET /device/unified/location-by-ip?ip=xxx
     *
     * @param ip 情报板 IP
     * @return { code, msg, data: { longitude, latitude } }
     */
    @GetMapping("/device/unified/location-by-ip")
    Map<String, Object> getLocationByIp(@RequestParam("ip") String ip);
}
