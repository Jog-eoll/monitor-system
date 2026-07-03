package com.monitorplatform.registry.client.component;

import com.monitorplatform.registry.client.ServiceRegistryClient;
import com.monitorplatform.registry.client.config.RegistryClientProperties;
import com.monitorplatform.registry.client.dto.ServiceRegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import javax.annotation.PreDestroy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ServiceAutoRegister implements ApplicationRunner, Ordered {
    
    private final RegistryClientProperties properties;
    private final ServiceRegistryClient serviceRegistryClient;
    private final Environment environment;
    
    private ScheduledExecutorService heartbeatExecutor;
    private String instanceId;
    private String registryInstanceId;
    
    public ServiceAutoRegister(RegistryClientProperties properties,
                               ServiceRegistryClient serviceRegistryClient,
                               Environment environment) {
        this.properties = properties;
        this.serviceRegistryClient = serviceRegistryClient;
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("服务注册客户端未启用");
            return;
        }
        
        if (!properties.isAutoRegister()) {
            log.info("服务自动注册已禁用");
            return;
        }
        
        try {
            // 自动探测 IP 和 MAC（取自同一张物理网卡，yml 配置优先）
            autoDetectNetworkInfo();
            
            // 自动获取端口（yml 配置优先）
            if (properties.getPort() == null) {
                properties.setPort(getServerPort());
            }
            
            // 生成实例ID
            instanceId = properties.getServiceName() + "-" + properties.getHost() + "-" + properties.getPort();
            registryInstanceId = resolveRegistryInstanceId();
            
            log.info("设备信息 - host: {}, port: {}, mac: {}, deviceType: {}, location: {}, version: {}", 
                    properties.getHost(), properties.getPort(), 
                    properties.getMacAddress(), properties.getDeviceType(),
                    properties.getLocation(), properties.getVersion());
            
            // 尝试注册
            if (registerWithRetry()) {
                startHeartbeat();
            }
            
        } catch (Exception e) {
            log.error("服务自动注册失败", e);
        }
    }
    
    /**
     * 自动探测宿主机 IP 和 MAC 地址（取自同一张物理网卡）
     * 优先级：yml 配置 > 自动探测
     * 探测策略：
     *   1. 收集所有符合条件的网卡候选（排除已知虚拟网卡）
     *   2. 按"物理优先 + 有MAC优先 + eth/enp/wlan 命名优先"排序
     *   3. 优先匹配 192.168.x.x，其次 site-local，最后 link-local
     *   4. 兜底通过 /proc/net/route 或 shell 命令获取默认路由网卡
     */
    private void autoDetectNetworkInfo() {
        boolean needHost = properties.getHost() == null || properties.getHost().isEmpty();
        boolean needMac = properties.getMacAddress() == null || properties.getMacAddress().isEmpty();
        
        if (!needHost && !needMac) {
            return;
        }
        
        try {
            // 先探测默认路由出口信息（用于同网卡多 IP 时优先选择默认路由出口 IP）
            DefaultRouteInfo defaultRouteInfo = detectDefaultRouteInfo();
            
            // 收集并排序所有候选网卡
            List<NicCandidate> candidates = collectNicCandidates(defaultRouteInfo);
            
            if (!candidates.isEmpty()) {
                // 按 IP 地址优先级排序：192.168.x.x > 10.x/172.x > link-local
                candidates.sort(this::compareNicPriority);
                
                NicCandidate best = candidates.get(0);
                if (needHost && best.ipAddress != null) {
                    properties.setHost(best.ipAddress);
                }
                if (needMac && best.macAddress != null) {
                    properties.setMacAddress(best.macAddress);
                }
                log.info("自动探测宿主机网卡: {} -> IP={}, MAC={}", 
                        best.interfaceName, properties.getHost(), properties.getMacAddress());
                return;
            }
            
            // 兜底：尝试通过默认路由获取网卡
            if (tryDefaultRouteNic(needHost, needMac)) {
                return;
            }
            
            // 最后兜底：使用 getLocalHost（但排除 127.0.0.1）
            if (needHost) {
                String localHost = InetAddress.getLocalHost().getHostAddress();
                if ("127.0.0.1".equals(localHost)) {
                    log.error("getLocalHost 返回 127.0.0.1，无法获取有效宿主机 IP，请在 yml 中手动配置 registry.client.host");
                } else {
                    properties.setHost(localHost);
                    log.warn("降级使用 getLocalHost: {}", localHost);
                }
            }
        } catch (Exception e) {
            log.error("自动探测网络信息失败", e);
        }
    }
    
    /**
     * 网卡候选对象
     */
    private static class NicCandidate {
        String interfaceName;
        String ipAddress;
        String macAddress;
        boolean isPhysicalName;  // 网卡名是否像物理网卡（eth/enp/wlan/enp0s/enp1s 等）
        boolean hasMac;
        boolean isSiteLocal;
        boolean is192168;
        boolean isLinkLocal;
        boolean isPreferredNetwork;  // 匹配 preferredNetwork 配置
        boolean isDefaultRoute;      // 是默认路由出口 IP
    }
    
    /**
     * 默认路由信息（探测结果缓存）
     */
    private static class DefaultRouteInfo {
        String interfaceName;  // 默认路由出口网卡名
        String sourceIp;       // 默认路由出口源 IP
    }
    
    /**
     * 探测默认路由出口信息（Linux: 读 /proc/net/route，其他系统: 尝试 connect 探测）
     */
    private DefaultRouteInfo detectDefaultRouteInfo() {
        DefaultRouteInfo info = new DefaultRouteInfo();
        
        // 方式1：读取 /proc/net/route（Linux）
        try {
            java.io.File routeFile = new java.io.File("/proc/net/route");
            if (routeFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(routeFile.toPath());
                for (String line : lines) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2 && "00000000".equals(parts[1])) {
                        info.interfaceName = parts[0];
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取 /proc/net/route 失败", e);
        }
        
        // 方式2：通过 UDP connect 探测本机出口 IP（跨平台，不实际发送数据）
        try {
            java.net.DatagramSocket socket = new java.net.DatagramSocket();
            socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 53);
            info.sourceIp = socket.getLocalAddress().getHostAddress();
            socket.close();
        } catch (Exception e) {
            log.debug("UDP connect 探测出口 IP 失败", e);
        }
        
        if (info.interfaceName != null || info.sourceIp != null) {
            log.info("默认路由探测: iface={}, sourceIp={}", info.interfaceName, info.sourceIp);
        }
        return info;
    }
    
    /**
     * 收集所有候选网卡
     */
    private List<NicCandidate> collectNicCandidates(DefaultRouteInfo defaultRouteInfo) throws Exception {
        List<NicCandidate> candidates = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        
        if (interfaces == null) {
            return candidates;
        }
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            
            // 跳过回环和未启用
            if (ni.isLoopback() || !ni.isUp()) {
                continue;
            }
            
            String niName = ni.getName().toLowerCase();
            
            // 跳过已知的虚拟/容器网卡
            if (isVirtualInterface(niName)) {
                log.debug("跳过虚拟/容器网络接口: {}", ni.getName());
                continue;
            }
            
            // 获取 MAC 地址（允许为 null，不再强制要求）
            byte[] hardwareAddress = null;
            try {
                hardwareAddress = ni.getHardwareAddress();
            } catch (Exception e) {
                log.debug("获取 MAC 地址异常: {} - {}", ni.getName(), e.getMessage());
            }
            String macStr = (hardwareAddress != null && hardwareAddress.length > 0) 
                    ? formatMac(hardwareAddress) : null;
            
            // 遍历该网卡的所有 IPv4 地址
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            boolean hasValidIp = false;
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (!(addr instanceof Inet4Address)) {
                    continue;
                }
                
                // 跳过 127.x.x.x
                if (addr.isLoopbackAddress()) {
                    continue;
                }
                
                NicCandidate candidate = new NicCandidate();
                candidate.interfaceName = ni.getName();
                candidate.ipAddress = addr.getHostAddress();
                candidate.macAddress = macStr;
                candidate.hasMac = macStr != null;
                candidate.isPhysicalName = isPhysicalNicName(niName);
                candidate.isSiteLocal = addr.isSiteLocalAddress();
                candidate.is192168 = addr.getHostAddress().startsWith("192.168.");
                candidate.isLinkLocal = addr.isLinkLocalAddress();
                // 匹配 preferredNetwork 配置（如 "192.168.1."）
                candidate.isPreferredNetwork = isPreferredNetworkMatch(addr.getHostAddress());
                // 是否为默认路由出口 IP
                candidate.isDefaultRoute = isDefaultRouteIp(ni.getName(), addr.getHostAddress(), defaultRouteInfo);
                
                // 接受 site-local 和 link-local（link-local 优先级最低）
                if (candidate.isSiteLocal || candidate.isLinkLocal) {
                    candidates.add(candidate);
                    hasValidIp = true;
                }
            }
            
            // 如果该网卡没有 site-local / link-local 地址但有公网 IP，也加入候选
            if (!hasValidIp) {
                Enumeration<InetAddress> addrAgain = ni.getInetAddresses();
                while (addrAgain.hasMoreElements()) {
                    InetAddress addr = addrAgain.nextElement();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    NicCandidate candidate = new NicCandidate();
                    candidate.interfaceName = ni.getName();
                    candidate.ipAddress = addr.getHostAddress();
                    candidate.macAddress = macStr;
                    candidate.hasMac = macStr != null;
                    candidate.isPhysicalName = isPhysicalNicName(niName);
                    candidate.isSiteLocal = false;
                    candidate.is192168 = false;
                    candidate.isLinkLocal = false;
                    candidate.isPreferredNetwork = isPreferredNetworkMatch(addr.getHostAddress());
                    candidate.isDefaultRoute = isDefaultRouteIp(ni.getName(), addr.getHostAddress(), defaultRouteInfo);
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }
    
    /**
     * 判断是否为已知的虚拟/容器网卡名
     */
    private boolean isVirtualInterface(String name) {
        if (name == null) return false;
        // Docker 相关
        if (name.startsWith("docker") || name.startsWith("br-") 
                || name.startsWith("veth") || name.startsWith("vnic")) {
            return true;
        }
        // KVM/libvirt 虚拟网桥
        if (name.startsWith("virbr")) {
            return true;
        }
        // Kubernetes / CNI 网络插件
        if (name.startsWith("flannel") || name.startsWith("cni") 
                || name.startsWith("cali") || name.startsWith("tunl")) {
            return true;
        }
        // VPN / 隧道
        if (name.startsWith("tun") || name.startsWith("tap") 
                || name.startsWith("vpn")) {
            return true;
        }
        // Windows 虚拟网卡
        // Hyper-V 虚拟交换机: "vEthernet", "HNS"
        // WSL: "vEthernet (WSL)", "WSL"
        // VMware: "VMware"
        // Windows 显示名中文名匹配（通过 name 或 displayName 判断）
        String nameLower = name.toLowerCase();
        if (nameLower.contains("hyper-v") || nameLower.contains("vmware")
                || nameLower.contains("vethernet") || nameLower.contains("wsl")
                || nameLower.contains("vpn") || nameLower.contains("loopback")) {
            return true;
        }
        // 网桥（br0 通常是系统桥接，由其他规则判断）
        if (name.equals("br0") || name.startsWith("bond")) {
            return false; // bond/br0 可能是物理网卡绑定，保留
        }
        // lo 回环
        if (name.equals("lo")) {
            return true;
        }
        return false;
    }
    
    /**
     * 判断网卡名是否像物理网卡命名
     * Linux 物理网卡常见命名：
     *   - enp0s25, enp1s0, eno1, ens33 (systemd predictable names)
     *   - eth0, eth1 (传统命名)
     *   - wlan0, wlp2s0 (无线)
     *   - enx001122334455 (USB 网卡)
     * Windows：
     *   - getName(): eth0, net0, wlan0 等内部名
     *   - getName() 可能是 net+数字 格式
     */
    private boolean isPhysicalNicName(String name) {
        if (name == null) return false;
        // Linux 命名
        if (name.startsWith("eth") || name.startsWith("enp") 
                || name.startsWith("eno") || name.startsWith("ens")
                || name.startsWith("wlan") || name.startsWith("wlp")
                || name.startsWith("enx")) {
            return true;
        }
        // Windows 命名：net0, net1 等（NetworkInterface.getName() 在 Windows 上返回的格式）
        if (name.startsWith("net") && name.length() <= 5) {
            return true;
        }
        return false;
    }
    
    /**
     * 网卡优先级比较
     * 排序规则（优先级从高到低）：
     *   1. preferredNetwork 配置匹配（最高优先，用户显式指定）
     *   2. 默认路由出口 IP（内核选定的对外通信 IP）
     *   3. 物理网卡名优先于非物理名
     *   4. 有 MAC 优先于无 MAC
     *   5. 192.168.x.x 优先于其他 site-local
     *   6. site-local 优先于 link-local
     *   7. link-local 优先于公网 IP
     */
    private int compareNicPriority(NicCandidate a, NicCandidate b) {
        // 1. preferredNetwork 配置匹配（最高优先）
        int cmp = Boolean.compare(b.isPreferredNetwork, a.isPreferredNetwork);
        if (cmp != 0) return cmp;
        
        // 2. 默认路由出口 IP
        cmp = Boolean.compare(b.isDefaultRoute, a.isDefaultRoute);
        if (cmp != 0) return cmp;
        
        // 3. 物理网卡名优先
        cmp = Boolean.compare(b.isPhysicalName, a.isPhysicalName);
        if (cmp != 0) return cmp;
        
        // 4. 有 MAC 优先
        cmp = Boolean.compare(b.hasMac, a.hasMac);
        if (cmp != 0) return cmp;
        
        // 5. 192.168 优先
        cmp = Boolean.compare(b.is192168, a.is192168);
        if (cmp != 0) return cmp;
        
        // 6. site-local 优先于 link-local
        cmp = Boolean.compare(b.isSiteLocal, a.isSiteLocal);
        if (cmp != 0) return cmp;
        
        // 7. link-local 优先于公网
        cmp = Boolean.compare(b.isLinkLocal, a.isLinkLocal);
        if (cmp != 0) return cmp;
        
        return 0;
    }
    
    /**
     * 判断 IP 是否匹配 preferredNetwork 配置
     */
    private boolean isPreferredNetworkMatch(String ipAddress) {
        String preferred = properties.getPreferredNetwork();
        if (preferred == null || preferred.isEmpty() || ipAddress == null) {
            return false;
        }
        return ipAddress.startsWith(preferred);
    }
    
    /**
     * 判断 IP 是否为默认路由出口 IP
     */
    private boolean isDefaultRouteIp(String interfaceName, String ipAddress, DefaultRouteInfo defaultRouteInfo) {
        if (defaultRouteInfo == null) {
            return false;
        }
        // 优先匹配 sourceIp（UDP connect 探测结果最准确）
        if (defaultRouteInfo.sourceIp != null && defaultRouteInfo.sourceIp.equals(ipAddress)) {
            return true;
        }
        // 其次匹配 interfaceName（/proc/net/route 探测结果）
        if (defaultRouteInfo.interfaceName != null && defaultRouteInfo.interfaceName.equals(interfaceName)) {
            // 同一网卡有多个 IP 时，仅当 sourceIp 也匹配时才返回 true
            // 如果没有 sourceIp 信息，则该网卡上的第一个 site-local IP 也算默认路由
            if (defaultRouteInfo.sourceIp == null) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 通过默认路由获取网卡信息（兜底方案）
     * 当 collectNicCandidates 未找到任何候选时使用
     */
    private boolean tryDefaultRouteNic(boolean needHost, boolean needMac) {
        try {
            // 尝试读取 /proc/net/route
            java.io.File routeFile = new java.io.File("/proc/net/route");
            if (!routeFile.exists()) {
                return false;
            }
            
            List<String> lines = java.nio.file.Files.readAllLines(routeFile.toPath());
            String defaultIface = null;
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2 && "00000000".equals(parts[1])) {
                    // 目标 00000000 表示默认路由
                    defaultIface = parts[0];
                    break;
                }
            }
            
            if (defaultIface == null) {
                return false;
            }
            
            log.info("从默认路由获取网卡: {}", defaultIface);
            
            // 通过网卡名获取 NetworkInterface
            NetworkInterface ni = NetworkInterface.getByName(defaultIface);
            if (ni == null || ni.isLoopback() || !ni.isUp()) {
                return false;
            }
            
            // 获取 IP
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                    continue;
                }
                if (needHost) {
                    properties.setHost(addr.getHostAddress());
                }
                if (needMac) {
                    byte[] hw = ni.getHardwareAddress();
                    if (hw != null && hw.length > 0) {
                        properties.setMacAddress(formatMac(hw));
                    }
                }
                log.info("默认路由网卡: {} -> IP={}, MAC={}", 
                        defaultIface, properties.getHost(), properties.getMacAddress());
                return true;
            }
        } catch (Exception e) {
            log.debug("通过默认路由获取网卡失败", e);
        }
        return false;
    }
    
    /**
     * 格式化 MAC 地址字节数组为 XX-XX-XX-XX-XX-XX 格式
     */
    private String formatMac(byte[] hardwareAddress) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hardwareAddress.length; i++) {
            sb.append(String.format("%02X", hardwareAddress[i]));
            if (i < hardwareAddress.length - 1) {
                sb.append("-");
            }
        }
        return sb.toString();
    }
    
    private Integer getServerPort() {
        // 尝试从环境变量获取端口
        String portStr = environment.getProperty("server.port");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                log.warn("解析端口失败: {}", portStr);
            }
        }
        
        // 尝试从 servlet 上下文获取
        String localServerPort = environment.getProperty("local.server.port");
        if (localServerPort != null && !localServerPort.isEmpty()) {
            try {
                return Integer.parseInt(localServerPort);
            } catch (NumberFormatException e) {
                log.warn("解析本地端口失败: {}", localServerPort);
            }
        }
        
        // 默认端口
        log.warn("未配置端口，使用默认端口 8080");
        return 8080;
    }
    
    // getMacAddress 已由 autoDetectNetworkInfo() 替代，不再单独使用
    
    private boolean registerWithRetry() {
        int retryCount = 0;
        while (retryCount <= properties.getRegisterRetryTimes()) {
            if (registerService()) {
                return true;
            }
            retryCount++;
            if (retryCount <= properties.getRegisterRetryTimes()) {
                log.warn("注册失败，{}ms 后重试 ({}/{})", properties.getRegisterRetryInterval(), retryCount, properties.getRegisterRetryTimes());
                try {
                    Thread.sleep(properties.getRegisterRetryInterval());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return false;
    }
    
    private boolean registerService() {
        try {
            ServiceRegisterRequest request = new ServiceRegisterRequest();
            request.setClientId(resolveClientId());
            request.setServiceName(properties.getServiceName());
            request.setInstanceId(registryInstanceId);
            request.setHost(properties.getHost());
            request.setPort(properties.getPort());
            request.setMacAddress(properties.getMacAddress());
            request.setDeviceType(properties.getDeviceType());
            // 新增字段（yml 配置优先）
            request.setLocation(properties.getLocation());
            request.setVersion(properties.getVersion());
            request.setManufacturer(properties.getManufacturer());
            request.setModel(properties.getModel());
            request.setRemark(properties.getRemark());
            
            boolean success = serviceRegistryClient.register(request);
            if (success) {
                log.info("设备注册成功: {}", instanceId);
            } else {
                log.error("设备注册失败: {}", instanceId);
            }
            return success;
        } catch (Exception e) {
            log.error("设备注册异常", e);
            return false;
        }
    }

    private String resolveClientId() {
        if (properties.getClientId() != null && !properties.getClientId().trim().isEmpty()) {
            return properties.getClientId().trim();
        }
        String clientId = environment.getProperty("monitor-platform.client-id");
        return clientId == null ? null : clientId.trim();
    }

    private String resolveRegistryInstanceId() {
        String clientId = resolveClientId();
        if (clientId != null && !clientId.trim().isEmpty()) {
            return clientId.trim();
        }
        return instanceId;
    }
    
    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                boolean success = serviceRegistryClient.heartbeat(registryInstanceId);
                if (!success) {
                    log.warn("心跳失败: {}", instanceId);
                }
            } catch (Exception e) {
                log.error("心跳异常", e);
            }
        }, properties.getHeartbeatInterval(), properties.getHeartbeatInterval(), TimeUnit.MILLISECONDS);
        log.info("心跳任务启动，间隔: {}ms", properties.getHeartbeatInterval());
    }
    
    @PreDestroy
    public void destroy() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            try {
                heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (properties.isAutoDeregister() && registryInstanceId != null) {
            boolean success = serviceRegistryClient.deregister(registryInstanceId);
            if (success) {
                log.info("设备注销成功: {}", instanceId);
            }
        }
    }
}
