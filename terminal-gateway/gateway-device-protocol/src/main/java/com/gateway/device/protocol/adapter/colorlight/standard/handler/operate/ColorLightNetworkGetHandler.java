package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.NetworkInfo;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 网络配置查询 —— GET /api/network.json。
 */
@Slf4j
public class ColorLightNetworkGetHandler extends AbstractColorLightHttpHandler<EmptyParams> {

    public ColorLightNetworkGetHandler(DeviceTransport transport,
                                       ColorLightCredentialStore credentialStore,
                                       ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.DEVICE_NETWORK_IP_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        ColorLightHttpResponse resp = send(device, ColorLightApi.NETWORK_GET);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_NET_GET_FAIL", "Failed to get network config");
        }
        NetworkInfo info = parseJson(resp, NetworkInfo.class);
        return successResult(info);
    }
}
