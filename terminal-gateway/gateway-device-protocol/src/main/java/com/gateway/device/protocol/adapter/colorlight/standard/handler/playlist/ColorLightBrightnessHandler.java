package com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;

import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.BrightnessSetPayload;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.BrightnessSetParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 亮度调节 —— PUT /api/brightness。
 * <p>ratio (0-100) → brightness (0-255)</p>
 */
@Slf4j
public class ColorLightBrightnessHandler extends AbstractColorLightHttpHandler<BrightnessSetParams> {

    public ColorLightBrightnessHandler(DeviceTransport transport,
                                       ColorLightCredentialStore credentialStore,
                                       ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<BrightnessSetParams> capability() {
        return CommonDeviceCapability.BRIGHTNESS_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, BrightnessSetParams params) {
        int brightness = params.getRatio() * 255 / 100;
        BrightnessSetPayload payload = BrightnessSetPayload.builder()
                .brightness(brightness).build();
        ColorLightHttpResponse resp = send(device, ColorLightApi.BRIGHTNESS, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_BRIGHT_FAIL", "Failed to set brightness");
        }
        return successResult();
    }
}
