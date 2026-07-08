package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.operate;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.NovaViplexCoreCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ResolutionSetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 显示屏分辨率设置处理器 —— 设置终端显示屏分辨率（宽高）。
 *
 * <p>能力: {@link NovaViplexCoreCapability#DISPLAY_RESOLUTION_SET}
 * <br>SDK: {@code nvSetCustomResolutionAsync}
 * <br>JSON 格式: {@code {"sn":"...","info":{"displayMode":1,"width":1920,"height":1079}}}
 * <br>超时: 30 秒（分辨率切换耗时较长）
 * <br>当前仅 NovaViplexCore 实现。</p>
 */
@Slf4j
public class NovaViplexCoreResolutionSetHandler extends AbstractNovaViplexCoreHandler<ResolutionSetParams> {

    public NovaViplexCoreResolutionSetHandler(ViplexCoreChannel channel,
                                              ViplexProgramPipeline pipeline,
                                              NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<ResolutionSetParams> capability() {
        return NovaViplexCoreCapability.DISPLAY_RESOLUTION_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, ResolutionSetParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        int displayMode = params != null ? params.getDisplayMode() : 1;
        int width = params != null && params.getWidth() != null ? params.getWidth() : 1920;
        int height = params != null && params.getHeight() != null ? params.getHeight() : 1080;

        log.info("设置分辨率 SN={} displayMode={} width={} height={}",
                sn, displayMode, width, height);
        return executeSetResolution(sn, displayMode, width, height);
    }

    private CommandResult executeSetResolution(String sn, int displayMode, int width, int height) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildCustomResolutionJson(sn, displayMode, width, height);
            log.debug("[nvSetCustomResolution] JSON: {}", json);
            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_SET_CUSTOM_RESOLUTION_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("设置分辨率超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("设置分辨率失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("设置分辨率失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            log.info("设置分辨率成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("分辨率设置完成").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("设置分辨率异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("设置分辨率异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
