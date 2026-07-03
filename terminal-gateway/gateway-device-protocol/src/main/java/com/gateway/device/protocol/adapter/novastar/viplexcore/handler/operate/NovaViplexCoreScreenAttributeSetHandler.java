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
import com.gateway.device.protocol.model.params.ScreenAttributeParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

/**
 * 显示屏点阵配屏处理器 —— 设置 LED 模组物理点阵（接收卡带载宽高）。
 *
 * <p>能力: {@link CommonDeviceCapability#SCREEN_ATTRIBUTE_SET}
 * <br>SDK: {@code nvSetScreenAttributeAsync}
 * <br>JSON 格式: {@code {"sn":"...","screenAttribute":{"screenAttributes":[{"id":0,"screenSource":1,"xCount":1,"yCount":1,"xOffset":0,"yOffset":0,"portNumber":1,"orders":[0,1],"scanInfos":[{"width":96,"height":96,"x":0,"y":0,"xInPort":0,"yInPort":0,"portIndex":0,"connectIndex":0}]}]}}
 * <br>超时: 30 秒（配屏耗时较长）
 * <br>区别于 {@code DISPLAY_RESOLUTION_SET}：本功能设置物理 LED 点阵，而非视频输出分辨率。</p>
 */
@Slf4j
public class NovaViplexCoreScreenAttributeSetHandler extends AbstractNovaViplexCoreHandler<ScreenAttributeParams> {

    public NovaViplexCoreScreenAttributeSetHandler(ViplexCoreChannel channel,
                                                   ViplexProgramPipeline pipeline,
                                                   NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<ScreenAttributeParams> capability() {
        return CommonDeviceCapability.SCREEN_ATTRIBUTE_SET;
    }

    @Override
    protected Duration getTimeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    public CommandResult execute(DeviceContext device, ScreenAttributeParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        if (params == null || params.getWidth() == null || params.getHeight() == null) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少 width/height 参数");
        }

        log.info("设置点阵配屏 SN={} width={} height={}",
                sn, params.getWidth(), params.getHeight());
        return executeSetScreenAttribute(sn, params);
    }

    private CommandResult executeSetScreenAttribute(String sn, ScreenAttributeParams params) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildScreenAttributeJson(sn, params);
            log.debug("[nvSetScreenAttribute] JSON: {}", json);
            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_SET_SCREEN_ATTRIBUTE_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("设置点阵配屏超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("设置点阵配屏失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("设置点阵配屏失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            log.info("设置点阵配屏成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("点阵配屏设置完成").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("设置点阵配屏异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("设置点阵配屏异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
