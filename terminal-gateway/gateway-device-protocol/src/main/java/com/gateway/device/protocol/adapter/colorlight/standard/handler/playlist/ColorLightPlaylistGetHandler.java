package com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.MediaItem;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.PlaylistGetParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 播放列表查询 —— GET /api/vsns.json。
 */
@Slf4j
public class ColorLightPlaylistGetHandler extends AbstractColorLightHttpHandler<PlaylistGetParams> {

    public ColorLightPlaylistGetHandler(DeviceTransport transport,
                                        ColorLightCredentialStore credentialStore,
                                        ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<PlaylistGetParams> capability() {
        return CommonDeviceCapability.PLAYLIST_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, PlaylistGetParams params) {
        ColorLightHttpResponse resp = send(device, ColorLightApi.VSN_LIST);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_PL_GET_FAIL", "Failed to get playlist");
        }
        java.util.List<MediaItem> items = parseJsonArray(resp, MediaItem.class);
        return successResult(items);
    }
}
