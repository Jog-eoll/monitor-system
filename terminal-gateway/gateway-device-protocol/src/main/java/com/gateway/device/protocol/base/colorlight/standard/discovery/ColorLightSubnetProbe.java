package com.gateway.device.protocol.base.colorlight.standard.discovery;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.common.discovery.CidrExpander;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * ColorLight 子网 TCP 探测工具 —— 通过 {@link DeviceTransport#tcpProbe} 验证端口可达。
 *
 * <p>配置 CIDR 子网列表，并行探测每个 IP:Port 组合，
 * 仅通过 TCP 握手确认设备存活，不发送应用层数据。
 * 设备信息收集由后续 {@code DeviceInfoGetHandler} 完成。</p>
 */
@Slf4j
public class ColorLightSubnetProbe {

    private final List<String> subnets;
    private final List<Integer> ports;
    private final int timeoutMs;
    private final int concurrency;
    private final DeviceTransport transport;

    public ColorLightSubnetProbe(List<String> subnets, List<Integer> ports,
                                 int timeoutMs, int concurrency,
                                 DeviceTransport transport) {
        this.subnets = subnets != null ? subnets : Collections.emptyList();
        this.ports = ports != null ? ports : Collections.singletonList(VendorDefaultPort.COLOR_LIGHT_STANDARD.getPort());
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 2000;
        this.concurrency = concurrency > 0 ? concurrency : 20;
        this.transport = transport;
    }

    /**
     * 探测所有配置子网中的设备。
     *
     * @return 发现的设备列表（按 IP 排序）
     */
    public List<ColorLightDiscoveredDevice> probeSubnets() {
        if (subnets.isEmpty()) {
            log.debug("未配置探测子网，跳过");
            return Collections.emptyList();
        }

        List<String> allIps = CidrExpander.expandAll(subnets);
        if (allIps.isEmpty()) {
            log.debug("子网展开后无可用 IP");
            return Collections.emptyList();
        }

        log.info("开始探测 {} 个子网, {} 个 IP, 端口{}, 并发{}",
                subnets.size(), allIps.size(), ports, concurrency);

        // 生成所有探测任务 (IP × Port)
        List<ProbeTask> tasks = new ArrayList<>();
        for (String ip : allIps) {
            for (int port : ports) {
                tasks.add(new ProbeTask(ip, port));
            }
        }

        // 并行执行
        Map<String, ColorLightDiscoveredDevice> found = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (ProbeTask task : tasks) {
                futures.add(executor.submit(() -> {
                    ColorLightDiscoveredDevice device = probeOne(task);
                    if (device != null) {
                        found.putIfAbsent(device.getIp(), device);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(timeoutMs * 2L, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    f.cancel(true);
                } catch (Exception ignored) {
                }
            }
        } finally {
            executor.shutdownNow();
        }

        List<ColorLightDiscoveredDevice> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparing(ColorLightDiscoveredDevice::getIp));
        log.info("子网探测完成: 发现 {} 台 ColorLight 设备", result.size());
        return result;
    }

    /**
     * 探测单个 IP:Port 组合 —— 通过 TCP 握手验证端口可达。
     */
    private ColorLightDiscoveredDevice probeOne(ProbeTask task) {
        try {
            log.debug("[{}:{}] TCP Connect 探测", task.ip, task.port);

            DeviceContext tempCtx = DeviceContext.builder()
                    .ip(task.ip)
                    .port(task.port)
                    .transportType(TransportType.HTTP)
                    .build();
            Boolean reachable = transport.tcpProbe(tempCtx, Duration.ofMillis(timeoutMs))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(reachable)) {
                log.info("[{}:{}] 发现 ColorLight 设备（TCP 可达）", task.ip, task.port);
                return ColorLightDiscoveredDevice.builder()
                        .ip(task.ip)
                        .sourcePort(task.port)
                        .build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("[{}:{}] TCP 探测失败: {}", task.ip, task.port,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return null;
    }

    // ════════════════════════════════════════════════════
    // 内部类
    // ════════════════════════════════════════════════════

    private static class ProbeTask {
        final String ip;
        final int port;

        ProbeTask(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}
