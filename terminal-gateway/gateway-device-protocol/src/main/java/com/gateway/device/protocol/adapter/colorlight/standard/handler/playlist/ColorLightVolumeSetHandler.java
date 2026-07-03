package com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;

import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.VolumeSetPayload;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.VolumeSetParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 音量设置 —— PUT /api/volume。
 * <p>ratio (0-100) → musicvolume (0-15)</p>
 */
@Slf4j
public class ColorLightVolumeSetHandler extends AbstractColorLightHttpHandler<VolumeSetParams> {

    public ColorLightVolumeSetHandler(DeviceTransport transport,
                                      ColorLightCredentialStore credentialStore,
                                      ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<VolumeSetParams> capability() {
        return CommonDeviceCapability.VOLUME_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, VolumeSetParams params) {
        // 输出的音量分为16个等级（0-15），15表示音量为100%
        int vol = params.getRatio() * 15 / 100;
        VolumeSetPayload payload = VolumeSetPayload.builder()
                .musicvolume(vol).build();
        ColorLightHttpResponse resp = send(device, ColorLightApi.VOLUME_SET, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_VOL_SET_FAIL", "Failed to set volume");
        }
        return successResult();
    }
}
