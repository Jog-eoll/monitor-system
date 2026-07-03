package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.NetworkTypeEnum;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.NetworkInfo;
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

import java.net.HttpURLConnection;
import java.util.List;

/**
 * ColorLight IP 配置 —— GET /api/network.json → 修改 LAN 条目 → POST /api/network。
 *
 * <p>遵循 API 文档规范：先获取当前完整网络配置，修改目标条目后回写。
 * 静态 IP 模式下自动通过 {@link IpConfigDefaults} 补全 mask/gateway/DNS。</p>
 */
@Slf4j
public class ColorLightIpConfigHandler extends AbstractColorLightHttpHandler<IpConfigParams> {

    public ColorLightIpConfigHandler(DeviceTransport transport,
                                     ColorLightCredentialStore credentialStore,
                                     ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<IpConfigParams> capability() {
        return CommonDeviceCapability.DEVICE_NETWORK_IP_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, IpConfigParams params) {
        // 1. 设备校验
        ImmutablePair<Boolean, CommandResult> pair = DeviceValidator.requireSingleDevice(device, params.getDeviceId());
        if (!pair.getLeft()) {
            return pair.getRight();
        }

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

        // 2. GET 当前网络配置
        ColorLightHttpResponse getResp = send(device, ColorLightApi.NETWORK_GET);
        if (getResp == null || getResp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_NET_GET_FAIL", "Failed to get current network config");
        }
        NetworkInfo current = parseJson(getResp, NetworkInfo.class);
        if (current == null || current.getTypes() == null || current.getTypes().isEmpty()) {
            return failureResult("CL_NET_PARSE_FAIL", "Failed to parse network config");
        }

        // 3. 原地修改 LAN 配置（复用 GET 响应的 NetworkInfo 作为 POST 请求体）
        List<NetworkInfo.NetworkType> types = current.getTypes();
        for (NetworkInfo.NetworkType nt : types) {
            if (nt.getType() == NetworkTypeEnum.LAN) {
                nt.setIsstatic(useStaticIp);
                if (useStaticIp) {
                    nt.setIps(NetworkInfo.IpInfo.builder()
                            .ip(resolvedParams.getIp()).mask(resolvedParams.getMask()).gateway(resolvedParams.getGateway()).build());
                }
                nt.setDns1(resolvedParams.getDns1());
                nt.setDns2(resolvedParams.getDns2());
            }
        }

        // 4. POST 新配置
        NetworkInfo payload = NetworkInfo.builder().types(types).build();
        ColorLightHttpResponse postResp = send(device, ColorLightApi.NETWORK, serializeBody(payload));
        if (postResp == null || postResp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_IP_FAIL", "Failed to configure network");
        }

        log.info("[{}] IP 配置成功 mode={} ip={} mask={} gateway={} dns1={} dns2={}",
                device.getIp(), useStaticIp ? "静态" : "DHCP",
                resolvedParams.getIp(), resolvedParams.getMask(), resolvedParams.getGateway(),
                resolvedParams.getDns1(), resolvedParams.getDns2());
        return successResult();
    }

    // ════════════════════════════════════════════════════
    // 内部辅助方法
    // ════════════════════════════════════════════════════

    private void validateIpFields(IpConfigParams params) {
        NetworkValidator.requireIpv4(params.getIp(), "IP");
        NetworkValidator.requireIpv4(params.getMask(), "子网掩码");
        NetworkValidator.requireIpv4(params.getGateway(), "网关");
        if (params.getDns1() != null) NetworkValidator.requireIpv4(params.getDns1(), "DNS1");
        if (params.getDns2() != null) NetworkValidator.requireIpv4(params.getDns2(), "DNS2");
    }
}
