package com.gateway.device.protocol.common;

import com.gateway.device.protocol.model.params.IpConfigParams;
import com.google.common.net.InetAddresses;
import org.apache.commons.lang3.StringUtils;

/**
 * IP 配置默认值策略 —— 根据 IP 段自动补全 mask/gateway/DNS。
 *
 * <pre>
 * 192.168.1.X1      → mask=255.255.255.0  gateway=192.168.1.1
 * 192.168.X2.X1     → mask=255.255.255.0  gateway=192.168.X2.1  (X2≠1)
 * 跨 C 段网关       → mask=255.255.0.0    (用户指定不同C段网关且未指定掩码时自动放宽)
 * 其他段            → mask/gateway 必填
 * DNS 未配置        → ["119.29.29.29", "223.5.5.5"]
 * </pre>
 */
public final class IpConfigDefaults {

    private static final String DEFAULT_DNS2 = "119.29.29.29";

    private IpConfigDefaults() {
    }

    /**
     * 根据 IP 段自动补全缺失的 mask/gateway/DNS。
     *
     * @throws IllegalArgumentException 非 192.168.x.x 段且 mask 或 gateway 缺失
     */
    public static IpConfigParams apply(IpConfigParams params) {
        String ip = params.getIp();
        if (StringUtils.isBlank(ip)) {
            return params; // DHCP 模式，无需补全
        }

        byte[] octets = InetAddresses.forString(ip).getAddress();
        int a = octets[0] & 0xFF;
        int b = octets[1] & 0xFF;
        int c = octets[2] & 0xFF;

        String mask = params.getMask();
        String gateway = params.getGateway();
        String dns1 = params.getDns1();
        String dns2 = params.getDns2();

        if (a == 192 && b == 168) {
            String userMask = mask;
            String userGateway = gateway;

            mask = StringUtils.isNotBlank(userMask) ? userMask : "255.255.255.0";
            gateway = StringUtils.isNotBlank(userGateway) ? userGateway : "192.168." + c + ".1";

            // 优化：用户指定了跨 C 段网关但未手动指定掩码时，自动放宽为 /16
            if (StringUtils.isNotBlank(userGateway) && StringUtils.isBlank(userMask)) {
                try {
                    int gwC = Integer.parseInt(userGateway.split("\\.")[2]);
                    if (gwC != c) {
                        mask = "255.255.0.0";
                    }
                } catch (Exception ignore) {
                    // 网关解析失败则保持默认 /24 掩码
                }
            }
        } else {
            if (StringUtils.isBlank(mask)) {
                throw new IllegalArgumentException("非 192.168.x.x 段 IP 必须指定子网掩码：" + ip);
            }
            if (StringUtils.isBlank(gateway)) {
                throw new IllegalArgumentException("非 192.168.x.x 段 IP 必须指定网关：" + ip);
            }
        }

        if (StringUtils.isBlank(dns1)) {
            dns1 = gateway;
        }

        if (StringUtils.isBlank(dns2)) {
            dns2 = DEFAULT_DNS2;
        }

        return IpConfigParams.builder()
                .deviceId(params.getDeviceId())
                .ip(ip).mask(mask)
                .gateway(gateway)
                .dns1(dns1)
                .dns2(dns2)
                .build();
    }
}
