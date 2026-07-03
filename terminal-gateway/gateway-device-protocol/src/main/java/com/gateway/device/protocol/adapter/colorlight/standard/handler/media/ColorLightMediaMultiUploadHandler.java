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
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.VsnItem;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.VsnRegion;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.DisplayRect;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.FileSource;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.ItemType;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.PathType;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.ReserveMode;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.common.file.MediaFileEntry;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaMultiUploadParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ColorLight 混合多媒体上传 —— POST /api/program/multi.vsn。
 */
@Slf4j
public class ColorLightMediaMultiUploadHandler extends AbstractColorLightHttpHandler<MediaMultiUploadParams> {

    public static final String DEFAULT_MULTI_NAME = "default_multi_media";

    public ColorLightMediaMultiUploadHandler(DeviceTransport transport,
                                             ColorLightCredentialStore credentialStore,
                                             ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<MediaMultiUploadParams> capability() {
        return CommonDeviceCapability.MEDIA_MULTI_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, MediaMultiUploadParams params) {
        List<MediaFileEntry> files = params.getMediaFiles();
        if (files == null || files.isEmpty()) {
            return failureResult("CL_MULTI_EMPTY", "Media file list is empty");
        }

        // order 去重检查
        long distinctOrders = files.stream().mapToInt(MediaFileEntry::getOrder).distinct().count();
        if (distinctOrders < files.size()) {
            return failureResult("CL_MULTI_DUP_ORDER", "mediaFiles 中存在重复的 order 值");
        }

        // 按 order 升序排序
        List<MediaFileEntry> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparingInt(MediaFileEntry::getOrder));

        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        List<VsnRegion> regions = new ArrayList<>();
        int layer = 1;
        for (MediaFileEntry entry : sorted) {
            VsnItem item = VsnItem.builder()
                    .type(entry.getMediaType() == MediaType.VIDEO
                            ? ItemType.VIDEO
                            : ItemType.PICTURE)
                    .volume(1.0f)
                    .alpha(entry.getMediaType() == MediaType.VIDEO ? null : 1.0f)
                    .reserveAS(ReserveMode.FIT_XY)
                    .fileSource(FileSource.builder()
                            .isRelative(PathType.RELATIVE)
                            .filePath(ColorLightProgramBuilder.buildFileSourcePath(DEFAULT_MULTI_NAME, entry.getFileName()))
                            .build())
                    .build();
            VsnRegion region = VsnRegion.builder()
                    .layer(layer++)
                    .rect(DisplayRect.builder().x(0).y(0).width(width).height(height).build())
                    .items(Collections.singletonList(item))
                    .build();
            regions.add(region);
        }

        String vsnJson = ColorLightProgramBuilder.buildMixed(regions, width, height);
        byte[] vsnBytes = vsnJson.getBytes(StandardCharsets.UTF_8);

        log.debug("[{}] 混合媒体上传: program={}.vsn  {} 个文件  vsn={}B  vsnJson={}",
                device.getIp(), DEFAULT_MULTI_NAME, files.size(), vsnBytes.length, vsnJson);

        // 构建 multipart: f1=VSN, f2..fN=媒体文件
        List<MultipartPart> parts = new ArrayList<>();
        parts.add(MultipartPart.builder()
                .name("f1").fileName(DEFAULT_MULTI_NAME + ".vsn")
                .data(vsnBytes).build());
        int idx = 2;
        for (MediaFileEntry entry : sorted) {
            byte[] fileData = entry.getData();
            log.debug("  f{}: {}  type={}  {}B",
                    idx, entry.getFileName(), entry.getMediaType(),
                    fileData != null ? fileData.length : 0);
            parts.add(MultipartPart.builder()
                    .name("f" + idx).fileName(entry.getFileName())
                    .data(fileData).build());
            idx++;
        }

        Map.Entry<String, byte[]> result = ColorLightProgramBuilder.buildMultipartBody(parts);
        ColorLightHttpResponse resp = postMultipart(device,
                ColorLightApi.MEDIA_UPLOAD.resolvePath(DEFAULT_MULTI_NAME),
                result.getValue(), result.getKey());
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_MULTI_FAIL", "Failed to upload multi-media program");
        }
        return successResult();
    }
}
