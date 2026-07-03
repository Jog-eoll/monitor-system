package com.monitorplatform.alarm.websocket;

import com.monitorplatform.alarm.mapper.AlarmMapper;
import com.monitorplatform.common.websocket.WebSocketPushConstants;
import com.monitorplatform.common.websocket.WebSocketPushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警推送服务
 * 负责通过 WebSocket 广播告警标志及今日统计给所有前端客户端。
 *
 * 推送内容：
 * {
 *   "alarmFlag": true,
 *   "timestamp": xxx,
 *   "todayAlarm": { "total": N, "handled": N, "pending": N }
 * }
 *
 * 触发时机：
 *   1. receiveAlarm() 新告警入库后
 *   2. handleAlarm() 处理成功后
 */
@Slf4j
@Service
public class AlarmPushService {

    @Resource
    private WebSocketPushPublisher webSocketPushPublisher;

    @Resource
    private AlarmMapper alarmMapper;

    /**
     * 广播告警标志 + 今日统计，通知前端刷新（异步）
     */
    @Async
    public void pushAlarmFlag() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alarmFlag", true);
            payload.put("timestamp", System.currentTimeMillis());

            // 携带今日统计，前端直接使用，无需再发起额外请求
            try {
                Map<String, Long> stats = buildTodayStats();
                payload.put("todayAlarm", stats);
            } catch (Exception e) {
                log.warn("[AlarmPush] 获取今日统计失败，跳过统计字段：{}", e.getMessage());
            }

            log.info("[AlarmPush] 推送告警标志 + 今日统计");
            webSocketPushPublisher.publish(
                    WebSocketPushConstants.TOPIC_ALARM,
                    WebSocketPushConstants.TYPE_ALARM_FLAG,
                    payload);
        } catch (Exception e) {
            log.warn("[AlarmPush] 推送告警标志失败（不影响主流程）: error={}", e.getMessage());
        }
    }

    /**
     * 构建今日统计数据（直接从 Mapper 取数，避免循环依赖）
     */
    private Map<String, Long> buildTodayStats() {
        long total      = nvl(alarmMapper.countToday());
        long handled    = nvl(alarmMapper.countHandledToday());
        long falseAlarm = nvl(alarmMapper.countFalseAlarmToday());
        long pending    = nvl(alarmMapper.countPendingToday());
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",      total);
        stats.put("handled",    handled);
        stats.put("falseAlarm", falseAlarm);
        stats.put("pending",    pending);
        return stats;
    }

    /** null 安全转 long */
    private long nvl(Long v) { return v == null ? 0L : v; }
}
