package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.JsonNodeUtils;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 音量获取处理器 —— 查询终端当前音量。
 *
 * <p>SDK: {@code nvGetVolumeAsync}</p>
 */
@Slf4j
public class NovaViplexCoreVolumeGetHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCoreVolumeGetHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.VOLUME_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }
        log.info("获取音量 SN={}", sn);
        return executeQuery(sn);
    }

    private CommandResult executeQuery(String sn) {
        long start = System.currentTimeMillis();
        try {
            String json = JsonCustomMapper.get().createObjectNode()
                    .put("sn", sn).toString();
            ViplexResponse resp = channel().execute(SdkFunction.NV_GET_VOLUME_ASYNC, json, getTimeout());
            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("获取音量超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("获取音量失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("获取音量失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            JsonNode data = JsonNodeUtils.normalizeNumberFields(resp.dataAsJson());
            log.info("获取音量成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .data(data).costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("获取音量异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("获取音量异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
