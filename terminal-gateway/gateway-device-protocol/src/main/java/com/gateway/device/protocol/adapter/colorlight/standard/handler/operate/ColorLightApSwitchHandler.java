package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.NetworkTypeEnum;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.NetworkInfo;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ApNetworkSwitchParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.util.List;

/**
 * ColorLight AP 热点开关 —— GET /api/network.json → 修改 "wifi ap" enabled → POST /api/network。
 *
 * <p>WiFi AP 可与 WiFi、LAN、4G 同时使用，仅控制热点的 {@code enabled} 字段（0 关闭 / 1 打开），
 * 不改动其他网络类型的配置。</p>
 */
@Slf4j
public class ColorLightApSwitchHandler extends AbstractColorLightHttpHandler<ApNetworkSwitchParams> {

    public ColorLightApSwitchHandler(DeviceTransport transport,
                                     ColorLightCredentialStore credentialStore,
                                     ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<ApNetworkSwitchParams> capability() {
        return CommonDeviceCapability.DEVICE_NETWORK_AP_SWITCH;
    }

    @Override
    public CommandResult execute(DeviceContext device, ApNetworkSwitchParams params) {
        // 1. GET 当前网络配置
        ColorLightHttpResponse getResp = send(device, ColorLightApi.NETWORK_GET);
        if (getResp == null || getResp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_NET_GET_FAIL", "Failed to get current network config");
        }
        NetworkInfo current = parseJson(getResp, NetworkInfo.class);
        if (current == null || current.getTypes() == null || current.getTypes().isEmpty()) {
            return failureResult("CL_NET_PARSE_FAIL", "Failed to parse network config");
        }

        // 2. 找到 wifi ap 条目并原地修改 enabled（复用 GET 响应的 NetworkInfo 作为 POST 请求体）
        boolean found = false;
        List<NetworkInfo.NetworkType> types = current.getTypes();
        for (NetworkInfo.NetworkType nt : types) {
            if (nt.getType() == NetworkTypeEnum.WIFI_AP) {
                found = true;
                nt.setEnabled(params.isEnable());
                break;
            }
        }

        if (!found) {
            return failureResult("CL_AP_NOT_FOUND", "Network config has no 'wifi ap' entry");
        }

        // 3. POST 新配置
        NetworkInfo payload = NetworkInfo.builder().types(types).build();
        ColorLightHttpResponse postResp = send(device, ColorLightApi.NETWORK, serializeBody(payload));
        if (postResp == null || postResp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_AP_FAIL", "Failed to toggle AP hotspot");
        }

        int enabled = params.isEnable() ? 1 : 0;
        log.info("[{}] AP 热点 {} {}", device.getIp(), enabled == 1 ? "开启" : "关闭", "成功");
        return successResult();
    }
}
