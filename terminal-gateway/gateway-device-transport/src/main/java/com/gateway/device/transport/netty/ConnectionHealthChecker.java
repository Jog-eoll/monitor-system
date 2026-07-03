package com.gateway.device.transport.netty;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * 连接健康检查 —— 心跳检测 & 重连。
 *
 * <p>使用 IdleStateHandler 触发心跳，本类管理重连退避。</p>
 */
@Slf4j
public class ConnectionHealthChecker {

    private final NettyTransportConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "health-checker");
        t.setDaemon(true);
        return t;
    });

    /**
     * deviceKey → 连续失败次数
     */
    private final ConcurrentMap<String, Integer> failureCounts = new ConcurrentHashMap<>();

    /**
     * deviceKey → 当前重连退避秒数
     */
    private final ConcurrentMap<String, Integer> backoffSec = new ConcurrentHashMap<>();

    /**
     * 离线通知回调
     */
    @Setter
    private Consumer<String> offlineListener;

    public ConnectionHealthChecker(NettyTransportConfig config) {
        this.config = config;
    }

    /**
     * 心跳成功，重置失败计数
     */
    public void markAlive(String deviceKey) {
        failureCounts.remove(deviceKey);
        backoffSec.remove(deviceKey);
    }

    /**
     * 心跳失败，累加计数
     */
    public void markFailure(String deviceKey) {
        int failures = failureCounts.merge(deviceKey, 1, Integer::sum);
        if (failures >= config.getHeartbeatMaxFailures()) {
            log.warn("设备 {} 连续 {} 次心跳失败，标记离线", deviceKey, failures);
            if (offlineListener != null) {
                offlineListener.accept(deviceKey);
            }
        }
    }

    /**
     * 获取重连延迟秒数（exponential backoff: 1→2→4→8...→max）
     */
    public int getReconnectDelaySec(String deviceKey) {
        int current = backoffSec.getOrDefault(deviceKey, 0);
        int next = (current == 0) ? config.getReconnectBaseSec()
                : Math.min(current * 2, config.getReconnectMaxSec());
        backoffSec.put(deviceKey, next);
        return next;
    }

    /**
     * 移除设备追踪状态
     */
    public void remove(String deviceKey) {
        failureCounts.remove(deviceKey);
        backoffSec.remove(deviceKey);
    }

    /**
     * 关停调度器
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
