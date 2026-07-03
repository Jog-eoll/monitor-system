package com.infopublish.client.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class ClientIdResolver {

    public static final String DEFAULT_CLIENT_ID = "info-publish-client-001";

    private static final Log log = LogFactory.getLog(ClientIdResolver.class);

    private ClientIdResolver() {
    }

    public static String resolve(Environment environment) {
        String configuredClientId = environment == null ? null : environment.getProperty("monitor-platform.client-id");
        if (!isDefaultOrBlank(configuredClientId)) {
            return configuredClientId.trim();
        }

        String registryClientId = environment == null ? null : environment.getProperty("registry.client.client-id");
        if (!isDefaultOrBlank(registryClientId)) {
            return registryClientId.trim();
        }

        return generateClientId();
    }

    public static boolean isDefaultOrBlank(String clientId) {
        return clientId == null
                || clientId.trim().isEmpty()
                || DEFAULT_CLIENT_ID.equals(clientId.trim());
    }

    private static String generateClientId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback()
                        || networkInterface.isVirtual()
                        || !networkInterface.isUp()) {
                    continue;
                }
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null && mac.length >= 3) {
                    String suffix = String.format("%02x%02x%02x",
                            mac[mac.length - 3] & 0xff,
                            mac[mac.length - 2] & 0xff,
                            mac[mac.length - 1] & 0xff);
                    return "ipc-" + suffix;
                }
            }
        } catch (Exception e) {
            log.warn("[ClientId] get mac address failed, fallback to hostname hash: " + e.getMessage());
        }

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            int hash = Math.abs(hostname.hashCode()) % 1000000;
            return String.format("ipc-%06d", hash);
        } catch (Exception e) {
            log.warn("[ClientId] get hostname failed, fallback to current time: " + e.getMessage());
            return "ipc-" + System.currentTimeMillis() % 1000000;
        }
    }
}
