package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.FileDeleteParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单文件删除处理器 — 统一处理各媒体类型的终端文件删除。
 *
 * <p>通过构造函数参数化差异化要素（Capability + FileType + 可选默认文件名）。</p>
 */
@Slf4j
public class JetFileIIResourceDeleteHandler extends AbstractJetFileIIHandler<FileDeleteParams> {

    private final DeviceCapability<FileDeleteParams> capability;
    private final FileType fileType;
    private final String logTag;
    private final String defaultFileName;

    public JetFileIIResourceDeleteHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                          DeviceCapability<FileDeleteParams> capability, FileType fileType) {
        this(messaging, transport, capability, fileType, null);
    }

    public JetFileIIResourceDeleteHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                          DeviceCapability<FileDeleteParams> capability, FileType fileType,
                                          String defaultFileName) {
        super(messaging, transport);
        this.capability = capability;
        this.fileType = fileType;
        this.logTag = capability.name();
        this.defaultFileName = defaultFileName;
    }

    @Override
    public DeviceCapability<FileDeleteParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, FileDeleteParams params) {
        try {
            Partition partition = params.getPartition();
            String fileName = StringUtils.defaultIfBlank(params.getFileName(), defaultFileName);
            if (StringUtils.isBlank(fileName)) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        "缺少文件名 (params.fileName)");
            }

            FileTransfer ft = createFileTransfer(device);
            String filePath = fileType.resolvePath(partition, fileName);
            ft.deleteFile(filePath);

            log.info("[{}] {} {}", device.getIp(), logTag, filePath);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deleted", filePath);
            result.put("partition", (int) partition.getCode());
            return CommandResult.success(result);
        } catch (Exception e) {
            log.error("[{}] {} 失败: {}", device.getIp(), logTag, e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
