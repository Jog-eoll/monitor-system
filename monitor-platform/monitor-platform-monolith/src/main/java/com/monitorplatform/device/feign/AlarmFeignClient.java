package com.monitorplatform.device.feign;

import com.monitorplatform.alarm.service.AlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 告警服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service("deviceAlarmFeignClient")
public class AlarmFeignClient {

    @Resource
    private AlarmService alarmService;

    /**
     * 查询指定情报板IP是否存在待处理告警
     */
    public Map<String, Object> hasPendingAlarmByIp(String ip) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean hasPending = alarmService.hasPendingAlarmByIp(ip);
            result.put("code", 200);
            result.put("data", hasPending);
        } catch (Exception e) {
            log.error("查询待处理告警失败: ip={}", ip, e);
            result.put("code", 500);
            result.put("msg", "查询失败: " + e.getMessage());
        }
        return result;
    }
}
