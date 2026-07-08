package com.gateway.device.protocol.adapter.colorlight.standard.handler.media;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.helper.ColorLightProgramBuilder;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.TextUploadParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * ColorLight 文本上传 —— POST /api/program/{name}.vsn。
 * <p>从 TextUploadParams.text 构建多行文本节目（Type=5）VSN JSON。</p>
 */
@Slf4j
public class ColorLightTextUploadHandler extends AbstractColorLightHttpHandler<TextUploadParams> {

    public static final String DEFAULT_TEXT_NAME = "default_text";

    public ColorLightTextUploadHandler(DeviceTransport transport,
                                       ColorLightCredentialStore credentialStore,
                                       ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<TextUploadParams> capability() {
        return CommonDeviceCapability.TEXT_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, TextUploadParams params) {
        String text = params.getText();
        if (text == null || text.isEmpty()) {
            return failureResult("CL_TEXT_EMPTY", "Text content is empty");
        }
        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        String vsnJson = ColorLightProgramBuilder.buildText(text, 0, 0, width, height);

        ColorLightHttpResponse resp = post(device, ColorLightApi.MEDIA_UPLOAD.resolvePath(DEFAULT_TEXT_NAME),
                vsnJson.getBytes(StandardCharsets.UTF_8));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_TEXT_FAIL", "Failed to upload text program");
        }

        // ── 设置默认缩略图（非致命） ──
        postSetThumbnail(device, DEFAULT_TEXT_NAME);

        return successResult();
    }
}
