package com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;

import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 清空播放列表 —— DELETE /api/clrprgms。
 */
@Slf4j
public class ColorLightPlaylistClearHandler extends AbstractColorLightHttpHandler<EmptyParams> {

    private final DeviceCapability<EmptyParams> capability;

    public ColorLightPlaylistClearHandler(DeviceTransport transport,
                                          ColorLightCredentialStore credentialStore,
                                          ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec,
                                          DeviceCapability<EmptyParams> capability) {
        super(transport, credentialStore, codec);
        this.capability = capability;
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        ColorLightHttpResponse resp = send(device, ColorLightApi.PLAYLIST_CLEAR);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_CLEAR_FAIL", "Failed to clear programs");
        }
        return successResult();
    }
}
