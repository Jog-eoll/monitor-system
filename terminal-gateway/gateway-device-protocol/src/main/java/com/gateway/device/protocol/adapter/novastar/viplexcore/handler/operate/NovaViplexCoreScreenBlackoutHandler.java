package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.operate;

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
import com.gateway.device.protocol.model.params.ScreenBlackoutParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 黑屏控制处理器 —— 调用 ViplexCore SDK 开关屏体电源实现黑屏/亮屏。
 *
 * <p>能力: {@link CommonDeviceCapability#SCREEN_BLACKOUT}
 * <br>SDK: {@code nvSetScreenPowerStateAsync}
 * <br>JSON 格式: {@code {"sn":"xxx","taskInfo":{"state":"CLOSE"|"OPEN"}}}</p>
 */
@Slf4j
public class NovaViplexCoreScreenBlackoutHandler extends AbstractNovaViplexCoreHandler<ScreenBlackoutParams> {

    public NovaViplexCoreScreenBlackoutHandler(ViplexCoreChannel channel,
                                               ViplexProgramPipeline pipeline,
                                               NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<ScreenBlackoutParams> capability() {
        return CommonDeviceCapability.SCREEN_BLACKOUT;
    }

    @Override
    public CommandResult execute(DeviceContext device, ScreenBlackoutParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        String state = params.isBlackout() ? "CLOSE" : "OPEN";

        log.info("设置黑屏 SN={} state={}", sn, state);
        return executeSet(sn, state);
    }

    private CommandResult executeSet(String sn, String state) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildScreenPowerJson(sn, state);

            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_SET_SCREEN_POWER_STATE_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("设置黑屏超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("设置黑屏失败 SN={} state={} code={} data={}", sn, state, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("设置黑屏失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            log.info("设置黑屏成功 SN={} state={}", sn, state);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("黑屏设置完成").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("设置黑屏异常 SN={} state={}", sn, state, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("设置黑屏异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
