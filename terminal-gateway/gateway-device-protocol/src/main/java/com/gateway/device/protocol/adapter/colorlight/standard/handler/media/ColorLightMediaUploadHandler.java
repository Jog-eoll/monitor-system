package com.gateway.device.protocol.adapter.colorlight.standard.handler.media;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.helper.ColorLightProgramBuilder;
import com.gateway.device.protocol.base.colorlight.standard.model.MultipartPart;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaUploadParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * ColorLight 媒体上传（图片/视频）—— POST /api/program/{name}.vsn。
 * <p>完整 multipart 上传需后续扩展，当前为 JSON VSN + 文件名引用模式。</p>
 */
@Slf4j
public class ColorLightMediaUploadHandler extends AbstractColorLightHttpHandler<MediaUploadParams> {

    private final DeviceCapability<MediaUploadParams> capability;

    public ColorLightMediaUploadHandler(DeviceTransport transport,
                                        ColorLightCredentialStore credentialStore,
                                        ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec,
                                        DeviceCapability<MediaUploadParams> capability) {
        super(transport, credentialStore, codec);
        this.capability = capability;
    }

    @Override
    public DeviceCapability<MediaUploadParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, MediaUploadParams params) {
        String fileName = params.getFileName();
        if (fileName == null || fileName.isEmpty()) {
            return failureResult("CL_MEDIA_NAME", "File name is required");
        }
        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        String programName = ColorLightProgramBuilder.sanitizeProgramName(fileName);
        String filePath = ColorLightProgramBuilder.buildFileSourcePath(programName, fileName);
        String vsnJson = ColorLightProgramBuilder.buildImage(filePath, 0, 0, width, height);
        byte[] vsnBytes = vsnJson.getBytes(StandardCharsets.UTF_8);
        byte[] mediaData = params.getData();
        int mediaSize = mediaData != null ? mediaData.length : 0;

        log.debug("[{}] 媒体上传: program={}.vsn  file={}({}B)  vsn={}B  vsnJson={}",
                device.getIp(), programName, fileName, mediaSize, vsnBytes.length, vsnJson);

        Map.Entry<String, byte[]> result = ColorLightProgramBuilder.buildMultipartBody(Arrays.asList(
                MultipartPart.builder()
                        .name("f1").fileName(programName + ".vsn")
                        .data(vsnBytes).build(),
                MultipartPart.builder()
                        .name("f2").fileName(fileName)
                        .data(mediaData).build()
        ));
        ColorLightHttpResponse resp = postMultipart(device,
                ColorLightApi.MEDIA_UPLOAD.resolvePath(programName),
                result.getValue(), result.getKey());
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_MEDIA_FAIL", "Failed to upload media program");
        }

        // ── 设置默认缩略图（非致命） ──
        postSetThumbnail(device, programName);

        return successResult();
    }
}
