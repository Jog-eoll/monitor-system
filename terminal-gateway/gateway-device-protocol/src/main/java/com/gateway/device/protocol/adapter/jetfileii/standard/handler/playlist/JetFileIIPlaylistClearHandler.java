package com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

/**
 * 播放列表清除处理器。
 */
@Slf4j
public class JetFileIIPlaylistClearHandler extends AbstractJetFileIIHandler<EmptyParams> {

    public JetFileIIPlaylistClearHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.PLAYLIST_CLEAR;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        try {
            FileTransfer ft = createFileTransfer(device);
            ft.clearPlaylist();

            log.info("[{}] PLAYLIST_CLEAR 完成", device.getIp());
            return CommandResult.success();
        } catch (Exception e) {
            log.error("[{}] PLAYLIST_CLEAR 失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
