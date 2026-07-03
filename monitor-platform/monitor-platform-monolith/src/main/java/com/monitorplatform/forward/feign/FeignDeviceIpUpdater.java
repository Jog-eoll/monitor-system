package com.monitorplatform.forward.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Service
public class FeignDeviceIpUpdater implements DeviceIpUpdater {

    @Resource
    private DeviceFeignClient deviceFeignClient;

    @Override
    public boolean updateDeviceIp(String deviceId, String newIp) {
        try {
            Map<String, Object> response = deviceFeignClient.updateDeviceIp(deviceId, newIp);
            if (response != null && Integer.valueOf(200).equals(response.get("code"))) {
                return true;
            }
            log.error("[FeignDeviceIpUpdater] device service returned failure. deviceId={}, newIp={}, response={}",
                    deviceId, newIp, response);
            return false;
        } catch (Exception e) {
            log.error("[FeignDeviceIpUpdater] failed to update device IP. deviceId={}, newIp={}", deviceId, newIp, e);
            return false;
        }
    }
}
