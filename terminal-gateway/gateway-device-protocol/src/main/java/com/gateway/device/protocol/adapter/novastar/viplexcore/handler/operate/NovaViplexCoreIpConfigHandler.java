package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.operate;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.DeviceValidator;
import com.gateway.device.protocol.common.IpConfigDefaults;
import com.gateway.device.protocol.common.NetworkValidator;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.IpConfigParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * NovaStar ViplexCore 设备 IP 配置处理器 —— 在线直写模式。
 *
 * <p>通过 SDK {@code nvSetEthernetInfoAsync} 直接设置设备以太网参数。
 * 模式由 IP 字段决定：提供 {@code ip} → 静态配置 (dhcp=false)，否则 → DHCP 自动获取。</p>
 *
 * <h3>SDK 字段映射</h3>
 * <ul>
 *   <li>{@code ip} → {@code ip}</li>
 *   <li>{@code netmask} → {@code mask}</li>
 *   <li>{@code gateway} → {@code gateWay}</li>
 *   <li>{@code dns} → {@code dns}（直接透传列表）</li>
 * </ul>
 */
@Slf4j
public class NovaViplexCoreIpConfigHandler extends AbstractNovaViplexCoreHandler<IpConfigParams> {

    public NovaViplexCoreIpConfigHandler(ViplexCoreChannel channel,
                                         ViplexProgramPipeline pipeline,
                                         NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<IpConfigParams> capability() {
        return CommonDeviceCapability.DEVICE_NETWORK_IP_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, IpConfigParams params) {
        ImmutablePair<Boolean, CommandResult> pair = DeviceValidator.requireSingleDevice(device, params.getDeviceId());
        if (!pair.getLeft()) {
            return pair.getRight();
        }

        String sn = device.getSn();

        final boolean useStaticIp = StringUtils.isNotBlank(params.getIp());

        IpConfigParams resolvedParams;
        if (useStaticIp) {
            try {
                resolvedParams = IpConfigDefaults.apply(params);
                validateIpFields(resolvedParams);
            } catch (IllegalArgumentException e) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM, e.getMessage());
            }
        } else {
            resolvedParams = params;
        }

        log.info("设置设备IP SN={} mode={} ip={} mask={} gateway={} dns1={} dns2={}",
                sn, useStaticIp ? "静态" : "DHCP",
                resolvedParams.getIp(), resolvedParams.getMask(), resolvedParams.getGateway(),
                resolvedParams.getDns1(), resolvedParams.getDns2());

        return executeSetEthernetInfo(sn, resolvedParams, useStaticIp);
    }

    // ════════════════════════════════════════════════════════════
    // 核心调用
    // ════════════════════════════════════════════════════════════

    private CommandResult executeSetEthernetInfo(String sn, IpConfigParams params, boolean useStaticIp) {
        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildEthernetInfoJson(sn, params, useStaticIp);
            log.debug("[nvSetEthernetInfo] JSON: {}", json);

            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_SET_ETHERNET_INFO_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("设置 IP 超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("设置 IP 失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("设置 IP 失败: %s, %s", ViplexErrorCode.describe(resp.getCode()), resp.getData()))
                        .costMillis(cost).build();
            }

            log.info("设置 IP 成功 SN={}", sn);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .message("IP 配置已生效").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("设置 IP 异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("设置 IP 异常: %s", e.getMessage())).costMillis(cost).build();
        }
    }

    // ════════════════════════════════════════════════════════════
    // 字段校验
    // ════════════════════════════════════════════════════════════

    private void validateIpFields(IpConfigParams params) {
        NetworkValidator.requireIpv4(params.getIp(), "IP");
        NetworkValidator.requireIpv4(params.getMask(), "子网掩码");
        NetworkValidator.requireIpv4(params.getGateway(), "网关");
        if (params.getDns1() != null) NetworkValidator.requireIpv4(params.getDns1(), "DNS1");
        if (params.getDns2() != null) NetworkValidator.requireIpv4(params.getDns2(), "DNS2");
    }
}
