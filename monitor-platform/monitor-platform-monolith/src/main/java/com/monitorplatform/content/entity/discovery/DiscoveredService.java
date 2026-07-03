package com.monitorplatform.content.entity.discovery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.jmdns.ServiceInfo;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JmDNS 发现的服务实例封装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredService {

    /** 服务类型，如 _http._tcp.local. */
    private String type;

    /** 服务名称（实例名） */
    private String name;

    /** 服务IP地址 */
    private String hostAddress;

    /** 服务端口 */
    private int port;

    /** 服务发现时间戳 */
    private long discoveredAt;

    /** 服务属性/元数据 */
    private Map<String, String> properties;

    /** 是否为在线状态 */
    private boolean online;

    /**
     * 从 JmDNS ServiceInfo 构建
     */
    public static DiscoveredService from(ServiceInfo info) {
        DiscoveredServiceBuilder builder = DiscoveredService.builder()
                .type(info.getType())
                .name(info.getName())
                .port(info.getPort())
                .online(true)
                .discoveredAt(System.currentTimeMillis());

        InetAddress[] addresses = info.getInetAddresses();
        if (addresses != null && addresses.length > 0) {
            builder.hostAddress(addresses[0].getHostAddress());
        }

        builder.properties(extractProperties(info));

        return builder.build();
    }

    private static Map<String, String> extractProperties(ServiceInfo info) {
        if (info == null) {
            return Collections.emptyMap();
        }

        Map<String, String> props = new LinkedHashMap<>();
        Enumeration<String> names = info.getPropertyNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            String value = info.getPropertyString(name);
            props.put(name.trim(), value == null ? "" : value);
        }
        return props;
    }

    /**
     * 通过服务名和类型生成唯一 key
     */
    public String getKey() {
        return name + "@" + type;
    }
}
