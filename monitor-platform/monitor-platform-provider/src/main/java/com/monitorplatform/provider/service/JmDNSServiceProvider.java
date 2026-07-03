package com.monitorplatform.provider.service;

import com.monitorplatform.provider.config.JmDNSConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * JmDNS 服务提供者（可复用库）
 * 其他服务引入此模块后，注入本 Bean，自行构造 props 调用 register() 注册到局域网 mDNS
 *
 * <pre>
 * 使用示例：
 *   &#064;Resource
 *   private JmDNSServiceProvider jmdnsProvider;
 *
 *   Map&lt;String, String&gt; props = new HashMap&lt;&gt;();
 *   props.put("description", "设备管理服务");
 *   props.put("path", "/device/health");
 *   props.put("version", "1.0.0");
 *   jmdnsProvider.register("monitor-device", 8062, props);
 * </pre>
 */
@Slf4j
@Service
public class JmDNSServiceProvider {

    /** 固定的 mDNS 服务类型 */
    public static final String SERVICE_TYPE = "_http._tcp.local.";

    @Resource
    private JmDNSConfig config;

    /** JmDNS 实例（懒初始化） */
    private volatile JmDNS jmdns;

    /** JmDNS 绑定地址 */
    private volatile InetAddress bindAddress;

    /** 注册的 ServiceInfo */
    private ServiceInfo registeredService;

    /** 注册状态 */
    private volatile boolean registered = false;

    /**
     * 确保 JmDNS 实例已初始化
     */
    private JmDNS ensureJmDNS() {
        if (jmdns == null) {
            synchronized (this) {
                if (jmdns == null) {
                    if (!config.isEnabled()) {
                        log.warn("[JmDNS-Provider] 已禁用，跳过初始化");
                        return null;
                    }
                    try {
                        InetAddress localAddr = resolveBindAddress();
                        jmdns = JmDNS.create(localAddr);
                        bindAddress = localAddr;
                        log.info("[JmDNS-Provider] JmDNS 实例创建成功: bindAddr={}", localAddr.getHostAddress());
                    } catch (IOException e) {
                        log.error("[JmDNS-Provider] JmDNS 实例创建失败", e);
                        return null;
                    }
                }
            }
        }
        return jmdns;
    }

    /**
     * 向局域网注册本服务（props 由调用方自行构造）
     *
     * @param serviceName 服务名称（mDNS 显示名）
     * @param port        服务端口
     * @param props       自定义属性（TXT 记录），如 description、path、version 等
     * @return 是否注册成功
     */
    public synchronized boolean register(String serviceName, int port, Map<String, String> props) {
        JmDNS jmDNSInstance = ensureJmDNS();
        if (jmDNSInstance == null) {
            log.warn("[JmDNS-Provider] JmDNS 未初始化，无法注册");
            return false;
        }

        // 先反注册旧服务（如果存在），避免残留
        if (registered) {
            deregister();
        }

        try {
            InetAddress localAddr = bindAddress != null ? bindAddress : resolveBindAddress();
            String hostAddress = resolveAdvertiseHost(localAddr);

            Map<String, String> mergedProps = new HashMap<>();
            if (props != null) {
                mergedProps.putAll(props);
            }
            mergedProps.putIfAbsent("host", hostAddress);

            ServiceInfo info = ServiceInfo.create(
                    SERVICE_TYPE,
                    serviceName,
                    port,
                    0, 0,   // weight, priority
                    mergedProps
            );

            jmDNSInstance.registerService(info);
            registeredService = info;
            registered = true;

            log.info("[JmDNS-Provider] ==========================================");
            log.info("[JmDNS-Provider] 服务注册成功！");
            log.info("[JmDNS-Provider]   类型: {}", SERVICE_TYPE);
            log.info("[JmDNS-Provider]   名称: {}", serviceName);
            log.info("[JmDNS-Provider]   地址: {}:{}", hostAddress, port);
            log.info("[JmDNS-Provider]   属性: {}", mergedProps);
            log.info("[JmDNS-Provider] ==========================================");

            return true;
        } catch (IOException e) {
            log.error("[JmDNS-Provider] 服务注册失败: serviceName={}, port={}", serviceName, port, e);
            return false;
        }
    }

    /**
     * 取消注册（优雅下线）
     */
    public synchronized boolean deregister() {
        if (jmdns == null || !registered) {
            return true;
        }

        try {
            jmdns.unregisterService(registeredService);
            registered = false;
            registeredService = null;
            log.info("[JmDNS-Provider] 服务已取消注册");
            return true;
        } catch (Exception e) {
            log.error("[JmDNS-Provider] 取消注册失败", e);
            return false;
        }
    }

    /**
     * 更新已注册服务的属性（重新注册）
     *
     * @param serviceName 服务名称
     * @param port        服务端口
     * @param newProps    新的属性
     * @return 是否更新成功
     */
    public synchronized boolean updateProperties(String serviceName, int port, Map<String, String> newProps) {
        if (jmdns == null || !registered) {
            log.warn("[JmDNS-Provider] 服务未注册，无法更新属性");
            return false;
        }

        try {
            InetAddress localAddr = bindAddress != null ? bindAddress : resolveBindAddress();
            String hostAddress = resolveAdvertiseHost(localAddr);
            Map<String, String> mergedProps = new HashMap<>(newProps);
            mergedProps.putIfAbsent("host", hostAddress);

            ServiceInfo updatedInfo = ServiceInfo.create(
                    SERVICE_TYPE,
                    serviceName,
                    port,
                    0, 0,
                    mergedProps
            );

            jmdns.registerService(updatedInfo);
            registeredService = updatedInfo;
            log.info("[JmDNS-Provider] 服务属性已更新: {}", newProps);
            return true;
        } catch (IOException e) {
            log.error("[JmDNS-Provider] 更新服务属性失败", e);
            return false;
        }
    }

    /**
     * 获取注册状态
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * 获取服务类型
     */
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    private InetAddress resolveBindAddress() throws IOException {
        if (StringUtils.hasText(config.getBindAddress())) {
            return InetAddress.getByName(config.getBindAddress().trim());
        }

        InetAddress candidate = findUsableIpv4Address();
        if (candidate != null) {
            return candidate;
        }

        return InetAddress.getLocalHost();
    }

    private InetAddress findUsableIpv4Address() throws SocketException {
        InetAddress fallback = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp()
                    || networkInterface.isLoopback()
                    || networkInterface.isVirtual()
                    || isIgnoredInterface(networkInterface)) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (!(address instanceof Inet4Address)
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()) {
                    continue;
                }
                if (address.isSiteLocalAddress()) {
                    return address;
                }
                if (fallback == null) {
                    fallback = address;
                }
            }
        }
        return fallback;
    }

    private boolean isIgnoredInterface(NetworkInterface networkInterface) {
        return isIgnoredInterfaceName(networkInterface.getName())
                || isIgnoredInterfaceName(networkInterface.getDisplayName());
    }

    private boolean isIgnoredInterfaceName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String lowerName = name.toLowerCase();
        return lowerName.startsWith("docker")
                || lowerName.startsWith("br-")
                || lowerName.startsWith("veth")
                || lowerName.startsWith("virbr")
                || lowerName.startsWith("cni")
                || lowerName.startsWith("flannel")
                || lowerName.startsWith("cali")
                || lowerName.startsWith("tun")
                || lowerName.startsWith("tap");
    }

    private String resolveAdvertiseHost(InetAddress localAddr) {
        if (StringUtils.hasText(config.getAdvertiseHost())) {
            return config.getAdvertiseHost().trim();
        }
        return localAddr.getHostAddress();
    }

    @PreDestroy
    public void destroy() {
        log.info("[JmDNS-Provider] 正在关闭服务提供者...");
        deregister();
        if (jmdns != null) {
            try {
                jmdns.close();
                log.info("[JmDNS-Provider] JmDNS 实例已关闭");
            } catch (IOException e) {
                log.error("[JmDNS-Provider] 关闭 JmDNS 实例失败", e);
            }
        }
    }
}
