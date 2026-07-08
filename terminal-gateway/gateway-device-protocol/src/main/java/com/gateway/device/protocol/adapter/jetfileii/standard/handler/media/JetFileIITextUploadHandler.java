package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SequentSysHelper;
import com.gateway.device.protocol.base.jetfileii.standard.text.NmgTextBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.text.NmgTextStyle;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.TextUploadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本上传处理器 —— 上传 NMG 后自动写入 SEQUENT.SYS 播放列表并重载。
 *
 * <p>字符串模式：传入 {@code text} (String)，用默认控制前缀构建 NMG。
 * <br>二进制模式：传入 {@code data} (byte[])，直接上传预建 NMG (兼容旧用法)。
 *
 * <p>样式 (色彩/字体/出入花样) 由 application.yml {@code device.jetfileii.text} 配置。</p>
 */
@Slf4j
public class JetFileIITextUploadHandler extends AbstractJetFileIIHandler<TextUploadParams> {

    private final String defaultFileName;
    private final NmgTextStyle textStyle;

    public JetFileIITextUploadHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                      String defaultFileName, NmgTextStyle textStyle) {
        super(messaging, transport);
        this.defaultFileName = defaultFileName;
        this.textStyle = textStyle;
    }

    @Override
    public DeviceCapability<TextUploadParams> capability() {
        return CommonDeviceCapability.TEXT_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, TextUploadParams params) {
        try {
            Partition partition = params.getPartition();
            String fileName = defaultFileName;

            byte[] nmgBytes;
            String text = params.getText();

            if (StringUtils.isNotBlank(text)) {
                NmgTextStyle effectiveStyle = resolveTextStyle(params);
                nmgBytes = NmgTextBuilder.builder()
                        .controlPrefix(effectiveStyle.buildControlPrefix())
                        .text(text)
                        .build()
                        .toBytes();
                log.info("[{}] TEXT_UPLOAD \"{}\" → {}:{} ({}B NMG)",
                        device.getIp(), text.replace("\n", "\\n"),
                        partition.getDrive(), fileName, nmgBytes.length);
            } else {
                nmgBytes = params.getData();
                if (nmgBytes == null || nmgBytes.length == 0) {
                    return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                            "缺少文本内容 (params.text) 或文件数据 (params.data)");
                }
                log.info("[{}] TEXT_UPLOAD data[{}B] → {}:{}",
                        device.getIp(), nmgBytes.length, partition.getDrive(), fileName);
            }

            String filePath = FileType.TEXT.resolvePath(partition, fileName);

            FileTransfer ft = createFileTransfer(device);
            ft.writePathFile(filePath, nmgBytes, ProtocolConst.DEFAULT_CHUNK_SIZE);

            // ── 写入播放列表并重载 ──
            int lineCount = StringUtils.isNotBlank(text) ? text.split("\n", -1).length : 0;
            SequentSysHelper.writeText(ft, filePath, lineCount);
            log.info("[{}] 播放列表已更新: [{}]", device.getIp(), filePath);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", fileName);
            result.put("partition", (int) partition.getCode());
            result.put("size", nmgBytes.length);
            result.put("playlist", filePath);
            return CommandResult.success(result);
        } catch (Exception e) {
            log.error("[{}] TEXT_UPLOAD 失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    private NmgTextStyle resolveTextStyle(TextUploadParams p) {
        if (p == null) return this.textStyle;
        Object tsObj = p.getTextStyle();
        if (tsObj instanceof NmgTextStyle) {
            return (NmgTextStyle) tsObj;
        }
        return this.textStyle;
    }
}
