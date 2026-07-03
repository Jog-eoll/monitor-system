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
import com.gateway.device.protocol.model.params.TimeSyncParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 时间同步处理器 —— 通过 {@code nvCalibrateTimeAsync} 校时。
 *
 * <p>提供 {@code targetTime} 时用指定时间，否则用当前系统时间。</p>
 */
@Slf4j
public class NovaViplexCoreTimeSyncHandler extends AbstractNovaViplexCoreHandler<TimeSyncParams> {

    public NovaViplexCoreTimeSyncHandler(ViplexCoreChannel channel,
                                         ViplexProgramPipeline pipeline,
                                         NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    private static ZoneId resolveTimeZone(TimeSyncParams params) {
        if (params != null && params.getTimeZone() != null) {
            return params.getTimeZone();
        }
        return ZoneId.systemDefault();
    }

    @Override
    public DeviceCapability<TimeSyncParams> capability() {
        return CommonDeviceCapability.TIME_SYNC;
    }

    @Override
    public CommandResult execute(DeviceContext device, TimeSyncParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        ZoneId zone = resolveTimeZone(params);
        ZonedDateTime zdt = params != null && params.getTargetTime() != null
                ? params.getTargetTime().atZone(zone)
                : ZonedDateTime.now(zone);

        log.info("校时 SN={} targetTime={} zone={}",
                sn, zdt, zone);
        return executeCalibrateTime(sn, zdt);
    }

    private CommandResult executeCalibrateTime(String sn, ZonedDateTime zdt) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildCalibrateTimeJson(sn, zdt);
            log.debug("[nvCalibrateTime] JSON: {}", json);
            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_CALIBRATE_TIME_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("校时超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("校时失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("校时失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            log.info("校时成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("校时完成").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("校时异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("校时异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
