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
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 设备信息查询处理器 —— 调用 ViplexCore SDK 获取固件/产品信息。
 *
 * <p>使用 {@code nvGetFirmwareInfosAsync}（全平台通用），
 * 返回 {@code {productName, mainVersion, model, mac, aliasName, fpga, registerAddress}}</p>
 */
@Slf4j
public class NovaViplexCoreDeviceInfoGetHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCoreDeviceInfoGetHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.DEVICE_INFO_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }
        log.info("获取设备信息 SN={}", sn);
        return executeQuery(sn);
    }

    private CommandResult executeQuery(String sn) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildSnJson(sn);
            ViplexResponse resp = channel().execute(
                    SdkFunction.NV_GET_FIRMWARE_INFOS_ASYNC, json, getTimeout());
            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("获取设备信息超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("获取设备信息失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("获取设备信息失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            JsonNode data = resp.dataAsJson();
            log.info("获取设备信息成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .data(data).costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("获取设备信息异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("获取设备信息异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
