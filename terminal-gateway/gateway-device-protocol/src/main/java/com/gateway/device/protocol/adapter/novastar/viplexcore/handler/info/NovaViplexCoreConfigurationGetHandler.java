package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.info;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 设备配置获取处理器 —— 获取终端配置信息。
 *
 * <p>SDK: {@code nvGetconfigurationAsync}</p>
 */
@Slf4j
public class NovaViplexCoreConfigurationGetHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCoreConfigurationGetHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return NovaViplexCoreCapability.DEVICE_CONFIGURATION;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }
        log.info("获取设备配置 SN={}", sn);
        return executeQuery(sn);
    }

    private CommandResult executeQuery(String sn) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildSnJson(sn);
            ViplexResponse resp = channel().execute(
                    SdkFunction.NV_GET_CONFIGURATION_ASYNC, json, getTimeout());
            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("获取设备配置超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("获取设备配置失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("获取设备配置失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            JsonNode data = resp.dataAsJson();
            log.info("获取设备配置成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .data(data).costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("获取设备配置异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("获取设备配置异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
