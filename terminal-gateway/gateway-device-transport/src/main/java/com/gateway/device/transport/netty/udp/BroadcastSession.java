package com.gateway.device.transport.netty.udp;

import com.gateway.device.transport.netty.NettyTransportManager.BroadcastResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 单次 UDP 广播收集会话 —— 在超时窗口内收集所有设备响应。
 */
@Slf4j
class BroadcastSession {

    final ConcurrentHashMap<String, byte[]> responses = new ConcurrentHashMap<>();
    final ScheduledFuture<?> timeoutTask;

    BroadcastSession(CompletableFuture<List<BroadcastResponse>> future, Duration timeout,
                     ScheduledExecutorService timeoutScheduler) {
        this.timeoutTask = timeoutScheduler.schedule(() -> {
            List<BroadcastResponse> result = new ArrayList<>();
            responses.forEach((key, data) -> {
                int colon = key.lastIndexOf(':');
                String ip = colon > 0 ? key.substring(0, colon) : key;
                int port;
                try {
                    port = Integer.parseInt(key.substring(colon + 1));
                } catch (NumberFormatException e) {
                    port = 0;
                }
                result.add(new BroadcastResponse(data, ip, port));
            });
            log.debug("广播收集完成: {} 个响应", result.size());
            future.complete(result);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
