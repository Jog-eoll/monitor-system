package com.publishgateway.udpproxy.manager;

import com.publishgateway.udpproxy.assembly.UdpFileAssemblyService;
import com.publishgateway.udpproxy.service.ClientValidationService;
import com.publishgateway.udpproxy.service.DataReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.publishgateway.udpproxy.config.ClientRelayProperties;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.forward.CatchAllTcpProxyServer;
import com.publishgateway.udpproxy.forward.TcpProxyServer;
import com.publishgateway.udpproxy.forward.UdpProxyServer;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.publishgateway.udpproxy.mapper.UdpProxyRuleMapper;
import com.publishgateway.udpproxy.secure.SecurePublishIngressService;
import com.publishgateway.udpproxy.service.CryptoPacketStore;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.TrafficReconciliationService;
import com.publishgateway.udpproxy.service.TranscodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class UdpProxyRuleManager {

    @Resource
    private UdpProxyRuleMapper ruleMapper;

    @Resource
    private CryptoService cryptoService;

    @Resource
    private CryptoPacketStore cryptoPacketStore;

    @Resource
    private DataReportService dataReportService;

    @Resource
    private ClientValidationService clientValidationService;

    @Resource
    private TranscodeService transcodeService;

    @Resource
    private TrafficReconciliationService trafficReconciliationService;

    @Resource
    private ClientRelayProperties clientRelayProperties;

    @Resource
    private SecurePublishIngressService securePublishIngressService;

    @Resource
    private UdpFileAssemblyService udpFileAssemblyService;


    @Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    /** 转码开关，注入到 Server 构造函数中 */
    @Value("${gateway.transcode.enabled:false}")
    private boolean transcodeEnabled;

    @Value("${security.per-packet-client-validation-enabled:false}")
    private boolean perPacketClientValidationEnabled;

    private final Map<String, UdpProxyServer> runningServers = new ConcurrentHashMap<>();

    /** 运行中的 TCP 代理服务器，Key: ruleId */
    private final Map<String, TcpProxyServer> runningTcpServers = new ConcurrentHashMap<>();

    /** 附加 TCP 代理服务器（诺瓦多端口场景），Key: ruleId, Value: 该规则的所有附加 TCP 代理 */
    private final Map<String, List<TcpProxyServer>> additionalTcpServers = new ConcurrentHashMap<>();

    private final Map<String, List<UdpProxyServer>> additionalUdpServers = new ConcurrentHashMap<>();

    /** CatchAll 动态端口透明代理（仅 Nova 大屏），Key: ruleId */
    private final Map<String, CatchAllTcpProxyServer> catchAllServers = new ConcurrentHashMap<>();

    /** CatchAll 默认监听端口 */
    private static final int DEFAULT_CATCH_ALL_PORT = 19999;

    @PostConstruct
    public void init() {
        loadAndStartRules();
    }

    private void loadAndStartRules() {
        try {
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getStatus, "ENABLED");
            List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

            // 步骤1：启动前检测端口冲突，相同 listenPort 只允许绑定到不同IP（0.0.0.0只能有一个）
            Set<String> occupiedBindings = new HashSet<>();
            for (UdpProxyRule rule : rules) {
                String bindKey = buildBindKey(rule);
                if (occupiedBindings.contains(bindKey)) {
                    log.info("规则 [{}] 与已有规则共享监听地址 ({})，跳过启动（保持ENABLED，复用已有服务）",
                            rule.getRuleId(), bindKey);
                    // 合并该规则到已运行服务（建立 IP -> Rule 映射）
                    mergeRuleToRunningServer(bindKey, rule);
                    continue;
                }
                occupiedBindings.add(bindKey);

                // 步骤2：启动失败时回写DB状态为DISABLED
                try {
                    startProxyServer(rule);
                } catch (Exception e) {
                    log.error("启动代理服务失败: {}，原因: {}，将规则标记为DISABLED",
                            rule.getRuleId(), e.getMessage());
                    disableRuleInDb(rule.getId(), "启动失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("加载代理规则失败", e);
        }
    }

    /**
     * 将规则合并到监听同一端口的已运行服务（建立 sourceIp -> rule 映射）
     */
    private void mergeRuleToRunningServer(String bindKey, UdpProxyRule rule) {
        if (rule == null || rule.getSourceIp() == null || rule.getSourceIp().isEmpty()) return;
        runningServers.values().stream()
                .filter(s -> s.isRunning() && buildBindKey(s.getRule()).equals(bindKey))
                .findFirst()
                .ifPresent(s -> s.mergeRule(rule));
        runningTcpServers.values().stream()
                .filter(s -> s.isRunning() && buildBindKey(s.getRule()).equals(bindKey))
                .findFirst()
                .ifPresent(s -> s.mergeRule(rule));
    }

    /**
     * 构建绑定唯一Key，用于端口冲突检测
     * 同一规则同时启动 TCP+UDP，端口冲突只按 ip+port 检测（不区分协议）
     */
    private String buildBindKey(UdpProxyRule rule) {
        String ip = (rule.getListenIp() == null || rule.getListenIp().isEmpty())
                ? "0.0.0.0" : rule.getListenIp();
        return ip + ":" + rule.getListenPort();
    }

    /**
     * 将规则在数据库中标记为DISABLED，并记录失败原因
     */
    private void disableRuleInDb(Long id, String reason) {
        if (id == null) return;
        try {
            LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
            update.eq(UdpProxyRule::getId, id)
                    .set(UdpProxyRule::getStatus, "DISABLED")
                    .set(UdpProxyRule::getRemark, reason);
            ruleMapper.update(null, update);
            log.info("✅ 已将规则[id={}]状态更新为DISABLED，原因: {}", id, reason);
        } catch (Exception ex) {
            log.error("更新规则状态失败[id={}]", id, ex);
        }
    }

    public boolean addOrUpdateRule(UdpProxyRule rule){
        try{
            // 停止旧服务
            stopProxyServer(rule.getRuleId());

            // 保存到数据库
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getRuleId, rule.getRuleId());
            UdpProxyRule existing = ruleMapper.selectOne(wrapper);

            if(existing != null){
                rule.setId(existing.getId());
                ruleMapper.updateById(rule);
            }else {
                ruleMapper.insert(rule);
            }

            // 启动新服务（如果相同地址已有服务在运行，跳过启动，复用已有服务）
            if ("ENABLED".equals(rule.getStatus())) {
                String bindKey = buildBindKey(rule);
                boolean alreadyRunning = runningServers.values().stream()
                        .anyMatch(s -> s.isRunning() && buildBindKey(s.getRule()).equals(bindKey));
                if (alreadyRunning) {
                    log.info("规则 [{}] 的监听地址 ({}) 已有服务运行，跳过启动（复用已有服务）",
                            rule.getRuleId(), bindKey);
                    // 合并该规则到已运行服务（建立 IP -> Rule 映射）
                    mergeRuleToRunningServer(bindKey, rule);
                } else {
                    startProxyServer(rule);
                }
            }

            return true;
        }catch (Exception e) {
            log.error("添加/更新规则失败", e);
            return false;
        }
    }


    /**
     * 一条规则同时启动 TCP + UDP 两个 Server，监听同一端口
     * 两种协议的客户端都能接入，无需预先配置协议类型
     */
    private void startProxyServer(UdpProxyRule rule) {
        applyManufacturerDefaults(rule);

        // 启动 UDP Server
        UdpProxyServer udpServer = new UdpProxyServer(
                rule, cryptoService, cryptoPacketStore, dataReportService, transcodeEnabled, transcodeService,
                clientValidationService, perPacketClientValidationEnabled, trafficReconciliationService,
                clientRelayProperties.isRejectDirectUdp(), securePublishIngressService, udpFileAssemblyService,
                diagnosticLogReporter);
        udpServer.start();
        if (udpServer.isRunning()) {
            runningServers.put(rule.getRuleId(), udpServer);
            log.info("✅ UDP代理服务启动成功: {}", rule.getRuleId());
        } else {
            log.error("❌ UDP代理服务启动失败: {}", rule.getRuleId());
            throw new RuntimeException("UDP代理服务启动失败: " + rule.getRuleId());
        }

        // 启动 TCP Server（失败不影响 UDP 的正常运行）
        try {
            TcpProxyServer tcpServer = new TcpProxyServer(
                    rule, cryptoService, cryptoPacketStore, dataReportService, transcodeEnabled, transcodeService);
            tcpServer.start();
            if (tcpServer.isRunning()) {
                runningTcpServers.put(rule.getRuleId(), tcpServer);
                log.info("✅ TCP代理服务启动成功: {}", rule.getRuleId());
            } else {
                log.warn("⚠️  TCP代理服务启动失败: {}，仅 UDP 模式运行", rule.getRuleId());
            }
        } catch (Exception e) {
            log.warn("⚠️  TCP代理服务启动异常: {}，仅 UDP 模式运行，原因: {}",
                    rule.getRuleId(), e.getMessage());
        }

        // 启动附加 TCP 代理（诺瓦多端口场景：16606控制 + 16602内容 + ...）
        startAdditionalTcpProxies(rule);
        startAdditionalUdpProxies(rule);

        // 启动 CatchAll 动态端口透明代理（仅 Nova 大屏，Sigma 等厂商此字段为 null 不触发）
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

    /**
     * 为附加端口启动独立的 TCP 代理服务器
     *
     * <pre>
     * 诺瓦大屏使用多端口通信架构：
     *   16606 — TLS 认证/控制连接（主端口，已由上面的 startProxyServer 启动）
     *   16602 — 内容发布数据传输（附加端口，由本方法启动）
     *
     * 端口映射约定（加密模式下）：
     *   发布网关 :16602 → 终端网关 :16602 → 情报板 :16602
     *   即：附加端口在终端网关上使用与情报板相同的端口号
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
                log.warn("⚠️  附加端口格式错误，跳过: '{}'", portStr);
                continue;
            }

            // 跳过与主端口相同的端口（避免重复绑定）
            if (port == rule.getListenPort()) {
                continue;
            }

            try {
                UdpProxyRule clonedRule = cloneRuleForPort(rule, port);
                TcpProxyServer server = new TcpProxyServer(
                        clonedRule, cryptoService, cryptoPacketStore, dataReportService, transcodeEnabled, transcodeService);
                server.start();
                if (server.isRunning()) {
                    servers.add(server);
                    log.info("✅ 附加TCP代理启动成功: 端口 {} (规则 {})", port, rule.getRuleId());
                } else {
                    log.warn("⚠️  附加TCP代理启动失败: 端口 {} (规则 {})", port, rule.getRuleId());
                }
            } catch (Exception e) {
                log.warn("⚠️  附加TCP代理启动异常: 端口 {} (规则 {})，原因: {}",
                        port, rule.getRuleId(), e.getMessage());
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
                log.warn("invalid additional UDP port '{}', ruleId={}", portStr, rule.getRuleId());
                continue;
            }
            if (port == rule.getListenPort()) {
                continue;
            }

            try {
                UdpProxyRule clonedRule = cloneRuleForUdpPort(rule, port);
                UdpProxyServer server = new UdpProxyServer(
                        clonedRule, cryptoService, cryptoPacketStore, dataReportService, transcodeEnabled, transcodeService,
                        clientValidationService, perPacketClientValidationEnabled, trafficReconciliationService,
                        clientRelayProperties.isRejectDirectUdp(), securePublishIngressService, udpFileAssemblyService,
                        diagnosticLogReporter);
                server.start();
                if (server.isRunning()) {
                    servers.add(server);
                    log.info("additional UDP proxy started: port={}, ruleId={}, chainId={}, manufacturer={}",
                            port, rule.getRuleId(), rule.getChainId(), rule.getManufacturer());
                } else {
                    log.warn("additional UDP proxy start failed: port={}, ruleId={}", port, rule.getRuleId());
                }
            } catch (Exception e) {
                log.warn("additional UDP proxy start exception: port={}, ruleId={}, reason={}",
                        port, rule.getRuleId(), e.getMessage());
            }
        }

        if (!servers.isEmpty()) {
            additionalUdpServers.put(rule.getRuleId(), servers);
        }
    }

    /**
     * 克隆规则用于附加端口代理
     * 复用主规则的加密配置、厂家标识等，仅修改端口
     */
    private UdpProxyRule cloneRuleForPort(UdpProxyRule original, int port) {
        UdpProxyRule clone = new UdpProxyRule();
        clone.setRuleId(original.getRuleId() + "_tcp" + port);
        clone.setRuleName(original.getRuleName() + " (TCP:" + port + ")");
        clone.setListenIp(original.getListenIp());
        clone.setListenPort(port);
        clone.setSourceIp(original.getSourceIp());
        clone.setTargetIp(original.getTargetIp());
        clone.setTargetPort(port);
        clone.setEncryptEnabled(original.getEncryptEnabled());
        clone.setTerminalGatewayIp(original.getTerminalGatewayIp());
        // 约定：附加端口在终端网关上使用与情报板相同的端口号
        clone.setTerminalGatewayPort(port);
        clone.setManufacturer(original.getManufacturer());
        clone.setChainId(original.getChainId());
        clone.setBranchCode(original.getBranchCode());
        clone.setStatus(original.getStatus());
        clone.setProtocol("TCP");
        return clone;
    }

    private UdpProxyRule cloneRuleForUdpPort(UdpProxyRule original, int port) {
        UdpProxyRule clone = new UdpProxyRule();
        clone.setRuleId(original.getRuleId() + "_udp" + port);
        clone.setRuleName(original.getRuleName() + " (UDP:" + port + ")");
        clone.setListenIp(original.getListenIp());
        clone.setListenPort(port);
        clone.setSourceIp(original.getSourceIp());
        clone.setTargetIp(original.getTargetIp());
        clone.setTargetPort(port);
        clone.setEncryptEnabled(original.getEncryptEnabled());
        clone.setTerminalGatewayIp(original.getTerminalGatewayIp());
        clone.setTerminalGatewayPort(port);
        clone.setManufacturer(original.getManufacturer());
        clone.setChainId(original.getChainId());
        clone.setBranchCode(original.getBranchCode());
        clone.setStatus(original.getStatus());
        clone.setProtocol("UDP");
        return clone;
    }

    /**
     * 启动 CatchAll 动态端口透明代理（仅 Nova 大屏）
     *
     * <pre>
     * 条件守卫：dynamicPortProxyEnabled == true 时才启动
     * Sigma 等其他厂商该字段为 null/false，不会触发，完全隔离。
     *
     * 技术依据：
     *   Nova 大屏通过 16602/16606 控制通道协商随机动态端口（如 34431, 45219）进行文件传输。
     *   TPROXY 在 Linux 内核层拦截所有发往大屏 IP 的未知 TCP 端口并重定向到 CatchAll 代理。
     * </pre>
     */
    private void startCatchAllProxy(UdpProxyRule rule) {
        if (!Boolean.TRUE.equals(rule.getDynamicPortProxyEnabled())) {
            return; // Sigma 等厂商不触发
        }

        int port = rule.getCatchAllProxyPort() != null ? rule.getCatchAllProxyPort() : DEFAULT_CATCH_ALL_PORT;

        try {
            CatchAllTcpProxyServer server = new CatchAllTcpProxyServer(
                    rule, cryptoService, cryptoPacketStore, dataReportService, transcodeEnabled, transcodeService, port);
            server.start();
            if (server.isRunning()) {
                catchAllServers.put(rule.getRuleId(), server);
                log.info("✅ CatchAll动态端口代理启动成功: 规则={}, 端口={}", rule.getRuleId(), port);
            } else {
                log.warn("⚠️  CatchAll动态端口代理启动失败: 规则={}", rule.getRuleId());
            }
        } catch (Throwable t) {
            log.warn("⚠️  CatchAll动态端口代理启动异常: 规则={}，原因: {}", rule.getRuleId(), t.getMessage(), t);
        }
    }

    private void stopProxyServer(String ruleId) {
        log.info("【stopProxy诊断】目标ruleId={}, runningServers现有keys={}, runningTcpServers现有keys={}",
                ruleId, runningServers.keySet(), runningTcpServers.keySet());
        UdpProxyServer udpServer = runningServers.remove(ruleId);
        if (udpServer != null) {
            udpServer.stop();
            log.info("UDP代理服务已停止: {}", ruleId);
        } else {
            // 规则可能以"合并规则"形式存在于其他服务的 ipRuleMapping 中，需清除，否则停用后仍会转发
            for (UdpProxyServer s : runningServers.values()) {
                if (s.removeRule(ruleId)) {
                    log.info("已从 UDP 服务 [{}] 中清除合并规则: {}", s.getRule().getRuleId(), ruleId);
                }
            }
        }

        TcpProxyServer tcpServer = runningTcpServers.remove(ruleId);
        if (tcpServer != null) {
            tcpServer.stop();
            log.info("TCP代理服务已停止: {}", ruleId);
        } else {
            for (TcpProxyServer s : runningTcpServers.values()) {
                if (s.removeRule(ruleId)) {
                    log.info("已从 TCP 服务 [{}] 中清除合并规则: {}", s.getRule().getRuleId(), ruleId);
                }
            }
        }

        // 停止附加 TCP 代理（诺瓦多端口场景）
        List<TcpProxyServer> additionalServers = additionalTcpServers.remove(ruleId);
        if (additionalServers != null) {
            for (TcpProxyServer s : additionalServers) {
                s.stop();
            }
            log.info("附加TCP代理已全部停止: {}，共 {} 个", ruleId, additionalServers.size());
        }

        // 停止 CatchAll 动态端口透明代理（仅 Nova 大屏有此服务）
        List<UdpProxyServer> additionalUdp = additionalUdpServers.remove(ruleId);
        if (additionalUdp != null) {
            for (UdpProxyServer s : additionalUdp) {
                s.stop();
            }
            log.info("additional UDP proxies stopped: ruleId={}, count={}", ruleId, additionalUdp.size());
        }

        CatchAllTcpProxyServer catchAll = catchAllServers.remove(ruleId);
        if (catchAll != null) {
            catchAll.stop();
            log.info("CatchAll动态端口代理已停止: {}", ruleId);
        }
    }

    public void stopAll() {
        runningServers.values().forEach(UdpProxyServer::stop);
        runningServers.clear();
        runningTcpServers.values().forEach(TcpProxyServer::stop);
        runningTcpServers.clear();
        // 停止所有附加 TCP 代理
        additionalTcpServers.values().forEach(list -> list.forEach(TcpProxyServer::stop));
        additionalTcpServers.clear();
        // 停止所有 CatchAll 动态端口代理
        additionalUdpServers.values().forEach(list -> list.forEach(UdpProxyServer::stop));
        additionalUdpServers.clear();
        catchAllServers.values().forEach(CatchAllTcpProxyServer::stop);
        catchAllServers.clear();
    }

    /**
     * 按链路ID切换所有关联规则的状态（ENABLED / DISABLED），不逻辑删除
     * 由管控平台启用/停用链路时调用
     *
     * @param chainId 链路ID
     * @param status  目标状态："ENABLED" 或 "DISABLED"
     * @return 处理的规则数量
     */
    public int setChainRulesStatus(Long chainId, String status) {
        log.info("==========================================");
        log.info("  【发布网关】收到链路状态切换通知");
        log.info("  链路ID: {}, 目标状态: {}", chainId, status);
        log.info("==========================================");

        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getChainId, chainId)
               .eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("【发布网关】链路 {} 下无有效规则，无需处理", chainId);
            return 0;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                if ("DISABLED".equals(status)) {
                    // 停用：停止内存中的代理服务（含合并规则清理）
                    stopProxyServer(rule.getRuleId());
                } else if ("ENABLED".equals(status)) {
                    // 启用：先清理旧状态，再检测端口是否已被占用
                    stopProxyServer(rule.getRuleId());
                    String bindKey = buildBindKey(rule);
                    boolean alreadyRunning = runningServers.values().stream()
                            .anyMatch(s -> s.isRunning() && buildBindKey(s.getRule()).equals(bindKey));
                    if (alreadyRunning) {
                        // 端口已被占用，合并到已有服务（建立 IP -> Rule 映射），不重新绑定端口
                        log.info("规则 [{}] 的监听地址 ({}) 已有服务运行，执行合并", rule.getRuleId(), bindKey);
                        mergeRuleToRunningServer(bindKey, rule);
                    } else {
                        startProxyServer(rule);
                    }
                }
                // 更新数据库状态
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                      .set(UdpProxyRule::getStatus, status)
                      .set(UdpProxyRule::getUpdateTime, LocalDateTime.now());
                ruleMapper.update(null, update);

                log.info("\u2705 规则状态已更新: ruleId={}, status={}", rule.getRuleId(), status);
                count++;
            } catch (Exception e) {
                log.error("\u274c 处理规则失败: ruleId={}", rule.getRuleId(), e);
            }
        }

        log.info("【发布网关】链路 {} 规则状态切换完毕，共处理 {} 条", chainId, count);
        return count;
    }

    /**
     * 按链路ID逻辑删除所有关联规则并停止对应服务
     * 由管控平台删除链路时调用
     *
     * @param chainId 链路ID
     * @return 处理的规则数量
     */
    public int disableRuleByChainId(Long chainId) {
        log.info("==========================================" );
        log.info("  【发布网关】收到链路删除通知，停止并逻辑删除规则");
        log.info("  链路ID: {}", chainId);
        log.info("==========================================" );

        // 查询该链路下所有未删除的规则
        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getChainId, chainId)
               .eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("【发布网关】链路 {} 下无有效规则，无需处理", chainId);
            return 0;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                // 1. 停止内存中的代理服务
                stopProxyServer(rule.getRuleId());

                // 2. 逻辑删除数据库记录（deleted=1）
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                      .set(UdpProxyRule::getDeleted, 1)
                      .set(UdpProxyRule::getStatus, "DISABLED")
                      .set(UdpProxyRule::getRemark, "链路已删除")
                      .set(UdpProxyRule::getUpdateTime, LocalDateTime.now());
                ruleMapper.update(null, update);

                log.info("✅ 规则已逻辑删除: ruleId={}, chainId={}", rule.getRuleId(), chainId);
                count++;
            } catch (Exception e) {
                log.error("❌ 处理规则失败: ruleId={}", rule.getRuleId(), e);
            }
        }

        log.info("【发布网关】链路 {} 规则处理完毕，共处理 {} 条", chainId, count);
        return count;
    }
}

