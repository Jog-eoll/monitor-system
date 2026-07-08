package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.file.JetFileIIFileExtensions;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SequentSysHelper;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.common.file.ExtensionValidator;
import com.gateway.device.protocol.common.file.MediaFileEntry;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaMultiUploadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 混合媒体批量上传处理器 — 图片+视频混合，逐文件上传后统一设置多曲播放列表。
 *
 * <p>处理流程: 校验 → 按 order 排序 → 逐文件上传(writePathFile) → SequentSysHelper.write</p>
 */
@Slf4j
public class JetFileIIMediaMultiUploadHandler extends AbstractJetFileIIHandler<MediaMultiUploadParams> {

    private final JetFileIIFileExtensions extensions;

    public JetFileIIMediaMultiUploadHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                            JetFileIIFileExtensions extensions) {
        super(messaging, transport);
        this.extensions = extensions;
    }

    /**
     * MediaType → JetFileII FileType 映射
     */
    private static FileType mapMediaToFileType(MediaType type) {
        switch (type) {
            case IMAGE:
                return FileType.PICTURE;
            case VIDEO:
                return FileType.FLW;
            default:
                throw new IllegalArgumentException(String.format("不支持的 MediaType: %s", type));
        }
    }

    @Override
    public DeviceCapability<MediaMultiUploadParams> capability() {
        return CommonDeviceCapability.MEDIA_MULTI_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, MediaMultiUploadParams params) {
        // ── 1. 提取参数 ──
        List<MediaFileEntry> mediaFiles = params != null ? params.getMediaFiles() : null;
        if (CollectionUtils.isEmpty(mediaFiles)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少 mediaFiles 参数或列表为空");
        }

        // ── 2. order 去重检查 ──
        long distinctOrders = mediaFiles.stream().mapToInt(MediaFileEntry::getOrder).distinct().count();
        if (distinctOrders < mediaFiles.size()) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "mediaFiles 中存在重复的 order 值");
        }

        // ── 3. 按 order 升序排序 ──
        List<MediaFileEntry> sorted = new ArrayList<>(mediaFiles);
        sorted.sort(Comparator.comparingInt(MediaFileEntry::getOrder));

        // ── 4. 逐条目校验 + 逐文件上传 ──
        FileTransfer ft = createFileTransfer(device);
        List<String> paths = new ArrayList<>(sorted.size());

        try {
            for (int i = 0; i < sorted.size(); i++) {
                MediaFileEntry entry = sorted.get(i);
                byte[] data = entry.getData();
                String fileName = entry.getFileName();
                MediaType mediaType = entry.getMediaType();

                // 单条目录缺失校验
                if (data == null || data.length == 0 || StringUtils.isEmpty(fileName)) {
                    return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            String.format("mediaFiles[%d] order=%d 缺少 data 或 fileName", i, entry.getOrder()));
                }
                if (mediaType == null) {
                    return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            String.format("mediaFiles[%d] order=%d mediaType 不能为 null", i, entry.getOrder()));
                }
                List<String> allowedExts = mediaType == MediaType.IMAGE
                        ? extensions.getImage() : extensions.getVideo();
                if (!ExtensionValidator.isAllowed(fileName, allowedExts)) {
                    String ext = FilenameUtils.getExtension(fileName);
                    return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            String.format("mediaFiles[%d] order=%d 不支持的文件扩展名: *.%s (mediaType=%s)", i, entry.getOrder(), ext, mediaType));
                }

                FileType fileType = mapMediaToFileType(mediaType);
                String devicePath = fileType.resolvePath(Partition.D, fileName);

                log.debug("[MediaMultiUpload] [{}/{}] order={} type={} → {} ({}B)",
                        i + 1, sorted.size(), entry.getOrder(), mediaType, devicePath, data.length);

                ft.writePathFile(devicePath, data, ProtocolConst.DEFAULT_CHUNK_SIZE);
                paths.add(devicePath);
            }

            // ── 5. 统一设置多曲混合播放列表 ──
            SequentSysHelper.write(ft, paths);
            log.info("[{}] MEDIA_MULTI_UPLOAD 完成: {} 个文件 → 播放列表已更新",
                    device.getIp(), paths.size());

            return CommandResult.success();
        } catch (Exception e) {
            log.error("[{}] MEDIA_MULTI_UPLOAD 失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
