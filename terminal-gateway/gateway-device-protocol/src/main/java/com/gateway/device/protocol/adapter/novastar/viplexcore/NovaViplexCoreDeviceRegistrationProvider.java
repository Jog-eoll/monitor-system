package com.gateway.device.protocol.adapter.novastar.viplexcore;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

/**
 * NovaViplexCore 设备注册信息提供者 —— 独立于 {@code NovaViplexCoreDeviceInfoGetHandler}。
 *
 * <p>调用 nvGetFirmwareInfosAsync 获取固件全量信息，返回 {@link JsonNode}，
 * 供 AutoDiscoveryService 注册 + DeviceInfoEnricher 解析使用。</p>
 */
@Slf4j
public class NovaViplexCoreDeviceRegistrationProvider implements DeviceRegistrationProvider {

    private final ViplexCoreChannel channel;

    public NovaViplexCoreDeviceRegistrationProvider(ViplexCoreChannel channel) {
        this.channel = channel;
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_VIPLEX_CORE;
    }

    /**
     * 支持显式 IP 注册 —— SN 缺失时通过 SDK 搜索广播发现设备。
     */
    @Override
    public boolean supportsExplicitIp() {
        return true;
    }

    @Override
    public Object fetchRegistrationInfo(DeviceContext device) {
        String sn = device.getSn();
        String ip = device.getIp();

        // SN 缺失时通过 SDK 广播搜索发现设备 SN（通用前置注册表补充未命中时兜底）
        if (StringUtils.isEmpty(sn)) {
            if (StringUtils.isEmpty(ip)) {
                log.warn("注册信息获取失败: SN 和 IP 均缺失");
                return null;
            }
            sn = discoverSnByIp(ip);
            if (StringUtils.isEmpty(sn)) {
                log.warn("[{}] 注册信息获取失败: SDK 搜索未发现匹配设备", ip);
                return null;
            }
            log.info("[{}] SDK 搜索发现设备 SN={}", ip, sn);
        }

        String json = ViplexCoreJsonBuilder.buildSnJson(sn);
        Duration timeout = Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_DEFAULT_MS);
        try {
            ViplexResponse resp = channel.execute(
                    SdkFunction.NV_GET_FIRMWARE_INFOS_ASYNC, json, timeout);
            if (resp.isTimeout()) {
                log.warn("[{}] 注册信息获取超时 SN={}", device.getIp(), sn);
                return null;
            }
            if (!resp.isSuccess()) {
                log.warn("[{}] 注册信息获取失败 SN={} code={}", device.getIp(), sn, resp.getCode());
                return null;
            }
            JsonNode data = resp.dataAsJson();
            log.info("[{}] 注册信息获取成功 SN={}", device.getIp(), sn);
            return data;
        } catch (Exception e) {
            log.error("[{}] 注册信息获取异常 SN={}", device.getIp(), sn, e);
            return null;
        }
    }

    /**
     * SDK 广播搜索按 IP 匹配设备 SN，仅全新设备手动 IP 注册时兜底调用。
     *
     * @param ip 设备 IP 地址
     * @return 匹配的设备 SN，未找到返回 null
     */
    private String discoverSnByIp(String ip) {
        ViplexResponse resp = channel.searchAllDevices(Duration.ofMillis(GatewayTimeoutConstants.DEVICE_LOGOUT_TIMEOUT_MS));
        if (!resp.isSuccess()) {
            log.debug("[{}] SDK 搜索无响应", ip);
            return null;
        }
        JsonNode result = resp.dataAsJson();
        if (result == null) return null;
        JsonNode devices = result.get("devices");
        if (devices == null || !devices.isArray()) return null;
        for (JsonNode dev : devices) {
            if (ip.equals(dev.get("ip").asText())) {
                String sn = dev.has("sn") ? dev.get("sn").asText() : null;
                if (!StringUtils.isEmpty(sn)) {
                    return sn;
                }
            }
        }
        return null;
    }

    /**
     * 登出设备 —— 调用 nvLogoutAsync 终止 SDK 会话。
     *
     * <p>登出后 SDK 长连接自动失效，本地会话状态（activeSessions、loginFailures、
     * knownAccounts）由 {@code ViplexCoreLifecycleManager.logout} 内部清理。
     * authStore 凭据和 registry 设备条目保留，以便下次自动登录。</p>
     */
    @Override
    public boolean logout(DeviceContext device) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            log.warn("[{}] 登出失败: SN 缺失", device.getIp());
            return false;
        }
        ViplexResponse resp = channel.logout(sn, Duration.ofMillis(GatewayTimeoutConstants.DEVICE_LOGOUT_TIMEOUT_MS));
        if (resp.isSuccess()) {
            log.info("[{}] ViplexCore 登出成功 SN={}", device.getIp(), sn);
            return true;
        }
        log.warn("[{}] ViplexCore 登出失败 SN={} code={} data={}",
                device.getIp(), sn, resp.getCode(), resp.getData());
        return false;
    }
}
