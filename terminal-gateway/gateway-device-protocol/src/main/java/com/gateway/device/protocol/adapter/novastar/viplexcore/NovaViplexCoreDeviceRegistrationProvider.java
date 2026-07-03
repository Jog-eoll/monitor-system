package com.gateway.device.protocol.adapter.novastar.viplexcore;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
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
     * SDK 通道依赖 SN 查找设备，不支持裸 IP 直连注册。
     */
    @Override
    public boolean supportsExplicitIp() {
        return false;
    }

    @Override
    public Object fetchRegistrationInfo(DeviceContext device) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            log.warn("[{}] 注册信息获取失败: SN 缺失", device.getIp());
            return null;
        }

        String json = ViplexCoreJsonBuilder.buildSnJson(sn);
        Duration timeout = Duration.ofSeconds(10);
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
}
