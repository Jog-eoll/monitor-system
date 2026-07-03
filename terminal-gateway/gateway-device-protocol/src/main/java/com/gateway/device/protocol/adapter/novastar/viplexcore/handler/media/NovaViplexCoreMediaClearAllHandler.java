package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.media;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 清除全部媒体处理器 — 清除终端全部媒体文件。
 *
 * <p>SDK: {@code nvClearAllMediasAsync}
 * <br><b>注意: 此操作不可逆，会清除终端上全部媒体文件。</b></p>
 */
@Slf4j
public class NovaViplexCoreMediaClearAllHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCoreMediaClearAllHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.MEDIA_CLEAR;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        log.warn("[MEDIA_CLEAR] SN={} 将清除终端全部媒体", sn);

        String json = ViplexCoreJsonBuilder.buildSnJson(sn);

        ViplexResponse resp;
        try {
            resp = channel().execute(SdkFunction.NV_CLEAR_ALL_MEDIAS_ASYNC, json, getTimeout());
        } catch (Exception e) {
            log.error("[MEDIA_CLEAR] SN={} 清除媒体异常", sn, e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, String.format("清除媒体异常: %s", e.getMessage()));
        }

        if (resp.isTimeout()) return CommandResult.timeout();
        if (resp.getCode() != 0) {
            log.error("[MEDIA_CLEAR] SN={} nvClearAllMediasAsync 返回错误 code={} data={}",
                    sn, resp.getCode(), resp.getData());
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("清除媒体失败: %s", ViplexErrorCode.describe(resp.getCode())));
        }

        log.info("[MEDIA_CLEAR] SN={} 终端全部媒体已清除", sn);
        return CommandResult.success();
    }
}
