package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.text.NmgTextFile;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.FileDownloadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本下载处理器 —— 下载 NMG 文件并解析返回纯文本字符串。
 *
 * <p>参数：{@code partition} + {@code fileName}（默认 defaultFileName）</p>
 */
@Slf4j
public class JetFileIITextDownloadHandler extends AbstractJetFileIIHandler<FileDownloadParams> {

    private final String defaultFileName;

    public JetFileIITextDownloadHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                        String defaultFileName) {
        super(messaging, transport);
        this.defaultFileName = defaultFileName;
    }

    @Override
    public DeviceCapability<FileDownloadParams> capability() {
        return CommonDeviceCapability.TEXT_DOWNLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, FileDownloadParams params) {
        try {
            Partition partition = params.getPartition();
            String fileName = defaultFileName;
            if (StringUtils.isBlank(fileName)) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少文件名 (params.fileName)");
            }

            FileTransfer ft = createFileTransfer(device);
            String filePath = FileType.TEXT.resolvePath(partition, fileName);
            byte[] data = ft.readPathFile(filePath);
            String text = NmgTextFile.extractText(data).replaceFirst("\n$", "");

            log.info("[{}] TEXT_DOWNLOAD {} → \"{}\" ({}B raw)",
                    device.getIp(), filePath,
                    text.replace("\n", "\\n"), data.length);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("text", text);
            result.put("rawSize", data.length);
            result.put("fileName", fileName);
            result.put("partition", partition.getDrive());
            return CommandResult.success(result);
        } catch (Exception e) {
            log.error("[{}] TEXT_DOWNLOAD 失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
