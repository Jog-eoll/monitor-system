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
import com.gateway.device.protocol.common.capability.expand.NovaViplexCoreCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ApNetworkSwitchParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * AP 热点开关处理器 —— 开启/关闭终端 WiFi 热点。
 *
 * <p>能力: {@link NovaViplexCoreCapability#AP_NETWORK_SWITCH}
 * <br>SDK: {@code nvSetAPNetworkOpenStatusAsync}
 * <br>JSON 格式: {@code {"sn":"xxx","enable":true|false}}</p>
 */
@Slf4j
public class NovaViplexCoreApNetworkSwitchHandler extends AbstractNovaViplexCoreHandler<ApNetworkSwitchParams> {

    public NovaViplexCoreApNetworkSwitchHandler(ViplexCoreChannel channel,
                                                ViplexProgramPipeline pipeline,
                                                NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<ApNetworkSwitchParams> capability() {
        return CommonDeviceCapability.DEVICE_NETWORK_AP_SWITCH;
    }

    @Override
    public CommandResult execute(DeviceContext device, ApNetworkSwitchParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        boolean enable = params.isEnable();
        log.info("设置 AP 热点 SN={} enable={}", sn, enable);

        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildApSwitchJson(sn, enable);

            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_SET_AP_NETWORK_OPEN_STATUS_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("设置 AP 热点超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("设置 AP 热点失败 SN={} enable={} code={} data={}",
                        sn, enable, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("设置 AP 热点失败: %s, %s",
                                ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }

            log.info("设置 AP 热点成功 SN={} enable={}", sn, enable);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message(enable ? "AP 热点已开启" : "AP 热点已关闭")
                    .costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("设置 AP 热点异常 SN={} enable={}", sn, enable, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("设置 AP 热点异常: %s", e.getMessage()))
                    .costMillis(cost).build();
        }
    }
}
