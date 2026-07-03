package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;

import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.ScreenStatusPayload;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ScreenBlackoutParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 黑屏开关 —— PUT /api/screenstatus。
 * <p>blackout=true → status=0(黑屏), blackout=false → status=1(亮屏)</p>
 */
@Slf4j
public class ColorLightScreenBlackoutHandler extends AbstractColorLightHttpHandler<ScreenBlackoutParams> {

    public ColorLightScreenBlackoutHandler(DeviceTransport transport, ColorLightCredentialStore credentialStore, ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<ScreenBlackoutParams> capability() {
        return CommonDeviceCapability.SCREEN_BLACKOUT;
    }

    @Override
    public CommandResult execute(DeviceContext device, ScreenBlackoutParams params) {
        // 1亮屏，0息屏
        ScreenStatusPayload payload = ScreenStatusPayload.builder()
                .screenstatus(!params.isBlackout()).build();
        ColorLightHttpResponse resp = send(device, ColorLightApi.SCREEN_STATUS, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_BLACKOUT_FAIL", "Failed to set screen blackout");
        }
        return successResult();
    }
}
