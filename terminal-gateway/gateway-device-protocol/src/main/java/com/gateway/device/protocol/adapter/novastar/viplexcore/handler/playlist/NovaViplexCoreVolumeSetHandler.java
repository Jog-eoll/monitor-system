package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.ValueClamp;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.VolumeSetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 音量设置处理器 — 设置终端音量。
 *
 * <p>SDK: {@code nvSetVolumeAsync}
 * <br>参数: ratio(0-100 音量百分比，默认 60)
 */
@Slf4j
public class NovaViplexCoreVolumeSetHandler extends AbstractNovaViplexCoreHandler<VolumeSetParams> {

    public NovaViplexCoreVolumeSetHandler(ViplexCoreChannel channel,
                                          ViplexProgramPipeline pipeline,
                                          NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<VolumeSetParams> capability() {
        return CommonDeviceCapability.VOLUME_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, VolumeSetParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        int ratio = ValueClamp.ratio(params != null ? params.getRatio() : 60);

        log.info("设置音量 SN={} ratio={}", sn, ratio);
        return executeSet(sn, ratio);
    }

    private CommandResult executeSet(String sn, double ratio) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildVolumeJson(sn, ratio);

            ViplexResponse resp = channel().execute(SdkFunction.NV_SET_VOLUME_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("设置音量超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("设置音量失败 SN={} ratio={} code={} data={}", sn, ratio, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("设置音量失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            log.info("设置音量成功 SN={} ratio={}", sn, ratio);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("音量设置完成").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("设置音量异常 SN={} ratio={}", sn, ratio, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("设置音量异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
