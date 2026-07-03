package com.gateway.device.protocol.adapter.colorlight.standard.handler.info;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 设备信息查询 —— GET /api/info.json。
 *
 * <p>返回 /api/info.json 的完整 JSON 响应，与注册流程独立。</p>
 */
@Slf4j
public class ColorLightDeviceInfoGetHandler extends AbstractColorLightHttpHandler<EmptyParams> {

    public ColorLightDeviceInfoGetHandler(DeviceTransport transport, ColorLightCredentialStore credentialStore, ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.DEVICE_INFO_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        ColorLightHttpResponse resp = send(device, ColorLightApi.DEVICE_INFO);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_HTTP_ERR", "Failed to get /api/info.json");
        }
        JsonNode info = parseJson(resp, JsonNode.class);
        if (info == null) {
            return failureResult("CL_HTTP_ERR", "Failed to parse /api/info.json response");
        }
        return successResult(info);
    }
}
