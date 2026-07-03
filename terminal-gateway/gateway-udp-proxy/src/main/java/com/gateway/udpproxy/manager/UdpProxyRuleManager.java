package com.gateway.udpproxy.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gateway.common.service.CryptoService;
import com.gateway.udpproxy.entity.UdpProxyRule;
import com.gateway.udpproxy.forward.CatchAllTcpProxyServer;
import com.gateway.udpproxy.forward.TcpProxyServer;
import com.gateway.udpproxy.forward.UdpProxyServer;
import com.gateway.udpproxy.entity.message.MessageAssembler;
import com.gateway.udpproxy.mapper.UdpProxyRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UdpProxyRuleManager {

    @Resource
    private UdpProxyRuleMapper ruleMapper;

    @Resource
    private CryptoService cryptoService;

    @Resource
    private MessageAssembler messageAssembler;

    private final Map<String, UdpProxyServer> runningServers = new ConcurrentHashMap<>();
    private final Map<String, TcpProxyServer> runningTcpServers = new ConcurrentHashMap<>();

    /** 附加TCP代理服务：key: ruleId, Value: 该规则下的附加TCP代理服务列表 */
    private final Map<String, List<TcpProxyServer>> additionalTcpServers = new ConcurrentHashMap<>();

    private final Map<String, List<UdpProxyServer>> additionalUdpServers = new ConcurrentHashMap<>();

    /** CatchAll 通用TCP代理服务（适配Nova动态端口）key: ruleId */
    private final Map<String, CatchAllTcpProxyServer> catchAllServers = new ConcurrentHashMap<>();

    /** CatchAll 代理默认端口 */
    private static final int DEFAULT_CATCH_ALL_PORT = 19999;

    private final ExecutorService configExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "udp-proxy-config-worker");
        t.setDaemon(true);
        return t;
    });

    private String formatRuleLog(UdpProxyRule rule) {
        return String.format("ruleId=%s, chainId=%s, manufacturer=%s",
                rule.getRuleId(), rule.getChainId(), rule.getManufacturer());
    }

    public String summarizeRule(UdpProxyRule rule) {
        return formatRuleLog(rule);
    }

    @PostConstruct
    public void init() {
        loadAndStartRules();
    }

    @PreDestroy
    public void destroy() {
        log.info("准备关闭所有UDP代理服务...");
        configExecutor.shutdownNow();
        stopAll();
        try {
            if (!configExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("config executor did not stop within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadAndStartRules() {
        try {
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getStatus, "ENABLED");
            List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

            // 用于记录已占用的监听地址，防止端口冲突
            java.util.Set<String> occupiedBindings = new java.util.HashSet<>();

            for (UdpProxyRule rule : rules) {
                try {
                    String bindKey = buildBindKey(rule);
                    if (occupiedBindings.contains(bindKey)) {
                        log.info("规则[{}] 监听地址({})已被占用，跳过启动",
                                rule.getRuleId(), bindKey);
                        continue;
                    }
                    startProxyServer(rule);
                    occupiedBindings.add(bindKey);
                } catch (Exception e) {
                    log.error("启动代理服务失败：{}", formatRuleLog(rule), e);
                }
            }
        } catch (Exception e) {
            log.error("加载并启动代理规则异常", e);
        }
    }

    /**
     * 新增或更新规则
     *
     * @return 规则监听端口，失败返回-1
     */
    public int addOrUpdateRule(UdpProxyRule rule) {
        try {
            if (rule == null || rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                log.error("add or update rule failed: invalid rule");
                return -1;
            }

            // 启用状态下自动分配可用端口，避免端口冲突
            if ("ENABLED".equals(rule.getStatus()) && rule.getListenPort() != null) {
                int actualPort = allocateAvailablePort(rule.getListenPort(), rule.getRuleId());
                if (actualPort != rule.getListenPort()) {
                    log.info("规则[{}] 端口 {} 已被占用，自动分配新端口{}",
                            formatRuleLog(rule), rule.getListenPort(), actualPort);
                    rule.setListenPort(actualPort);
                }
            }

            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getRuleId, rule.getRuleId());
            UdpProxyRule existing = ruleMapper.selectOne(wrapper);

            if (existing != null) {
                rule.setId(existing.getId());
                ruleMapper.updateById(rule);
                log.info("规则已更新: {}", formatRuleLog(rule));
            } else {
                ruleMapper.insert(rule);
                log.info("规则已插入: {}", formatRuleLog(rule));
            }

            scheduleProxyRestart(rule);

            return rule.getListenPort();
        } catch (Exception e) {
            log.error("add or update rule failed", e);
            return -1;
        }
    }

    /**
     * 获取所有已占用的端口
     */
    public java.util.Set<Integer> getOccupiedPorts() {
        return getOccupiedPortsExcept(null);
    }

    private java.util.Set<Integer> getOccupiedPortsExcept(String excludedRuleId) {
        java.util.Set<Integer> ports = new java.util.HashSet<>();
        for (Map.Entry<String, UdpProxyServer> entry : runningServers.entrySet()) {
            if (excludedRuleId != null && excludedRuleId.equals(entry.getKey())) {
                continue;
            }
            UdpProxyServer s = entry.getValue();
            if (s.isRunning() && s.getRule().getListenPort() != null) {
                ports.add(s.getRule().getListenPort());
            }
        }
        for (Map.Entry<String, TcpProxyServer> entry : runningTcpServers.entrySet()) {
            if (excludedRuleId != null && excludedRuleId.equals(entry.getKey())) {
                continue;
            }
            TcpProxyServer s = entry.getValue();
            if (s.isRunning() && s.getRule().getListenPort() != null) {
                ports.add(s.getRule().getListenPort());
            }
        }
        return ports;
    }

    /**
     * 分配可用端口（端口冲突时自动递增）
     */
    private int allocateAvailablePort(int requestedPort) {
        java.util.Set<Integer> occupied = getOccupiedPorts();
        int port = requestedPort;
        while (occupied.contains(port)) {
            log.debug("端口{} 已被占用，尝试下一个端口{}", port, port + 1);
            port++;
        }
        return port;
    }

    /**
     * 启动TCP + UDP 全套代理服务
     *
     * 业务说明：
     * 设备侧同时存在TCP（TLS加密）和UDP（明文）通信
     * 主端口默认6606，同时支持配置附加端口
     * TCP代理负责加密/解密转发，UDP代理负责透传转发
     */
    private int allocateAvailablePort(int requestedPort, String excludedRuleId) {
        java.util.Set<Integer> occupied = getOccupiedPortsExcept(excludedRuleId);
        int port = requestedPort;
        while (occupied.contains(port)) {
            log.debug("port {} is occupied, try next port {}", port, port + 1);
            port++;
        }
        return port;
    }

    private void scheduleProxyRestart(UdpProxyRule rule) {
        configExecutor.submit(() -> {
            try {
                stopProxyServer(rule.getRuleId());
                if ("ENABLED".equals(rule.getStatus())) {
                    startProxyServer(rule);
                    log.info("async proxy restart done: {}", formatRuleLog(rule));
                } else {
                    log.info("async proxy stopped: {}", formatRuleLog(rule));
                }
            } catch (Exception e) {
                log.error("async proxy restart failed: {}", formatRuleLog(rule), e);
            }
        });
    }

    private void startProxyServer(UdpProxyRule rule) {
        applyManufacturerDefaults(rule);

        // 1. 启动UDP代理服务
        startUdpProxyServer(rule);

        // 2. 启动TCP代理服务
        try {
            startTcpProxyServer(rule);
        } catch (Exception e) {
            log.warn("启动主TCP代理服务失败，不影响UDP服务运行：{}，原因：{}",
                    rule.getRuleId(), e.getMessage());
        }

        // 3. 启动附加TCP/UDP代理服务（如16606/16602等端口）
        startAdditionalTcpProxies(rule);
        startAdditionalUdpProxies(rule);

        // 4. 启动CatchAll通用TCP代理服务（适配Nova动态端口）
        startCatchAllProxy(rule);
    }

    private void applyManufacturerDefaults(UdpProxyRule rule) {
        if (rule == null || rule.getManufacturer() == null
                || !"nova".equalsIgnoreCase(rule.getManufacturer().trim())) {
            return;
        }

        if (rule.getAdditionalTcpPorts() == null || rule.getAdditionalTcpPorts().trim().isEmpty()) {
            rule.setAdditionalTcpPorts("16602");
        }
        if (rule.getAdditionalUdpPorts() == null || rule.getAdditionalUdpPorts().trim().isEmpty()) {
            rule.setAdditionalUdpPorts("16601,16611");
        }
        if (rule.getDynamicPortProxyEnabled() == null) {
            rule.setDynamicPortProxyEnabled(Boolean.TRUE);
        }
        if (rule.getCatchAllProxyPort() == null) {
            rule.setCatchAllProxyPort(DEFAULT_CATCH_ALL_PORT);
        }

        log.info("Nova规则默认端口已补齐: ruleId={}, additionalTcpPorts={}, additionalUdpPorts={}, dynamicPortProxyEnabled={}, catchAllProxyPort={}",
                rule.getRuleId(),
                rule.getAdditionalTcpPorts(),
                rule.getAdditionalUdpPorts(),
                rule.getDynamicPortProxyEnabled(),
                rule.getCatchAllProxyPort());
    }

    private void startUdpProxyServer(UdpProxyRule rule) {
        UdpProxyServer server = new UdpProxyServer(rule, cryptoService, messageAssembler);
        server.start();

        // 校验服务启动状态
        if (server.isRunning()) {
            runningServers.put(rule.getRuleId(), server);
            log.info("UDP代理服务启动成功：{}", formatRuleLog(rule));
        } else {
            log.error("UDP proxy start failed: {}, server not running", formatRuleLog(rule));
            throw new RuntimeException("UDP代理服务启动失败：" + formatRuleLog(rule));
        }
    }

    private void startTcpProxyServer(UdpProxyRule rule) {
        TcpProxyServer server = new TcpProxyServer(rule, cryptoService, messageAssembler);
        server.start();
        if (server.isRunning()) {
            runningTcpServers.put(rule.getRuleId(), server);
            log.info("TCP代理服务启动成功：{}", formatRuleLog(rule));
        } else {
            log.error("TCP proxy start failed: {}, server not running", formatRuleLog(rule));
            throw new RuntimeException("TCP代理服务启动失败：" + formatRuleLog(rule));
        }
    }

    /**
     * 启动规则配置的附加TCP代理服务
     *
     * <pre>
     * 常见附加端口：
     *   16606 TLS加密端口
     *   16602 设备心跳/状态上报端口
     *
     * 转发逻辑：
     *   本地监听:16602 → 转发到目标:16602
     *   支持同时配置多个附加端口
     * </pre>
     */
    private void startAdditionalTcpProxies(UdpProxyRule rule) {
        String ports = rule.getAdditionalTcpPorts();
        if (ports == null || ports.trim().isEmpty()) {
            return;
        }

        List<TcpProxyServer> servers = new ArrayList<>();
        for (String portStr : ports.split(",")) {
            int port;
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                log.warn("无效的附加TCP端口：'{}'", portStr);
                continue;
            }

            // 跳过主端口，避免重复
            if (rule.getListenPort() != null && port == rule.getListenPort()) {
                continue;
            }

            try {
                UdpProxyRule clonedRule = cloneRuleForPort(rule, port);
                TcpProxyServer server = new TcpProxyServer(clonedRule, cryptoService, messageAssembler);
                server.start();
                if (server.isRunning()) {
                    servers.add(server);
                    log.info("附加TCP代理启动成功，端口{} ({})", port, formatRuleLog(rule));
                } else {
                    log.warn("附加TCP代理启动失败，端口{} ({})", port, formatRuleLog(rule));
                }
            } catch (Exception e) {
                log.warn("附加TCP代理启动异常，端口{} (规则{})，原因：{}",
                        port, formatRuleLog(rule), e.getMessage());
            }
        }

        if (!servers.isEmpty()) {
            additionalTcpServers.put(rule.getRuleId(), servers);
        }
    }

    private void startAdditionalUdpProxies(UdpProxyRule rule) {
        String ports = rule.getAdditionalUdpPorts();
        if (ports == null || ports.trim().isEmpty()) {
            return;
        }

        List<UdpProxyServer> servers = new ArrayList<>();
        for (String portStr : ports.split(",")) {
            int port;
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                log.warn("invalid additional UDP port '{}', {}", portStr, formatRuleLog(rule));
                continue;
            }
            if (rule.getListenPort() != null && port == rule.getListenPort()) {
                continue;
            }

            try {
                UdpProxyRule clonedRule = cloneRuleForUdpPort(rule, port);
                UdpProxyServer server = new UdpProxyServer(clonedRule, cryptoService, messageAssembler);
                server.start();
                if (server.isRunning()) {
                    servers.add(server);
                    log.info("additional UDP proxy started: port={}, {}", port, formatRuleLog(rule));
                } else {
                    log.warn("additional UDP proxy start failed: port={}, {}", port, formatRuleLog(rule));
                }
            } catch (Exception e) {
                log.warn("additional UDP proxy start exception: port={}, {}, reason={}",
                        port, formatRuleLog(rule), e.getMessage());
            }
        }

        if (!servers.isEmpty()) {
            additionalUdpServers.put(rule.getRuleId(), servers);
        }
    }

    /**
     * 克隆规则用于附加端口启动
     * 生成独立的规则配置，不修改原始规则
     */
    private UdpProxyRule cloneRuleForPort(UdpProxyRule original, int port) {
        UdpProxyRule clone = new UdpProxyRule();
        clone.setRuleId(original.getRuleId() + "_tcp" + port);
        clone.setRuleName(original.getRuleName() + " (TCP:" + port + ")");
        clone.setListenPort(port);
        clone.setSourceIp(original.getSourceIp());
        clone.setTargetIp(original.getTargetIp());
        clone.setTargetPort(port);
        clone.setDecryptEnabled(original.getDecryptEnabled());
        clone.setManufacturer(original.getManufacturer());
        clone.setChainId(original.getChainId());
        clone.setStatus(original.getStatus());
        clone.setProtocol("TCP");
        return clone;
    }

    private UdpProxyRule cloneRuleForUdpPort(UdpProxyRule original, int port) {
        UdpProxyRule clone = new UdpProxyRule();
        clone.setRuleId(original.getRuleId() + "_udp" + port);
        clone.setRuleName(original.getRuleName() + " (UDP:" + port + ")");
        clone.setListenPort(port);
        clone.setSourceIp(original.getSourceIp());
        clone.setTargetIp(original.getTargetIp());
        clone.setTargetPort(port);
        clone.setDecryptEnabled(original.getDecryptEnabled());
        clone.setManufacturer(original.getManufacturer());
        clone.setChainId(original.getChainId());
        clone.setStatus(original.getStatus());
        clone.setProtocol("UDP");
        return clone;
    }

    /**
     * 启动CatchAll通用TCP代理服务（适配Nova动态端口）
     *
     * <pre>
     * 启用条件：dynamicPortProxyEnabled == true
     * 适用场景：
     *   设备使用TPROXY模式，Nova动态端口通信
     *   统一监听一个端口，转发所有动态端口流量
     * </pre>
     */
    private void startCatchAllProxy(UdpProxyRule rule) {
        if (!Boolean.TRUE.equals(rule.getDynamicPortProxyEnabled())) {
            return; // 未开启动态端口代理，跳过
        }

        int port = rule.getCatchAllProxyPort() != null ? rule.getCatchAllProxyPort() : DEFAULT_CATCH_ALL_PORT;

        try {
            CatchAllTcpProxyServer server = new CatchAllTcpProxyServer(
                    rule, cryptoService, messageAssembler, port);
            server.start();
            if (server.isRunning()) {
                catchAllServers.put(rule.getRuleId(), server);
                log.info("CatchAll通用代理启动成功：{}，端口{}", formatRuleLog(rule), port);
            } else {
                log.warn("CatchAll通用代理启动失败：{}", formatRuleLog(rule));
            }
        } catch (Throwable t) {
            log.warn("CatchAll通用代理启动异常：{}，原因：{}", formatRuleLog(rule), t.getMessage(), t);
        }
    }

    private void stopProxyServer(String ruleId) {
        UdpProxyServer server = runningServers.remove(ruleId);
        if (server != null) {
            server.stop();
            log.info("UDP代理服务已关闭：{}", ruleId);
        }
        TcpProxyServer tcpServer = runningTcpServers.remove(ruleId);
        if (tcpServer != null) {
            tcpServer.stop();
            log.info("TCP代理服务已关闭：{}", ruleId);
        }
        // 关闭附加TCP代理服务
        List<TcpProxyServer> additionalServers = additionalTcpServers.remove(ruleId);
        if (additionalServers != null) {
            for (TcpProxyServer s : additionalServers) {
                s.stop();
            }
            log.info("additional TCP proxies stopped: ruleId={}, count={}", ruleId, additionalServers.size());
        }

        // 关闭附加UDP代理服务
        List<UdpProxyServer> additionalUdp = additionalUdpServers.remove(ruleId);
        if (additionalUdp != null) {
            for (UdpProxyServer s : additionalUdp) {
                s.stop();
            }
            log.info("additional UDP proxies stopped: ruleId={}, count={}", ruleId, additionalUdp.size());
        }

        // 关闭CatchAll通用代理服务
        CatchAllTcpProxyServer catchAll = catchAllServers.remove(ruleId);
        if (catchAll != null) {
            catchAll.stop();
            log.info("CatchAll通用代理已关闭：{}", ruleId);
        }
    }

    /**
     * 批量修改指定链路下所有规则的状态（ENABLED / DISABLED）
     *
     * @param chainId 链路ID
     * @param status  目标状态 "ENABLED" 或 "DISABLED"
     * @return 操作成功的规则数量
     */
    public int setChainRulesStatus(Long chainId, String status) {
        log.info("==========================================");
        log.info("  [terminal-gateway] receive chain status switch");
        log.info("  链路ID: {}, 目标状态: {}", chainId, status);
        log.info("==========================================");

        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getChainId, chainId)
                .eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("[terminal-gateway] no active rules under chainId={}, skip", chainId);
            return 0;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                if ("DISABLED".equals(status)) {
                    stopProxyServer(rule.getRuleId());
                } else if ("ENABLED".equals(status) && !isRuleRunning(rule.getRuleId())) {
                    startProxyServer(rule);
                }
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                        .set(UdpProxyRule::getStatus, status);
                ruleMapper.update(null, update);
                log.info("✅ 规则状态更新完成：ruleId={}, status={}", rule.getRuleId(), status);
                count++;
            } catch (Exception e) {
                log.error("❌ 规则操作失败：ruleId={}", rule.getRuleId(), e);
            }
        }
        log.info("[terminal-gateway] chain status switch done: chainId={}, count={}", chainId, count);
        return count;
    }

    /**
     * 关闭指定规则的代理服务
     *
     * @param ruleId 规则ID
     * @return true-关闭成功, false-服务未运行或关闭失败
     */
    public boolean stopRule(String ruleId) {
        try {
            UdpProxyServer server = runningServers.get(ruleId);
            TcpProxyServer tcpServer = runningTcpServers.get(ruleId);
            boolean isRunning = (server != null && server.isRunning()) || (tcpServer != null && tcpServer.isRunning());
            if (isRunning) {
                stopProxyServer(ruleId);

                // 更新数据库状态为DISABLED
                LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(UdpProxyRule::getRuleId, ruleId);
                UdpProxyRule rule = ruleMapper.selectOne(wrapper);

                if (rule != null) {
                    rule.setStatus("DISABLED");
                    ruleMapper.updateById(rule);
                    log.info("规则已更新: {}", formatRuleLog(rule));
                }

                return true;
            } else {
                log.warn("规则未运行，无需关闭：{}", ruleId);
                return false;
            }
        } catch (Exception e) {
            log.error("关闭规则失败：{}", ruleId, e);
            return false;
        }
    }

    public void stopAll() {
        runningServers.values().forEach(UdpProxyServer::stop);
        runningServers.clear();
        runningTcpServers.values().forEach(TcpProxyServer::stop);
        runningTcpServers.clear();
        // 关闭所有附加TCP代理
        additionalTcpServers.values().forEach(list -> list.forEach(TcpProxyServer::stop));
        additionalTcpServers.clear();
        // 关闭所有附加UDP代理
        additionalUdpServers.values().forEach(list -> list.forEach(UdpProxyServer::stop));
        additionalUdpServers.clear();
        // 关闭所有CatchAll通用代理
        catchAllServers.values().forEach(CatchAllTcpProxyServer::stop);
        catchAllServers.clear();
    }

    /**
     * 获取所有正在运行的规则ID
     */
    public List<String> getRunningRuleIds() {
        List<String> result = new java.util.ArrayList<>();
        runningServers.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().isRunning())
                .map(Map.Entry::getKey)
                .forEach(result::add);
        runningTcpServers.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().isRunning())
                .map(Map.Entry::getKey)
                .forEach(result::add);
        return result;
    }

    /**
     * 获取所有已启用的规则（ENABLED状态）
     */
    public List<UdpProxyRule> getAllEnabledRules() {
        try {
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getStatus, "ENABLED");
            return ruleMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("查询启用规则失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 构建监听唯一标识（IP:端口），防止重复监听
     */
    private String buildBindKey(UdpProxyRule rule) {
        String ip = (rule.getListenIp() == null || rule.getListenIp().isEmpty())
                ? "0.0.0.0" : rule.getListenIp();
        return ip + ":" + rule.getListenPort();
    }

    /**
     * 判断规则是否正在运行
     */
    public boolean isRuleRunning(String ruleId) {
        UdpProxyServer server = runningServers.get(ruleId);
        if (server != null && server.isRunning()) return true;
        TcpProxyServer tcpServer = runningTcpServers.get(ruleId);
        return tcpServer != null && tcpServer.isRunning();
    }

    /**
     * 向指定目标发送UDP指令（适用于紧急指令下发）
     *
     * @param targetIp   目标IP
     * @param targetPort 目标端口
     * @param data       指令数据
     * @return true-发送成功 false-发送失败
     */
    public boolean sendCommand(String targetIp, int targetPort, byte[] data) {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                            // 忽略响应，仅发送指令
                        }
                    });

            Channel channel = bootstrap.bind(0).sync().channel();
            InetSocketAddress targetAddress = new InetSocketAddress(targetIp, targetPort);
            channel.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(data), targetAddress)).sync();
            channel.close().sync();

            log.info("==========================================" );
            log.info("  [emergency] command sent");
            log.info("  目标地址：{}:{}", targetIp, targetPort);
            log.info("  数据长度：{}", data.length);
            log.info("==========================================");
            return true;
        } catch (Exception e) {
            log.error("紧急指令发送失败：{}:{}", targetIp, targetPort, e);
            return false;
        } finally {
            group.shutdownGracefully();
        }
    }
}
