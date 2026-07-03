package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreProgramHelper;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.PlaylistSetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 播放列表设置处理器 — 从终端已部署节目中选择一个激活播放。
 *
 * <p>匹配优先级：
 * <ol>
 *   <li>identifier 非空 → 直接按 identifier 匹配，失败返回异常（不回退 name）</li>
 *   <li>identifier 为空 → 按 name 匹配</li>
 * </ol>
 *
 * <p>SDK: {@code nvGetProgramInfoAsync} → {@code nvStartPlayAsync}</p>
 */
@Slf4j
public class NovaViplexCorePlaylistSetHandler extends AbstractNovaViplexCoreHandler<PlaylistSetParams> {

    public NovaViplexCorePlaylistSetHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<PlaylistSetParams> capability() {
        return CommonDeviceCapability.PLAYLIST_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, PlaylistSetParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        String identifier = params != null ? params.getIdentifier() : null;
        String name = params != null ? params.getName() : null;
        boolean hasId = StringUtils.isNotBlank(identifier);

        if (!hasId && StringUtils.isBlank(name)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                    "缺少 identifier 或 name 参数");
        }

        // 1. 查询终端节目列表
        JsonNode infos = ViplexCoreProgramHelper.queryProgramInfos(channel(), sn, getTimeout());
        if (infos == null) {
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    "查询节目列表失败");
        }

        // 2. 匹配：优先 identifier，否则 name
        if (hasId) {
            if (!ViplexCoreProgramHelper.hasProgramIdentifier(infos, identifier)) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        String.format("未找到匹配节目: identifier=%s", identifier));
            }
        } else {
            identifier = ViplexCoreProgramHelper.findProgramIdentifier(infos, name);
            if (identifier == null) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                        String.format("未找到匹配节目: name=%s", name));
            }
        }

        // 3. 激活该节目播放
        String json = JsonCustomMapper.get().createObjectNode()
                .put("sn", sn)
                .put("identifier", identifier)
                .toString();

        log.info("[PLAYLIST_SET] SN={} identifier={} 激活播放", sn, identifier);

        ViplexResponse resp = channel().execute(SdkFunction.NV_START_PLAY_ASYNC, json, getTimeout());

        if (resp.isTimeout()) return CommandResult.timeout();
        if (!resp.isSuccess()) {
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("激活播放失败: %s", ViplexErrorCode.describe(resp.getCode())));
        }

        return CommandResult.success();
    }
}
