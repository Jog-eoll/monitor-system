package com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.sys.SequentSysHelper;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.PlaylistSetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 播放列表设置 — 设定播放列表，可选预检文件存在。
 *
 * <p>空 {@code paths} 直接拒绝（清除请用 {@code PLAYLIST_CLEAR}）。
 * <table>
 *   <tr><td>{@code paths} 非空, {@code checkExistence}=false</td><td>直接设定</td></tr>
 *   <tr><td>{@code paths} 非空, {@code checkExistence}=true</td><td>先校验文件存在，缺失返回失败</td></tr>
 * </table>
 */
@Slf4j
public class JetFileIIPlaylistSetHandler extends AbstractJetFileIIHandler<PlaylistSetParams> {

    public JetFileIIPlaylistSetHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<PlaylistSetParams> capability() {
        return CommonDeviceCapability.PLAYLIST_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, PlaylistSetParams params) {
        try {
            FileTransfer ft = createFileTransfer(device);

            List<String> paths = params.getPaths();
            if (CollectionUtils.isEmpty(paths)) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "paths 不能为空，清除请使用 PLAYLIST_CLEAR");
            }

            boolean checkExistence = params.isCheckExistence();

            // ── 校验文件存在 ──
            if (checkExistence) {
                List<String> missing = new ArrayList<>();
                for (String path : paths) {
                    if (!ft.fileExists(path)) {
                        missing.add(path);
                    }
                }
                if (!missing.isEmpty()) {
                    log.warn("[{}] 播放列表设定跳过 — 缺失文件: {}", device.getIp(), missing);
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("missingFiles", missing);
                    detail.put("paths", paths);
                    detail.put("skipped", true);
                    return CommandResult.builder()
                            .success(false).code(StandardErrorCode.DEVICE_NOT_FOUND)
                            .message("设备文件不全，已跳过")
                            .data(detail).build();
                }
            }

            // ── 设定 ──
            SequentSysHelper.write(ft, paths);
            log.info("[{}] 节目单已更新: {} 条", device.getIp(), paths.size());
            return CommandResult.success();
        } catch (Exception e) {
            log.error("[{}] 节目单设置失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
