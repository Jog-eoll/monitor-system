package com.gateway.device.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.core.config.DiscoveryProperties;
import com.gateway.device.core.event.DeviceDiscoveredEvent;
import com.gateway.device.core.executor.DeviceCommandExecutor;
import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.protocol.api.*;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.discovery.ComplianceGroupConfig;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import com.gateway.device.protocol.model.discovery.ExplicitIpDiscoveredDevice;
import com.gateway.device.transport.netty.NettyTransportManager;
import com.gateway.device.transport.netty.NettyTransportManager.BroadcastResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 自动广播发现服务 —— 启动后定时扫描局域网设备，查询信息并校验合规性，通过后自动注册。
 *
 * <p>配置驱动：映射端口/IP→厂商，厂商→产品合规规则。
 * 同一厂商不同产品通过 compliance yaml 配置区分。</p>
 */
@Slf4j
public class AutoDiscoveryService {

    private static final int INITIAL_DELAY_SEC = 5;

    private final DiscoveryProperties properties;
    private final DeviceManagementService deviceManagementService;
    private final ProtocolRouter router;
    private final TaskScheduler taskScheduler;
    private final Map<DeviceVendor, DeviceComplianceValidator> validators;
    private final Map<DeviceVendor, DeviceDiscoveryProvider> discoveryProviders;
    private final Map<DeviceVendor, DeviceInfoEnricher> enrichers;
    private final Map<DeviceVendor, DeviceRegistrationProvider> registrationProviders;
    private final NettyTransportManager transportManager;
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceCommandExecutor commandExecutor;
    private final AtomicBoolean scanning = new AtomicBoolean();

    public AutoDiscoveryService(DiscoveryProperties properties,
                                DeviceManagementService deviceManagementService,
                                ProtocolRouter router,
                                TaskScheduler taskScheduler,
                                List<DeviceComplianceValidator> validatorList,
                                List<DeviceDiscoveryProvider> providerList,
                                List<DeviceInfoEnricher> enricherList,
                                List<DeviceRegistrationProvider> regProviderList,
                                NettyTransportManager transportManager,
                                ApplicationEventPublisher eventPublisher,
                                DeviceCommandExecutor commandExecutor) {
        this.properties = properties;
        this.deviceManagementService = deviceManagementService;
        this.router = router;
        this.taskScheduler = taskScheduler;
        this.validators = new EnumMap<>(DeviceVendor.class);
        for (DeviceComplianceValidator v : validatorList) {
            validators.put(v.vendor(), v);
        }
        this.discoveryProviders = new EnumMap<>(DeviceVendor.class);
        for (DeviceDiscoveryProvider p : providerList) {
            discoveryProviders.put(p.vendor(), p);
        }
        this.enrichers = new EnumMap<>(DeviceVendor.class);
        for (DeviceInfoEnricher e : enricherList) {
            enrichers.put(e.vendor(), e);
        }
        this.registrationProviders = new EnumMap<>(DeviceVendor.class);
        for (DeviceRegistrationProvider rp : regProviderList) {
            registrationProviders.put(rp.vendor(), rp);
        }
        this.transportManager = transportManager;
        this.eventPublisher = eventPublisher;
        this.commandExecutor = commandExecutor;
    }

    private static String attr(Map<String, Object> attrs, String primary, String fallback) {
        Object v = attrs.get(primary);
        if (v != null) return v.toString();
        v = attrs.get(fallback);
        return v != null ? v.toString() : null;
    }

    private static String firstAttr(Map<String, Object> attrs, String... keys) {
        for (String key : keys) {
            Object v = attrs.get(key);
            if (v != null) return v.toString();
        }
        return null;
    }

    private static Integer attrInt(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("自动发现已禁用");
            return;
        }
        taskScheduler.scheduleWithFixedDelay(
                this::scan,
                Instant.now().plusSeconds(INITIAL_DELAY_SEC),
                Duration.ofMinutes(properties.getScanIntervalMinutes()));
        log.info("自动发现已启动, 扫描间隔 {} 分钟", properties.getScanIntervalMinutes());
    }

    // ════════════════════════════════════════════════════
    // 内部
    // ════════════════════════════════════════════════════

    /**
     * 执行一轮扫描
     */
    public void scan() {
        if (!scanning.compareAndSet(false, true)) {
            log.debug("上一轮扫描尚未完成，跳过");
            return;
        }
        try {
            doScan();
        } finally {
            scanning.set(false);
        }
    }

    private void doScan() {
        List<DeviceVendorMapping> mappings = properties.getMappings();
        Set<String> discoveredIps = new HashSet<>();

        // 0. 显式 IP 注册（优先，跳过广播/SDK 发现）
        for (DeviceVendorMapping m : mappings) {
            if (!m.hasIps()) continue;
            for (String ip : m.getIps()) {
                // 从注册表按 IP 查找 deviceId，检查是否忙碌
                String registeredId = deviceManagementService.listAll().stream()
                        .filter(d -> ip.equals(d.getIp()))
                        .findFirst()
                        .map(DeviceContext::getDeviceId).orElse(null);
                if (registeredId != null && commandExecutor.isBusy(registeredId)) {
                    log.debug("[{}] 设备忙碌中，跳过显式 IP 注册", ip);
                    continue;
                }
                try {
                    ExplicitIpDiscoveredDevice dd = new ExplicitIpDiscoveredDevice(ip, m.effectivePort());
                    discoveredIps.add(ip);
                    registerDevice(dd, m);
                } catch (Exception e) {
                    log.error("[{}] 显式 IP 注册失败: {}", ip, e.getMessage());
                }
            }
        }

        // 1. byte-UDP 广播扫描（排除 hasIps 的映射，按端口分组）
        List<DeviceVendorMapping> broadcastMappings = mappings.stream()
                .filter(m -> !m.hasIps() && !m.isSdk())
                .collect(Collectors.toList());

        Map<Integer, List<DeviceVendorMapping>> portGroups = broadcastMappings.stream()
                .collect(Collectors.groupingBy(
                        DeviceVendorMapping::effectivePort));

        for (Map.Entry<Integer, List<DeviceVendorMapping>> entry : portGroups.entrySet()) {
            try {
                scanPort(entry.getKey(), entry.getValue(), discoveredIps);
            } catch (Exception e) {
                log.error("扫描端口 {} 失败: {}", entry.getKey(), e.getMessage(), e);
            }
        }

        // 2. SDK 直连扫描（排除 hasIps 的映射）
        List<DeviceVendorMapping> sdkMappings = mappings.stream()
                .filter(m -> !m.hasIps() && m.isSdk())
                .collect(Collectors.toList());

        for (DeviceVendorMapping m : sdkMappings) {
            DeviceDiscoveryProvider provider = discoveryProviders.get(m.getVendor());
            if (provider == null) continue;

            try {
                List<DiscoveredDevice> devices = provider.discover(m.getTimeoutMs());
                Set<String> seen = new HashSet<>();
                for (DiscoveredDevice dd : devices) {
                    if (m.hasBlockIps() && m.getBlockIps().contains(dd.getIp())) {
                        log.debug("[{}] IP 在屏蔽列表中，跳过", dd.getIp());
                        continue;
                    }
                    String key = resolveDeviceId(dd);
                    if (!seen.add(key)) {
                        log.debug("[{}] SDK 扫描跳过重复设备: {}", m.getVendor(), key);
                        continue;
                    }
                    if (commandExecutor.isBusy(key)) {
                        log.debug("[{}] 设备忙碌中，跳过 SDK 注册", dd.getIp());
                        continue;
                    }
                    discoveredIps.add(dd.getIp());
                    registerDevice(dd, m);
                }
            } catch (Exception e) {
                log.error("[{}] SDK 扫描失败: {}", m.getVendor(), e.getMessage());
            }
        }

        if (properties.isMarkOfflineWhenMissing()) {
            for (DeviceContext d : deviceManagementService.listAll()) {
                if (d.isOnline() && !discoveredIps.contains(d.getIp())) {
                    deviceManagementService.markOffline(d.getDeviceId());
                log.warn("[{}] 设备离线（扫描未发现）", d.getIp());
                }
            }
        } else {
            log.debug("自动发现未启用缺失设备离线标记，本轮发现 {} 个 IP", discoveredIps.size());
        }
    }

    private void scanPort(int port, List<DeviceVendorMapping> portMappings, Set<String> discoveredIps) {
        // 收集本端口组所有映射的 blockIps 并集
        Set<String> blockedIps = portMappings.stream()
                .filter(DeviceVendorMapping::hasBlockIps)
                .flatMap(m -> m.getBlockIps().stream())
                .collect(Collectors.toSet());

        Set<DeviceVendor> vendors = portMappings.stream()
                .map(DeviceVendorMapping::getVendor).collect(Collectors.toSet());

        for (DeviceVendor vendor : vendors) {
            DeviceVendorMapping bestMapping = portMappings.stream()
                    .filter(m -> m.getVendor() == vendor)
                    .findFirst().orElse(null);
            if (bestMapping == null) continue;

            DeviceDiscoveryProvider provider = discoveryProviders.get(vendor);
            if (provider == null) continue;

            List<DiscoveredDevice> devices;
            if (provider.buildBroadcastRequest() != null) {
                // UDP 广播 (JetFileII, NovaStar)
                try {
                    devices = broadcastDiscover(vendor, bestMapping, port);
                } catch (Exception e) {
                    log.warn("[{}] 端口 {} 广播失败: {}", vendor, port, e.getMessage());
                    continue;
                }
            } else {
                // 无广播 → 回退 TCP 探测 (ColorLight)
                log.info("[{}] 无广播能力，回退 discover() 探测, 超时 {}ms",
                        vendor, bestMapping.getTimeoutMs());
                try {
                    devices = provider.discover(bestMapping.getTimeoutMs());
                } catch (Exception e) {
                    log.warn("[{}] discover() 探测失败: {}", vendor, e.getMessage());
                    continue;
                }
            }

            for (DiscoveredDevice dd : devices) {
                if (!blockedIps.isEmpty() && blockedIps.contains(dd.getIp())) {
                    log.debug("[{}] IP 在屏蔽列表中，跳过", dd.getIp());
                    continue;
                }
                discoveredIps.add(dd.getIp());
                DeviceVendorMapping mapping = resolveMapping(dd, portMappings);
                if (mapping == null) {
                    log.debug("[{}:{}] 无匹配映射，跳过", dd.getIp(), dd.getSourcePort());
                    continue;
                }

                // 前置过滤：设备忙碌则跳过，避免并发请求干扰
                String deviceId = resolveDeviceId(dd);
                if (commandExecutor.isBusy(deviceId)) {
                    log.debug("[{}:{}] 设备忙碌中，跳过本次扫描", dd.getIp(), dd.getSourcePort());
                    continue;
                }
                DeviceContext existing = deviceManagementService.get(deviceId).orElse(null);
                if (existing != null) {
                    boolean vendorChanged = existing.getVendor() != mapping.getVendor();
                    DeviceDiscoveryProvider identityProvider = discoveryProviders.get(mapping.getVendor());
                    boolean identityChanged = checkIdentityChanged(existing, dd, identityProvider);

                    if (vendorChanged || identityChanged) {
                        log.info("[{}] 设备身份变更 (vendor={}, identity={})，强制重注册",
                                dd.getIp(), vendorChanged, identityChanged);
                    } else {
                        deviceManagementService.markOnline(deviceId);
                        log.debug("[{}] 设备无变更，在线", dd.getIp());
                        continue;
                    }
                }
                registerDevice(dd, mapping);
            }
        }
    }

    /**
     * 广播搜索局域网内设备。
     */
    private List<DiscoveredDevice> broadcastDiscover(DeviceVendor vendor,
                                                     DeviceVendorMapping mapping, int port) {
        DeviceDiscoveryProvider provider = discoveryProviders.get(vendor);
        if (provider == null) {
            log.warn("无 {} 厂商发现提供者", vendor);
            return Collections.emptyList();
        }

        int timeoutMs = mapping.getTimeoutMs();
        byte[] request = provider.buildBroadcastRequest();
        String broadcastHost = mapping.effectiveBroadcastHost();
        InetSocketAddress target = new InetSocketAddress(broadcastHost, port);

        log.debug("广播搜索 {} 设备: {}:{}, 超时 {}ms", vendor, broadcastHost, port, timeoutMs);
        List<BroadcastResponse> responses;
        try {
            responses = transportManager.broadcastAndCollect(
                            request, target, Duration.ofMillis(timeoutMs))
                    .get(timeoutMs + 2000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("广播搜索 {} 失败: {}", vendor, e.getMessage());
            return Collections.emptyList();
        }

        Map<String, DiscoveredDevice> seen = new LinkedHashMap<>();
        for (BroadcastResponse r : responses) {
            DiscoveredDevice dd = provider.parseReply(
                    r.getData(), r.getSenderIp(), r.getSenderPort());
            if (dd != null) {
                Map<String, Object> attrs = dd.getAttributes();
                String key = attrs.containsKey("sn") && attrs.get("sn") != null
                        ? String.valueOf(attrs.get("sn"))
                        : dd.getIp();
                seen.putIfAbsent(key, dd);
            }
        }

        List<DiscoveredDevice> result = new ArrayList<>(seen.values());
        result.sort(Comparator.comparing(DiscoveredDevice::getIp));
        log.info("发现 {} 个 {} 设备", result.size(), vendor);
        return result;
    }

    private boolean checkIdentityChanged(DeviceContext existing, DiscoveredDevice dd,
                                         DeviceDiscoveryProvider provider) {
        if (provider == null) return false;
        List<String> keys = provider.identityKeys();
        if (keys.isEmpty()) return false;
        Map<String, Object> newAttrs = dd.getAttributes();
        Map<String, Object> existingAttrs = existing.getAttributes();
        if (existingAttrs == null) return true;
        return keys.stream().anyMatch(k -> {
            Object newVal = newAttrs.get(k);
            Object oldVal = existingAttrs.get(k);
            if ("macAddr".equals(k) || "mac".equals(k)) {
                String newStr = newVal != null ? ProtocolConstant.formatMac(newVal.toString()) : null;
                String oldStr = oldVal != null ? ProtocolConstant.formatMac(oldVal.toString()) : null;
                return !Objects.equals(newStr, oldStr);
            }
            return !Objects.equals(oldVal, newVal);
        });
    }

    /**
     * 为发现的设备选择最具体的映射（IP+端口 > IP > 端口）
     */
    private DeviceVendorMapping resolveMapping(DiscoveredDevice dd, List<DeviceVendorMapping> mappings) {
        DeviceVendorMapping best = null;
        int bestScore = -1;
        for (DeviceVendorMapping m : mappings) {
            int score = 0;
            boolean portMatch = m.effectivePort() == dd.getSourcePort();
            if (portMatch) score += 1;
            if (m.hasIps() && m.getIps().contains(dd.getIp())) score += 2;
            if (score > bestScore) {
                best = m;
                bestScore = score;
            }
        }
        return bestScore > 0 ? best : null;
    }

    /**
     * 执行完整设备注册流水线，供 {@link IpRegistrationService} 等外部调用。
     *
     * @param dd      发现设备
     * @param mapping 厂商映射配置
     * @return 注册成功的 DeviceContext，失败返回 null
     */
    public DeviceContext registerDevice(DiscoveredDevice dd, DeviceVendorMapping mapping) {
        DeviceVendor vendor = mapping.getVendor();
        String ip = dd.getIp();

        // 兜底过滤：所有调用路径的最前置守护
        if (commandExecutor.isBusy(resolveDeviceId(dd))) {
            log.debug("[{}] 设备忙碌中，跳过本次注册", ip);
            return null;
        }

        try {
            VendorProtocolAdapter adapter = router.findByVendor(vendor);
            if (adapter == null) {
                log.warn("[{}] 无 {} 厂商适配器", ip, vendor);
                return null;
            }

            DeviceContext tempCtx = buildTempContext(dd, vendor, adapter);

            DeviceRegistrationProvider regProvider = registrationProviders.get(vendor);
            if (regProvider == null) {
                log.warn("[{}] 无 {} 注册信息提供者", ip, vendor);
                return null;
            }
            Object infoData = regProvider.fetchRegistrationInfo(tempCtx);
            if (infoData == null) {
                log.warn("[{}] 设备注册信息获取失败", ip);
                return null;
            }

            // 日志原始设备信息数据，用于分析模型字段映射覆盖度
            if (infoData instanceof byte[]) {
                log.debug("[{}] 设备原始信息(bytes): {} bytes", ip, ((byte[]) infoData).length);
            } else if (infoData instanceof JsonNode) {
                log.debug("[{}] 设备原始信息(JSON): {}", ip, infoData);
            } else {
                log.debug("[{}] 设备原始信息: {}", ip, infoData);
            }

            String groupLabel = validateCompliance(vendor, tempCtx, infoData);
            if (groupLabel == null) {
                log.warn("[{}] 合规校验不通过，跳过注册", ip);
                return null;
            }

            Map<String, Object> attrs = new LinkedHashMap<>(dd.getAttributes());
            DeviceInfoEnricher enricher = enrichers.get(vendor);
            if (enricher != null && infoData instanceof byte[]) {
                enricher.enrich(dd, (byte[]) infoData, attrs);
            } else if (enricher != null && infoData instanceof JsonNode) {
                enricher.enrichJson(dd, (JsonNode) infoData, attrs);
            }

            String deviceId = resolveDeviceId(attrs, ip);
            DeviceContext device = DeviceContext.builder()
                    .deviceId(deviceId)
                    .ip(ip)
                    .port(dd.getSourcePort())
                    .vendor(vendor)
                    .transportType(adapter.transportType())
                    .groupLabel(groupLabel)
                    .online(true)
                    .capabilities(adapter.capabilities())
                    .attributes(attrs)
                    .width(attrInt(attrs, "width"))
                    .height(attrInt(attrs, "height"))
                    .sn(firstAttr(attrs, "serialNo", "sn", "serialno"))
                    .macAddr(ProtocolConstant.formatMac(firstAttr(attrs, "macAddr", "mac")))
                    .build();
            deviceManagementService.register(device);
            regProvider.postRegister(device);
            eventPublisher.publishEvent(new DeviceDiscoveredEvent(this,
                    Collections.singletonList(device)));
            log.info("[{}] 发现并注册设备: 厂商={} 分组={} 宽={} 高={} 属性={}",
                    ip, vendor, groupLabel, device.getWidth(), device.getHeight(), attrs);
            return device;

        } catch (Exception e) {
            log.error("[{}] 处理设备失败: {}", ip, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 合规校验，返回匹配的产品类型；不通过返回 null。
     *
     * <p>显式分组按 YAML 书写顺序优先判定，全部不匹配时 fallback 到 default 分组（若启用）。</p>
     */
    private String validateCompliance(DeviceVendor vendor, DeviceContext device, Object infoData) {
        LinkedHashMap<String, ComplianceGroupConfig> productRules = properties.getCompliance().get(vendor);
        if (MapUtils.isEmpty(productRules)) {
            log.debug("[{}] 未配置 {} 合规规则，直接注册", device.getIp(), vendor);
            return "default";
        }

        DeviceComplianceValidator validator = validators.get(vendor);
        if (validator == null) {
            log.warn("[{}] {} 无合规校验器，跳过", device.getIp(), vendor);
            return null;
        }

        // default 默认启用（不带任何 route 条件），仅显式 enabled: false 时关闭
        ComplianceGroupConfig defaultConfig = productRules.get("default");
        boolean defaultEnabled = defaultConfig == null
                || defaultConfig.getEnabled() == null
                || defaultConfig.getEnabled();

        // 按配置顺序遍历显式分组（LinkedHashMap 保证 YAML 书写顺序）
        for (Map.Entry<String, ComplianceGroupConfig> entry : productRules.entrySet()) {
            if ("default".equals(entry.getKey())) continue;
            ComplianceGroupConfig cfg = entry.getValue();
            if (cfg == null || CollectionUtils.isEmpty(cfg.getRules())) continue;
            if (validator.validate(device, infoData, cfg.getRules())) {
                log.debug("[{}] 合规通过(显式): {} / {}", device.getIp(), vendor, entry.getKey());
                return entry.getKey();
            }
        }

        // 显式分组均不匹配 → fallback 到 default
        if (defaultEnabled) {
            log.debug("[{}] 显式分组均不匹配，进入 default: {}", device.getIp(), vendor);
            return "default";
        }

        log.debug("[{}] 合规不通过（无匹配分组且 default 已禁用）: {}", device.getIp(), vendor);
        return null;
    }

    private DeviceContext buildTempContext(DiscoveredDevice dd, DeviceVendor vendor,
                                           VendorProtocolAdapter adapter) {
        Map<String, Object> attrs = dd.getAttributes();
        return DeviceContext.builder()
                .deviceId(resolveDeviceId(dd))
                .ip(dd.getIp())
                .port(dd.getSourcePort())
                .vendor(vendor)
                .transportType(adapter.transportType())
                .online(true)
                .sn(attr(attrs, "serialNo", "sn"))
                .macAddr(attr(attrs, "macAddr", "mac"))
                .width(attrInt(attrs, "width"))
                .height(attrInt(attrs, "height"))
                .attributes(attrs)
                .build();
    }

    /**
     * 从发现属性中解析设备唯一标识：优先 SN 序列号，其次 MAC，最后回退 IP。
     */
    private String resolveDeviceId(DiscoveredDevice dd) {
        return resolveDeviceId(dd.getAttributes(), dd.getIp());
    }

    private String resolveDeviceId(Map<String, Object> attrs, String ip) {
        String sn = firstAttr(attrs, "serialNo", "sn");
        if (sn != null) return sn;
        String mac = attr(attrs, "macAddr", "mac");
        if (mac != null) return ProtocolConstant.formatMac(mac);
        return ip;
    }
}
