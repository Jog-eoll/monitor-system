package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 播放列表清除处理器 — 清空终端播放列表（仅删除播放列表，不删除节目）。
 *
 * <p>SDK: {@code nvDeletePlayListAsync}
 * <br>节目清理由 {@code MEDIA_CLEAR} 负责。</p>
 */
@Slf4j
public class NovaViplexCorePlaylistClearHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCorePlaylistClearHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.PLAYLIST_CLEAR;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        long start = System.currentTimeMillis();

        // 1. 校验 SN
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        // 2. 调用 nvDeletePlayListAsync
        String json = JsonCustomMapper.get().createObjectNode()
                .put("sn", sn).toString();
        log.info("[PLAYLIST_CLEAR] SN={} 清空播放列表", sn);

        ViplexResponse resp = channel().execute(SdkFunction.NV_DELETE_PLAYLIST_ASYNC, json, getTimeout());

        long cost = System.currentTimeMillis() - start;

        // 3. 处理超时
        if (resp.isTimeout()) {
            log.warn("[PLAYLIST_CLEAR] SN={} 清空播放列表超时", sn);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.TIMEOUT)
                    .message("SDK 响应超时").costMillis(cost).build();
        }

        // 4. 处理 SDK 返回失败
        if (!resp.isSuccess()) {
            log.warn("[PLAYLIST_CLEAR] SN={} 清空播放列表失败 code={} data={}",
                    sn, resp.getCode(), resp.getData());
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                    .message(String.format("清空播放列表失败: %s", resp.getData()))
                    .costMillis(cost).build();
        }

        // 5. 成功
        log.info("[PLAYLIST_CLEAR] SN={} 清空播放列表成功 cost={}ms", sn, cost);
        return CommandResult.builder()
                .success(true).code(StandardErrorCode.SUCCESS)
                .message("清空播放列表成功")
                .costMillis(cost).build();
    }
}
