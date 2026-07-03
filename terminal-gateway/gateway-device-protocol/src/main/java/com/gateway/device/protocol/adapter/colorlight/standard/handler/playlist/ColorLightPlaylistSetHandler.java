package com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist;

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
import com.gateway.device.protocol.model.params.PlaylistSetParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 播放列表设置 —— PUT /api/vsns/sources/lan/vsns/{name}/activated。
 * <p>切换到指定名称的已存在节目。节目需先通过 Media/Text Upload 发布。</p>
 */
@Slf4j
public class ColorLightPlaylistSetHandler extends AbstractColorLightHttpHandler<PlaylistSetParams> {

    public ColorLightPlaylistSetHandler(DeviceTransport transport,
                                        ColorLightCredentialStore credentialStore,
                                        ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<PlaylistSetParams> capability() {
        return CommonDeviceCapability.PLAYLIST_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, PlaylistSetParams params) {
        // 使用 name 或 identifier 作为节目名
        String programName = params.getName();
        if (programName == null || programName.isEmpty()) {
            programName = params.getIdentifier();
        }
        if (programName == null || programName.isEmpty()) {
            return failureResult("CL_PL_NAME", "Playlist name/identifier is required");
        }
        ColorLightHttpResponse resp = put(device,
                ColorLightApi.PLAYLIST_SET.resolvePath(programName), new byte[0]);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_PL_ACTIVATE_FAIL", "Failed to activate playlist: " + programName);
        }
        return successResult();
    }
}
