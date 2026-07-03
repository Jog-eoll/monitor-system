package com.monitorplatform.device.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 告警服务 Feign 客户端
 */
@FeignClient(name = "monitor-alarm")
public interface AlarmFeignClient {

    /**
     * 查询指定情报板IP是否存在待处理告警
     */
    @GetMapping("/alarm/has-pending-by-ip")
    Map<String, Object> hasPendingAlarmByIp(@RequestParam("ip") String ip);
}
