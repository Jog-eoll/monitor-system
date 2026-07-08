package com.infopublish.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 客户端心跳定时任务
 *
 * <p>每隔 5 秒向管控平台上报一次心跳，使管控平台得知本服务进程存活。
 * 管控平台根据最近一次心跳时间判断客户端是否在线，超过阈值（默认 15 秒）未收到
 * 心跳则认为客户端已宕机，登录界面将展示"客户端服务不可用"的错误提示。
 *
 * <p>只在 UKey 已完成认证（{@code isAuthenticated()}）时才上报，未认证时静默跳过。
 */
@Slf4j
@Component
public class ClientHeartbeatTask {

    @Resource
    private UkeyLifecycleManager lifecycleManager;

    @Resource
    private MonitorPlatformClient monitorPlatformClient;

    /**
     * 每 5 秒执行一次，上报心跳到管控平台
     * fixedDelay 保证上一次执行完成后再等 5 秒，避免并发堆积
     */
    @Scheduled(fixedDelay = 5000)
    public void sendHeartbeat() {
        // 只有 UKey 已认证才有意义上报心跳
        if (!lifecycleManager.isAuthenticated()) {
            return;
        }

        String certSerialNo = lifecycleManager.getCurrentCertSerialNo();
        if (certSerialNo == null || certSerialNo.isEmpty()) {
            return;
        }

        monitorPlatformClient.sendHeartbeat(certSerialNo);
    }
}