package com.gateway.device.protocol.common.discovery;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.util.SubnetUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CIDR 子网展开工具 —— 委托 Apache Commons Net {@link SubnetUtils}。
 *
 * <p>将 {@code "192.168.1.0/24"} 展开为可用主机 IP 列表（排除网络地址和广播地址）。</p>
 */
@Slf4j
public final class CidrExpander {

    private CidrExpander() {
    }

    /**
     * 展开单个 CIDR 为可用主机 IP 列表（排除网络地址和广播地址）。
     *
     * @param cidr CIDR 表示法，如 "192.168.1.0/24"
     * @return 可用主机 IP 列表，解析失败返回空列表
     */
    public static List<String> expand(String cidr) {
        try {
            SubnetUtils utils = new SubnetUtils(cidr);
            utils.setInclusiveHostCount(false); // 排除网络地址和广播地址
            return Arrays.asList(utils.getInfo().getAllAddresses());
        } catch (Exception e) {
            log.debug("CIDR 解析失败: {}", cidr, e);
            return Collections.emptyList();
        }
    }

    /**
     * 展开多个 CIDR 子网为 IP 列表（去重，保留插入顺序）。
     *
     * @param subnets CIDR 列表
     * @return 合并后的 IP 列表
     */
    public static List<String> expandAll(List<String> subnets) {
        if (subnets == null || subnets.isEmpty()) return Collections.emptyList();
        List<String> all = new ArrayList<>();
        for (String cidr : subnets) {
            List<String> ips = expand(cidr.trim());
            if (ips.isEmpty()) {
                log.warn("无效 CIDR 子网: {}", cidr);
            }
            all.addAll(ips);
        }
        return all;
    }
}
