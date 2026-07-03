package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIResourceMapping;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileDataParser;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.FileDownloadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 资源下载处理器 —— 封装 FileTransfer，调用方只需传入公共参数。
 *
 * <p>路径模式：{@code remotePath} → readPathFileCrc
 * <br>文件名模式：{@code partition} + {@code fileName}
 */
@Slf4j
public class JetFileIIResourceDownloadHandler extends AbstractJetFileIIHandler<FileDownloadParams> {

    private final DeviceCapability<FileDownloadParams> capability;

    public JetFileIIResourceDownloadHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                            DeviceCapability<FileDownloadParams> capability) {
        super(messaging, transport);
        this.capability = capability;
    }

    @Override
    public DeviceCapability<FileDownloadParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, FileDownloadParams params) {
        try {
            FileTransfer ft = createFileTransfer(device);

            String remotePath = params.getRemotePath();
            byte[] data;

            if (StringUtils.isNotBlank(remotePath)) {
                data = ft.readPathFileCrc(remotePath);
            } else if (params.getFileName() != null) {
                Partition partition = params.getPartition();
                String fileName = params.getFileName();
                data = readByCapability(ft, partition, fileName);
            } else {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        "缺少文件路径 (params.remotePath) 或 (params.fileName)");
            }

            log.info("[{}] {} 下载完成: {} ({}B)", device.getIp(), capability,
                    StringUtils.isNotBlank(remotePath) ? remotePath : params.getFileName(), data.length);
            return CommandResult.success(data);
        } catch (Exception e) {
            log.error("[{}] {} 下载失败: {}", device.getIp(), capability, e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    private byte[] readByCapability(FileTransfer ft,
                                    Partition partition, String fileName) throws Exception {
        FileType fileType = JetFileIIResourceMapping.fileTypeOf(capability);
        String filePath = fileType.resolvePath(partition, fileName);
        byte[] raw = ft.readPathFileCrc(filePath);
        if (capability == JetFileIICapability.PMG_FILE_DOWNLOAD) {
            return FileDataParser.wrapWithNG(raw);
        }
        return raw;
    }
}
