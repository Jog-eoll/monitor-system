package com.monitorplatform.forward.service;

import com.monitorplatform.forward.feign.DeviceIpUpdater;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class IpChangeCallbackService {

    @Value("${ip-change.callback.enabled:true}")
    private boolean callbackEnabled;

    @Resource
    private DeviceIpUpdater deviceIpUpdater;

    @Resource
    private LinkNodeIpUpdater linkNodeIpUpdater;

    public IpChangeCallbackResult onIpSuccess(String deviceId, String newIp) {
        if (!callbackEnabled) {
            log.info("[IpChangeCallback] callback disabled, skip. deviceId={}", deviceId);
            return IpChangeCallbackResult.skipped();
        }

        log.info("[IpChangeCallback] IP changed successfully, updating DB. deviceId={}, newIp={}", deviceId, newIp);

        boolean deviceUpdated = false;
        int linkNodesUpdated = 0;

        try {
            deviceUpdated = deviceIpUpdater.updateDeviceIp(deviceId, newIp);
            log.info("[IpChangeCallback] device table update result: {}", deviceUpdated);
        } catch (Exception e) {
            log.error("[IpChangeCallback] device table update exception. deviceId={}, newIp={}", deviceId, newIp, e);
        }

        try {
            linkNodesUpdated = linkNodeIpUpdater.updateChainNodeIp(deviceId, newIp);
            log.info("[IpChangeCallback] chain node table update count: {}", linkNodesUpdated);
        } catch (Exception e) {
            log.error("[IpChangeCallback] chain node table update exception. deviceId={}, newIp={}", deviceId, newIp, e);
        }

        if (deviceUpdated && linkNodesUpdated >= 0) {
            return IpChangeCallbackResult.success(deviceUpdated, linkNodesUpdated);
        }
        return IpChangeCallbackResult.failure(deviceUpdated, linkNodesUpdated);
    }
}
