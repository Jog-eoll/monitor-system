package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;

import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.ActionPayload;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 休眠控制 —— POST /api/action {"command":"sleep"}。
 */
@Slf4j
public class ColorLightSleepHandler extends AbstractColorLightHttpHandler<EmptyParams> {

    public ColorLightSleepHandler(DeviceTransport transport,
                                  ColorLightCredentialStore credentialStore,
                                  ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.POWER_CONTROL_SLEEP;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        ActionPayload payload = ActionPayload.builder().command("sleep").build();
        ColorLightHttpResponse resp = send(device, ColorLightApi.ACTION, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_SLEEP_FAIL", "Failed to set screen sleep");
        }
        return successResult();
    }
}
