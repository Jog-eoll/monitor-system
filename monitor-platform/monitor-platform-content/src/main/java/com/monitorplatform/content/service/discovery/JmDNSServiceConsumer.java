package com.monitorplatform.content.service.discovery;

import com.monitorplatform.content.config.JmDNSConfig;
import com.monitorplatform.content.entity.discovery.DiscoveredService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JmDNS 服务消费者
 * 负责在局域网内发现已注册的 mDNS/DNS-SD 服务（如网关、终端设备等）
 * 固定监听服务类型: _http._tcp.local.
 */
@Slf4j
@Service
public class JmDNSServiceConsumer {

    /** 固定的服务发现类型 */
    private static final String SERVICE_TYPE = "_http._tcp.local.";

    private static final Set<String> SCAN_DEVICE_TYPE_WHITELIST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "publish_gateway",
            "terminal_encrypt_gateway",
            "publish_server"
    )));

    @Resource
    private JmDNSConfig config;

    /** JmDNS 实例（全局共享） */
    private JmDNS jmdns;

    /** 监听器（单一类型） */
    private JmDNSServiceListener listener;

    /** 所有在线服务缓存 */
    private final Map<String, DiscoveredService> serviceCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("[JmDNS] 服务发现已禁用（content.jmdns.enabled=false），跳过初始化");
            return;
        }

        try {
            InetAddress localAddr = resolveBindAddress();
            jmdns = JmDNS.create(localAddr);
            log.info("[JmDNS] JmDNS 实例创建成功: bindAddr={}", localAddr);

            if (config.isStartOnBoot()) {
                startDiscovery();
            }
        } catch (IOException e) {
            log.error("[JmDNS] JmDNS 实例创建失败", e);
        }
    }

    /**
     * 启动服务发现 - 注册 _http._tcp.local. 监听器
     */
    public synchronized void startDiscovery() {
        if (jmdns == null) {
            log.warn("[JmDNS] JmDNS 实例未初始化，无法启动发现");
            return;
        }

        if (listener != null) {
            return; // 已注册
        }

        log.info("[JmDNS] 开始发现服务，监听类型: {}", SERVICE_TYPE);
        listener = createListener();
        jmdns.addServiceListener(SERVICE_TYPE, listener);
        log.info("[JmDNS] 已注册监听器: type={}", SERVICE_TYPE);
    }

    /**
     * 创建带回调的监听器
     */
    private JmDNSServiceListener createListener() {
        return new JmDNSServiceListener()
                .onServiceAdded(service -> {
                    serviceCache.put(service.getKey(), service);
                    log.info("[JmDNS] 服务上线: name={}, host={}, port={}",
                            service.getName(), service.getHostAddress(), service.getPort());
                })
                .onServiceRemoved(service -> {
                    serviceCache.remove(service.getKey());
                    log.info("[JmDNS] 服务离线: name={}, host={}",
                            service.getName(), service.getHostAddress());
                });
    }

    /**
     * 停止服务发现
     */
    public synchronized void stopDiscovery() {
        if (jmdns == null) {
            return;
        }
        if (listener != null) {
            jmdns.removeServiceListener(SERVICE_TYPE, listener);
            log.info("[JmDNS] 已移除监听器: type={}", SERVICE_TYPE);
            listener = null;
        }
        serviceCache.clear();
    }

    /**
     * 主动扫描服务
     */
    public List<DiscoveredService> scanServices() {
        if (jmdns == null) {
            log.warn("[JmDNS] JmDNS 实例未初始化");
            return Collections.emptyList();
        }

        List<DiscoveredService> result = new ArrayList<>();
        try {
            ServiceInfo[] infos = jmdns.list(SERVICE_TYPE, 3000);
            if (infos != null) {
                for (ServiceInfo info : infos) {
                    DiscoveredService service = DiscoveredService.from(info);
                    if (isAllowedScanDevice(service)) {
                        result.add(service);
                    }
                }
            }
            log.info("[JmDNS] 扫描完成: type={}, found={}", SERVICE_TYPE, result.size());
        } catch (Exception e) {
            log.error("[JmDNS] 扫描服务失败: type={}", SERVICE_TYPE, e);
        }
        return result;
    }

    private boolean isAllowedScanDevice(DiscoveredService service) {
        if (service == null || service.getProperties() == null) {
            return false;
        }

        String deviceType = service.getProperties().get("deviceType");
        if (!StringUtils.hasText(deviceType)) {
            return false;
        }

        return SCAN_DEVICE_TYPE_WHITELIST.contains(deviceType.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 获取所有已发现的在线服务
     */
    public List<DiscoveredService> getAllServices() {
        return new ArrayList<>(serviceCache.values());
    }

    /**
     * 按类型获取服务
     */
    public List<DiscoveredService> getServicesByType(String serviceType) {
        List<DiscoveredService> result = new ArrayList<>();
        for (DiscoveredService ds : serviceCache.values()) {
            if (ds.getType() != null && ds.getType().equals(serviceType)) {
                result.add(ds);
            }
        }
        return result;
    }

    /**
     * 按名称关键字过滤服务（用于查找特定网关/设备）
     */
    public List<DiscoveredService> getServicesByNameKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return Collections.emptyList();
        }
        List<DiscoveredService> result = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        for (DiscoveredService ds : serviceCache.values()) {
            if (ds.getName() != null && ds.getName().toLowerCase().contains(lowerKeyword)) {
                result.add(ds);
            }
        }
        return result;
    }

    /**
     * 按主机地址查找服务
     */
    public DiscoveredService getServiceByHost(String hostAddress) {
        if (hostAddress == null || hostAddress.isEmpty()) {
            return null;
        }
        for (DiscoveredService ds : serviceCache.values()) {
            if (hostAddress.equals(ds.getHostAddress())) {
                return ds;
            }
        }
        return null;
    }

    /**
     * 获取所有注册为网关的服务（通过配置的关键字过滤）
     */
    public List<DiscoveredService> getGatewayServices() {
        List<String> keywords = config.getGatewayNameKeywords();
        if (keywords == null || keywords.isEmpty()) {
            // 没有配置关键字则返回所有服务
            return getAllServices();
        }
        List<DiscoveredService> result = new ArrayList<>();
        for (DiscoveredService ds : serviceCache.values()) {
            String nameLower = ds.getName() != null ? ds.getName().toLowerCase() : "";
            for (String kw : keywords) {
                if (nameLower.contains(kw.toLowerCase())) {
                    result.add(ds);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 获取已发现服务数量
     */
    public int getServiceCount() {
        return serviceCache.size();
    }

    /**
     * 获取已注册监听的数量
     */
    public int getListenerCount() {
        return listener != null ? 1 : 0;
    }

    /**
     * 检查 JmDNS 是否已启用并运行
     */
    public boolean isRunning() {
        return jmdns != null && listener != null;
    }

    /**
     * 获取当前固定的服务类型
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

    @PreDestroy
    public void destroy() {
        log.info("[JmDNS] 正在关闭 JmDNS 消费者...");
        stopDiscovery();
        if (jmdns != null) {
            try {
                jmdns.close();
                log.info("[JmDNS] JmDNS 实例已关闭");
            } catch (IOException e) {
                log.error("[JmDNS] 关闭 JmDNS 实例失败", e);
            }
        }
    }
}
