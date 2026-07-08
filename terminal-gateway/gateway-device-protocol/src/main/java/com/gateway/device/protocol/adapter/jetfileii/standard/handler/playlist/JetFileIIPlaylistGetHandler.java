package com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SysFileName;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.PlaylistGetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

/**
 * 播放列表查询 —— 支持读取当前播放文件，或下载完整 SEQUENT.SYS 文件。
 *
 * <p>仅当前文件：无参调用 → READ_CUR_FILE
 * <br>下载系统文件：{@code sysFile} (String, 如 "SEQUENT.SYS") → readSysFile
 */
@Slf4j
public class JetFileIIPlaylistGetHandler extends AbstractJetFileIIHandler<PlaylistGetParams> {

    public JetFileIIPlaylistGetHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<PlaylistGetParams> capability() {
        return CommonDeviceCapability.PLAYLIST_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, PlaylistGetParams params) {
        String sysFile = params.getSysFile() != null ? params.getSysFile() : SysFileName.SEQUENT_SYS;
        if (StringUtils.isNotBlank(sysFile)) {
            try {
                FileTransfer ft = createFileTransfer(device);
                byte[] data = ft.readSysFile(sysFile);
                log.info("[{}] 读取系统文件: {} ({}B)", device.getIp(), sysFile, data.length);
                return CommandResult.success(data);
            } catch (Exception e) {
                log.error("[{}] 读取系统文件失败: {}", device.getIp(), e.getMessage(), e);
                return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
            }
        }
        // 简单回退：查询当前播放文件
        JetFileIIRequest req = JetFileIIRequest.of(MainCmd.DISPLAY, SubCmd.DISP_READ_CUR_FILE);
        return messaging().executeSimple(transport(), device, req, Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS));
    }
}
