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
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.file.MediaFileEntry;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaMultiUploadParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ColorLight 多页面节目上传 —— 每个媒体文件对应一个独立的节目页。
 *
 * <p>流程（对齐 Web UI 调用顺序）：</p>
 * <ol>
 *   <li>构建多页 VSN + 媒体文件 multipart → POST /api/program/{name}.vsn</li>
 *   <li>设置默认缩略图 → PUT /api/programthumbnail/{name}.vsn（非致命）</li>
 * </ol>
 */
@Slf4j
public class ColorLightMediaMultiUploadHandler extends AbstractColorLightHttpHandler<MediaMultiUploadParams> {

    /**
     * 默认多页面节目名（不含 .vsn 扩展名）
     */
    public static final String DEFAULT_MULTI_PROGRAM_NAME = "default_mutli_media_program";

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
            return failureResult("CL_MULTI_PG_EMPTY", "Media file list is empty");
        }

        // order 去重检查
        long distinctOrders = files.stream().mapToInt(MediaFileEntry::getOrder).distinct().count();
        if (distinctOrders < files.size()) {
            return failureResult("CL_MULTI_PG_DUP_ORDER", "mediaFiles 中存在重复的 order 值");
        }

        // 按 order 升序排序
        List<MediaFileEntry> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparingInt(MediaFileEntry::getOrder));

        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        // ── Step 1: 构建多页 VSN + 媒体文件 multipart 上传 ──
        // POST /api/program/{name}.vsn
        String vsnJson = ColorLightProgramBuilder.buildMultiPage(sorted, DEFAULT_MULTI_PROGRAM_NAME, width, height);
        byte[] vsnBytes = vsnJson.getBytes(StandardCharsets.UTF_8);

        log.debug("[{}] 多页面节目上传: program={}.vsn  {} 个文件  vsn={}B  vsnJson={}",
                device.getIp(), DEFAULT_MULTI_PROGRAM_NAME, files.size(), vsnBytes.length, vsnJson);

        // 构建 multipart: f1=VSN, f2..fN=媒体文件
        List<MultipartPart> parts = new ArrayList<>();
        parts.add(MultipartPart.builder()
                .name("f1").fileName(DEFAULT_MULTI_PROGRAM_NAME + ".vsn")
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
                ColorLightApi.MEDIA_UPLOAD.resolvePath(DEFAULT_MULTI_PROGRAM_NAME),
                result.getValue(), result.getKey());
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_MULTI_PG_FAIL", "Failed to upload multi-page program media");
        }

        // ── Step 1(置后):设置默认缩略图（非致命） ──
        postSetThumbnail(device, DEFAULT_MULTI_PROGRAM_NAME);

        return successResult();
    }
}
