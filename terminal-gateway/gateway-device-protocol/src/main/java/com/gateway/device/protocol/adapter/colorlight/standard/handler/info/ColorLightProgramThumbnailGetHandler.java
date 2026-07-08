package com.gateway.device.protocol.adapter.colorlight.standard.handler.info;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.ColorLightCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ProgramThumbnailGetParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 节目缩略图查询 —— GET /images/{name}.files/{name}.jpeg。
 */
@Slf4j
public class ColorLightProgramThumbnailGetHandler
        extends AbstractColorLightHttpHandler<ProgramThumbnailGetParams> {

    public ColorLightProgramThumbnailGetHandler(DeviceTransport transport,
                                                ColorLightCredentialStore credentialStore,
                                                ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<ProgramThumbnailGetParams> capability() {
        return ColorLightCapability.PROGRAM_THUMBNAIL_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, ProgramThumbnailGetParams params) {
        String uri = ColorLightApi.PROGRAM_THUMBNAIL_GET.resolvePath(params.getProgramName());
        ColorLightHttpResponse resp = get(device, uri);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_THUMB_GET_FAIL", "获取节目缩略图失败");
        }
        byte[] jpeg = resp.getBody();
        if (jpeg == null || jpeg.length == 0) {
            return failureResult("CL_THUMB_EMPTY", "缩略图数据为空");
        }
        return successResult(jpeg);
    }
}
