package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.file.JetFileIIFileExtensions;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIResourceMapping;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SequentSysHelper;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.common.file.ExtensionValidator;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaUploadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;

/**
 * 资源上传处理器 — 字节直写 + 播放列表。
 *
 * <p>适用 IMAGE / VIDEO / PMG / QST / NMG（不做解析，只传输）。
 * TEXT 上传由 {@link TextUploadHandler} 独立处理。</p>
 */
@Slf4j
public class JetFileIIResourceUploadHandler extends AbstractJetFileIIHandler<MediaUploadParams> {

    private final DeviceCapability<MediaUploadParams> capability;
    private final JetFileIIFileExtensions extensions;

    public JetFileIIResourceUploadHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                          DeviceCapability<MediaUploadParams> capability,
                                          JetFileIIFileExtensions extensions) {
        super(messaging, transport);
        this.capability = capability;
        this.extensions = extensions;
    }

    @Override
    public DeviceCapability<MediaUploadParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, MediaUploadParams params) {
        try {
            byte[] data = params.getData();
            if (data == null || data.length == 0) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少文件数据 (params.data)");
            }

            // ── 扩展名校验 ──
            String checkName = params.getFileName();
            if (StringUtils.isBlank(checkName) && StringUtils.isNotBlank(params.getRemotePath())) {
                checkName = FilenameUtils.getName(params.getRemotePath());
            }
            if (StringUtils.isNotBlank(checkName)
                    && !ExtensionValidator.isAllowed(checkName,
                    JetFileIIResourceMapping.extensionsOf(capability, extensions))) {
                String ext = FilenameUtils.getExtension(checkName);
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        String.format("不支持的文件扩展名: *.%s (capability=%s)", ext, capability));
            }

            int chunkSize = params.getChunkSize();

            FileTransfer ft = createFileTransfer(device);

            String remotePath = params.getRemotePath();
            String deviceFilePath;

            if (StringUtils.isNotBlank(remotePath)) {
                ft.writePathFile(remotePath, data, chunkSize);
                deviceFilePath = remotePath;
                log.info("[{}] {} 上传完成: {} ({}B)", device.getIp(), capability, remotePath, data.length);
            } else if (params.getFileName() != null) {
                Partition partition = params.getPartition();
                String fileName = params.getFileName();

                FileType fileType = JetFileIIResourceMapping.fileTypeOf(capability);
                deviceFilePath = fileType.resolvePath(partition, fileName);
                ft.writePathFile(deviceFilePath, data, chunkSize);
                log.info("[{}] {} 上传完成: {}:{} ({}B)", device.getIp(), capability,
                        partition.getDrive(), fileName, data.length);
            } else {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        "缺少目标路径 (params.remotePath) 或 (params.fileName)");
            }

            SequentSysHelper.write(ft, Collections.singletonList(deviceFilePath));
            log.info("[{}] 播放列表已更新: [{}]", device.getIp(), deviceFilePath);

            return CommandResult.success();
        } catch (Exception e) {
            log.error("[{}] {} 上传失败: {}", device.getIp(), capability, e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
