package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.PlaylistGetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 播放列表查询处理器 —— 获取终端当前节目列表（新式 GET 结构）。
 *
 * <p>SDK: {@code nvGetProgramInfoAsync}</p>
 */
@Slf4j
public class NovaViplexCorePlaylistGetHandler extends AbstractNovaViplexCoreHandler<PlaylistGetParams> {

    public NovaViplexCorePlaylistGetHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<PlaylistGetParams> capability() {
        return CommonDeviceCapability.PLAYLIST_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, PlaylistGetParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }
        log.info("获取播放列表 SN={}", sn);
        return executeQuery(sn);
    }

    private CommandResult executeQuery(String sn) {
        try {
            String jsonParams = ViplexCoreJsonBuilder.buildSnJson(sn);

            ViplexResponse resp = channel().execute(SdkFunction.NV_GET_PROGRAM_INFO_ASYNC, jsonParams, getTimeout());

            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("SDK 响应超时").build();
            }
            if (!resp.isSuccess()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("SDK 返回错误: %s", resp.getData())).build();
            }
            JsonNode data = resp.dataAsJson();
            return data != null
                    ? CommandResult.builder().success(true).data(data).build()
                    : CommandResult.builder().success(true).build();
        } catch (Exception e) {
            log.warn("获取播放列表失败 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("SDK 调用异常: %s", e.getMessage())).build();
        }
    }
}
