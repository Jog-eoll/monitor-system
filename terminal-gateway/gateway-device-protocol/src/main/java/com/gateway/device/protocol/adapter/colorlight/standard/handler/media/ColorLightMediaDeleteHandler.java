package com.gateway.device.protocol.adapter.colorlight.standard.handler.media;

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
import com.gateway.device.protocol.model.params.FileDeleteParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 媒体删除 —— DELETE /api/vsns/sources/lan/vsns/{name}。
 */
@Slf4j
public class ColorLightMediaDeleteHandler extends AbstractColorLightHttpHandler<FileDeleteParams> {

    private final DeviceCapability<FileDeleteParams> capability;

    public ColorLightMediaDeleteHandler(DeviceTransport transport,
                                        ColorLightCredentialStore credentialStore,
                                        ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec,
                                        DeviceCapability<FileDeleteParams> capability) {
        super(transport, credentialStore, codec);
        this.capability = capability;
    }

    @Override
    public DeviceCapability<FileDeleteParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, FileDeleteParams params) {
        String fileName = params.getFileName();
        if (fileName == null || fileName.isEmpty()) {
            return failureResult("CL_DEL_EMPTY", "File name is empty");
        }
        ColorLightHttpResponse resp = delete(device, ColorLightApi.MEDIA_DELETE.resolvePath(fileName));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_DEL_FAIL", "Failed to delete: " + fileName);
        }
        return successResult();
    }
}
