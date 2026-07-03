package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.media;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.MediaFileInfo;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreFileHelper;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.common.file.MediaFileEntry;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaMultiUploadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 混合媒体上传处理器 — 支持图片+视频混合上传，每个文件独立容器独立 zOrder。
 *
 * <p>管线: GetFileMd5(×N) → CreateProgram → SetMediaMultiPage → MakeProgram → StartTransferProgram
 * <br>参数: mediaFiles(List 含 order), width(屏宽), height(屏高)
 * <br>排序: 按 order 升序，zOrder 为独立顺序编号(1,2,3…)</p>
 */
@Slf4j
public class NovaViplexCoreMediaMultiUploadHandler extends AbstractNovaViplexCoreHandler<MediaMultiUploadParams> {

    public NovaViplexCoreMediaMultiUploadHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<MediaMultiUploadParams> capability() {
        return CommonDeviceCapability.MEDIA_MULTI_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, MediaMultiUploadParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        // 1. 提取参数
        List<MediaFileEntry> mediaFiles = params != null ? params.getMediaFiles() : null;
        if (CollectionUtils.isEmpty(mediaFiles)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少 mediaFiles 参数或列表为空");
        }

        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        log.debug("[MediaMultiUpload] SN={} mediaCount={} {}x{}", sn, mediaFiles.size(), width, height);

        // 2. 检查 order 重复
        long distinctOrders = mediaFiles.stream().mapToInt(MediaFileEntry::getOrder).distinct().count();
        if (distinctOrders < mediaFiles.size()) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "mediaFiles 中存在重复的 order 值");
        }

        // 3. 按 order 升序排序
        List<MediaFileEntry> sorted = new ArrayList<>(mediaFiles);
        sorted.sort(Comparator.comparingInt(MediaFileEntry::getOrder));

        // 3. 逐个写入临时文件 → 获取 MD5 → 构建 MediaFileInfo
        List<Path> tempFiles = new ArrayList<>(sorted.size());
        try {
            List<MediaFileInfo> mediaInfos = new ArrayList<>(sorted.size());
            for (int i = 0; i < sorted.size(); i++) {
                MediaFileEntry file = sorted.get(i);

                byte[] data = file.getData();
                String fileName = file.getFileName();
                MediaType mediaType = file.getMediaType();
                Integer duration = file.getDuration();

                if (data == null || data.length == 0 || StringUtils.isEmpty(fileName)) {
                    cleanupTempFiles(tempFiles);
                    return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            String.format("mediaFiles[%d] 缺少 data 或 fileName", i));
                }
                if (mediaType == null) {
                    cleanupTempFiles(tempFiles);
                    return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            String.format("mediaFiles[%d] mediaType 不能为 null", i));
                }

                Path tmp = ViplexCoreFileHelper.writeTempFile(data, fileName);
                tempFiles.add(tmp);

                String md5 = pipeline().getFileMd5(tmp.toString());
                if (md5 == null) {
                    cleanupTempFiles(tempFiles);
                    return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                            String.format("获取 mediaFiles[%d] MD5 失败: %s", i, fileName));
                }

                int d = duration != null ? duration : 10000;
                mediaInfos.add(MediaFileInfo.builder()
                        .filePath(tmp.toString()).md5(md5).fileName(fileName)
                        .mediaType(mediaType).fileSize(data.length).duration(d).build());
                log.debug("[MediaMultiUpload] [{}/{}] order={} type={} file={} md5={} size={} duration={}",
                        i + 1, sorted.size(), file.getOrder(), mediaType, fileName, md5, data.length, d);
            }

            // 4. 创建节目
            String outPutPath;
            try {
                outPutPath = pipeline().getProgramOutputDir();
            } catch (IOException e) {
                cleanupTempFiles(tempFiles);
                return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, String.format("创建输出目录失败: %s", e.getMessage()));
            }

            ViplexResponse createResp = pipeline().createProgram(width, height);
            if (!createResp.isSuccess()) {
                cleanupTempFiles(tempFiles);
                if (createResp.isTimeout()) return CommandResult.timeout();
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("创建节目失败: %s", createResp.getData()));
            }
            String programId = pipeline().parseProgramId(createResp);
            if (programId == null) {
                cleanupTempFiles(tempFiles);
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("创建节目响应缺少 programID: %s", createResp.getData()));
            }

            // 5. 设置混合媒体页面（每文件独立页面，nvSetPageProgramsAsync 批量）
            ViplexResponse pageResp = pipeline().setMediaMultiPage(
                    programId, mediaInfos);
            if (!pageResp.isSuccess()) {
                cleanupTempFiles(tempFiles);
                if (pageResp.isTimeout()) return CommandResult.timeout();
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("设置混合媒体页面失败: %s", pageResp.getData()));
            }

            // 6. 制作节目
            ViplexResponse makeResp = pipeline().makeProgram(programId, outPutPath);
            if (!makeResp.isSuccess()) {
                cleanupTempFiles(tempFiles);
                if (makeResp.isTimeout()) return CommandResult.timeout();
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("制作节目失败: %s", makeResp.getData()));
            }

            // 7. 发送到终端
            Map<String, String> mediasPath = new LinkedHashMap<>();
            for (MediaFileInfo info : mediaInfos) {
                mediasPath.put(info.getFilePath(), info.getFileName());
            }
            ViplexResponse transferResp = pipeline().transferProgram(
                    sn, programId, outPutPath, mediasPath);
            if (transferResp.isTimeout()) return CommandResult.timeout();
            if (!transferResp.isSuccess()) {
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("发送节目失败: %s", transferResp.getData()));
            }

            log.debug("[MediaMultiUpload] 完成 SN={} mediaCount={}", sn, mediaInfos.size());
            return CommandResult.success();
        } catch (IOException e) {
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR,
                    String.format("文件操作失败: %s", e.getMessage()));
        } finally {
            cleanupTempFiles(tempFiles);
        }
    }

    private void cleanupTempFiles(List<Path> tempFiles) {
        for (Path tmp : tempFiles) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }
}
