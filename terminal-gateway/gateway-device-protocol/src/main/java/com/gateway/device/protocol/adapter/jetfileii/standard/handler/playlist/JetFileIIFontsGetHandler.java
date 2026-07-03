package com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.text.FontListFile;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字体列表查询处理器 —— 读取设备 FONTLIST.LST 返回当前字体清单。
 *
 * <p>通过 {@link FileTransfer#readFontList()} 下载并解析 FONTLIST.LST。</p>
 */
@Slf4j
public class JetFileIIFontsGetHandler extends AbstractJetFileIIHandler<EmptyParams> {

    public JetFileIIFontsGetHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.FONTS_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        try {
            FileTransfer ft = createFileTransfer(device);
            FontListFile list = ft.readFontList();

            List<Map<String, Object>> fonts = new ArrayList<>();
            for (FontListFile.FontEntry e : list.entries()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("fileName", e.getFileName());
                entry.put("fontCode", String.valueOf(e.getFontCode()));
                entry.put("fileSize", e.getFileSize());
                entry.put("width", e.getWidth());
                entry.put("height", e.getHeight());
                fonts.add(entry);
            }

            log.info("[{}] 查询字体列表完成, 共 {} 字体", device.getIp(), fonts.size());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", fonts.size());
            result.put("fonts", fonts);
            return CommandResult.success(result);
        } catch (Exception e) {
            log.error("[{}] 查询字体列表失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
