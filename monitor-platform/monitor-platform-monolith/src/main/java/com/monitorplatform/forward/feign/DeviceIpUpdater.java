package com.monitorplatform.forward.feign;

public interface DeviceIpUpdater {

    boolean updateDeviceIp(String deviceId, String newIp);
}
