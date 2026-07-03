package com.gateway.device.protocol.common;

import com.google.common.net.InetAddresses;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;

/**
 * 网络地址校验工具 —— 基于 Guava {@link InetAddresses}，严格限制 IPv4。
 */
public final class NetworkValidator {

    private NetworkValidator() {
    }

    /**
     * 严格校验是否为合法 IPv4 地址（拒绝 IPv6、主机名、非法格式）。
     */
    public static boolean isValidIpv4(String ip) {
        if (StringUtils.isEmpty(ip)) return false;
        try {
            return InetAddresses.forString(ip) instanceof Inet4Address;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 校验并抛异常。
     *
     * @throws IllegalArgumentException IP 非法或非 IPv4
     */
    public static void requireIpv4(String ip, String fieldName) {
        if (!isValidIpv4(ip)) {
            throw new IllegalArgumentException(fieldName + " 无效 IPv4 地址: " + ip);
        }
    }
}
