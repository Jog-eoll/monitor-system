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
import com.gateway.device.protocol.model.params.NtpSetParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * NTP 服务器配置处理器 —— 通过 {@code nvSetNetTimingInfoAsync} 下发 NTP 服务器地址。
 */
@Slf4j
public class NovaViplexCoreNtpSetHandler extends AbstractNovaViplexCoreHandler<NtpSetParams> {

    public NovaViplexCoreNtpSetHandler(ViplexCoreChannel channel,
                                       ViplexProgramPipeline pipeline,
                                       NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<NtpSetParams> capability() {
        return CommonDeviceCapability.NTP_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, NtpSetParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        String ntpServer = params.resolveNtpServer();
        log.info("NTP 配置 SN={} ntpServer={}", sn, ntpServer);
        return executeNtpConfig(sn, ntpServer);
    }

    private CommandResult executeNtpConfig(String sn, String ntpServer) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildNtpConfigJson(sn, ntpServer);
            log.debug("[nvSetNetTimingInfo] JSON: {}", json);
            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_SET_NET_TIMING_INFO_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("NTP 配置超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("NTP 配置失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("NTP 配置失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }
            log.info("NTP 配置成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("NTP 服务器已配置").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("NTP 配置异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("NTP 配置异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }
}
