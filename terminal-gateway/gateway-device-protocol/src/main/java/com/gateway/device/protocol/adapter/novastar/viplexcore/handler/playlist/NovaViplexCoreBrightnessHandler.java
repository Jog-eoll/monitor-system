package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonNodeUtils;
import com.gateway.device.protocol.common.ValueClamp;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.BrightnessSetParams;
import org.apache.commons.lang3.StringUtils;

/**
 * 亮度控制处理器 —— 获取/设置屏幕亮度。
 *
 * <p>SDK: {@code nvGetScreenBrightnessAsync} / {@code nvSetScreenBrightnessAsync}
 * <br>参数: action("GET"/"SET"), ratio(0-100 亮度百分比)</p>
 */
public class NovaViplexCoreBrightnessHandler extends AbstractNovaViplexCoreHandler<BrightnessSetParams> {

    public NovaViplexCoreBrightnessHandler(ViplexCoreChannel channel,
                                           ViplexProgramPipeline pipeline,
                                           NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<BrightnessSetParams> capability() {
        return CommonDeviceCapability.BRIGHTNESS_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, BrightnessSetParams params) {
        BrightnessSetParams.BrightnessAction action = params != null && params.getAction() != null
                ? params.getAction() : BrightnessSetParams.BrightnessAction.GET;

        if (BrightnessSetParams.BrightnessAction.SET.equals(action)) {
            return executeSet(device, params);
        }
        return executeGet(device);
    }

    private CommandResult executeGet(DeviceContext device) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure("INVALID_PARAM", "设备 SN 缺失");
        }

        String json = ViplexCoreJsonBuilder.buildSnJson(sn);

        ViplexResponse resp = channel()
                .execute(SdkFunction.NV_GET_SCREEN_BRIGHTNESS_ASYNC, json, getTimeout());

        if (resp.isTimeout()) return CommandResult.timeout();
        if (!resp.isSuccess()) {
            return CommandResult.failure("PROTOCOL_ERROR",
                    String.format("获取亮度失败: %s", ViplexErrorCode.describe(resp.getCode())));
        }

        JsonNode data = JsonNodeUtils.normalizeNumberFields(resp.dataAsJson(), "ratio", "brightness");
        return CommandResult.success(data);
    }

    private CommandResult executeSet(DeviceContext device, BrightnessSetParams p) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure("INVALID_PARAM", "设备 SN 缺失");
        }

        int ratio = p != null ? ValueClamp.ratio(p.getRatio()) : 80;

        String json = ViplexCoreJsonBuilder.buildBrightnessSetJson(sn, ratio);

        ViplexResponse resp = channel()
                .execute(SdkFunction.NV_SET_SCREEN_BRIGHTNESS_ASYNC, json, getTimeout());

        if (resp.isTimeout()) return CommandResult.timeout();
        if (!resp.isSuccess()) {
            return CommandResult.failure("PROTOCOL_ERROR",
                    String.format("设置亮度失败: %s", ViplexErrorCode.describe(resp.getCode())));
        }
        return CommandResult.success();
    }
}
