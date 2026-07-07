package com.publishgateway.udpproxy.forward;

import com.publishgateway.udpproxy.assembly.FileAssemblyRequest;
import com.publishgateway.udpproxy.assembly.FileAssemblyResult;
import com.publishgateway.udpproxy.assembly.UdpFileAssemblyService;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.entity.message.Message;
import com.publishgateway.udpproxy.entity.message.MessageBuilder;
import com.publishgateway.udpproxy.entity.message.MessageHeader;
import com.publishgateway.udpproxy.log.DiagnosticLogReport;
import com.publishgateway.udpproxy.log.DiagnosticLogReporter;
import com.publishgateway.udpproxy.service.ClientValidationService;
import com.publishgateway.udpproxy.service.CryptoPacketStore;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.DataReportService;
import com.publishgateway.udpproxy.service.TrafficReconciliationService;
import com.publishgateway.udpproxy.service.TranscodeService;
import com.publishgateway.udpproxy.secure.SecurePublishIngressDecision;
import com.publishgateway.udpproxy.secure.SecurePublishIngressService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP代理服务器（发布网关版本 - 支持加密转发）
 *
 * 数据流：
 * Sigma --[UDP]--> 本代理(伪装IP) --[加密UDP]--> 终端网关 --[UDP]--> 情报板
 */
@Slf4j
public class UdpProxyServer {

    /** 转码开关：false=透传模式，true=转码模式（通过构造注入） */
    private final boolean transcodeEnabled;
    private final TranscodeService transcodeService;

    private final UdpProxyRule rule;
    private final CryptoService cryptoService;
    private final CryptoPacketStore cryptoPacketStore;
    private final DataReportService dataReportService;
    /** 来源 IP:Port 合法性校验服务（同步调用 info-publish-client） */
    private final ClientValidationService clientValidationService;
    private final boolean perPacketClientValidationEnabled;
    private final TrafficReconciliationService trafficReconciliationService;
    private final boolean rejectDirectUdp;
    private final SecurePublishIngressService securePublishIngressService;
    private final UdpFileAssemblyService udpFileAssemblyService;
    private final DiagnosticLogReporter diagnosticLogReporter;
    private EventLoopGroup group;
    private Channel serverChannel;
    private volatile boolean running = false;
    private static final int MAX_UDP_DATAGRAM_BYTES = 65_535;

    /**
     * 存储客户端地址和对应的出站通道
     * Key: 客户端地址字符串, Value: 连接到终端网关的UDP Channel
     */
    private final Map<String, Channel> clientChannels = new ConcurrentHashMap<>();

    /**
     * 动态追加的允许IP集合（端口复用时合并其他规则的 sourceIp）
     */
    private final Set<String> extraAllowedIps = ConcurrentHashMap.newKeySet();

    /**
     * 端口复用时，sourceIp -> 对应的规则映射
     * 用于根据发送方IP路由到正确的 ruleId/chainId 进行数据上报和告警
     */
    private final Map<String, UdpProxyRule> ipRuleMapping = new ConcurrentHashMap<>();

    public UdpProxyServer(UdpProxyRule rule, CryptoService cryptoService,
                          CryptoPacketStore cryptoPacketStore,
                          DataReportService dataReportService,
                          boolean transcodeEnabled, TranscodeService transcodeService,
                          ClientValidationService clientValidationService,
                          boolean perPacketClientValidationEnabled,
                          TrafficReconciliationService trafficReconciliationService,
                          boolean rejectDirectUdp,
                          SecurePublishIngressService securePublishIngressService,
                          UdpFileAssemblyService udpFileAssemblyService,
                          DiagnosticLogReporter diagnosticLogReporter) {
        this.rule                     = rule;
        this.cryptoService            = cryptoService;
        this.cryptoPacketStore        = cryptoPacketStore;
        this.dataReportService        = dataReportService;
        this.transcodeEnabled         = transcodeEnabled;
        this.transcodeService         = transcodeService;
        this.clientValidationService  = clientValidationService;
        this.perPacketClientValidationEnabled = perPacketClientValidationEnabled;
        this.trafficReconciliationService = trafficReconciliationService;
        this.rejectDirectUdp = rejectDirectUdp;
        this.securePublishIngressService = securePublishIngressService;
        this.udpFileAssemblyService = udpFileAssemblyService;
        this.diagnosticLogReporter = diagnosticLogReporter;
    }

    /**
     * 启动UDP代理服务器
     */
    public void start() {
        if (running) {
            log.warn("UDP代理服务器已在运行，规则ID: {}", rule.getRuleId());
            return;
        }

        group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 2048 * 1024)
                    .option(ChannelOption.RCVBUF_ALLOCATOR,
                            new FixedRecvByteBufAllocator(MAX_UDP_DATAGRAM_BYTES))
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                    .handler(new UdpProxyHandler());

            // 绑定到指定IP和端口（不做回退，失败直接抛出，避免误占端口导致其他规则连锁失败）
            InetSocketAddress bindAddress;
            if (rule.getListenIp() != null && !rule.getListenIp().isEmpty()) {
                try {
                    // 先校验IP地址格式是否合法
                    java.net.InetAddress.getByName(rule.getListenIp());
                    bindAddress = new InetSocketAddress(rule.getListenIp(), rule.getListenPort());
                } catch (java.net.UnknownHostException e) {
                    throw new IllegalArgumentException(
                            "监听IP地址非法，规则ID: " + rule.getRuleId() + ", IP: " + rule.getListenIp(), e);
                }
            } else {
                bindAddress = new InetSocketAddress(rule.getListenPort());
            }

            ChannelFuture future = bootstrap.bind(bindAddress).sync();

            serverChannel = future.channel();
            running = true;

            String actualListenIp = ((InetSocketAddress) serverChannel.localAddress()).getAddress().getHostAddress();
            log.info("==========================================");
            log.info("  UDP代理服务器启动成功（发布网关）");
            log.info("  规则ID: {}", rule.getRuleId());
            log.info("  规则名称: {}", rule.getRuleName());
            log.info("  监听地址: {}:{} (UDP)", actualListenIp, rule.getListenPort());

            // 判断是否启用加密转发
            if (Boolean.TRUE.equals(rule.getEncryptEnabled()) &&
                    rule.getTerminalGatewayIp() != null && rule.getTerminalGatewayPort() != null) {
                log.info("  加密转发: 已启用");
                log.info("  终端网关: {}:{}", rule.getTerminalGatewayIp(), rule.getTerminalGatewayPort());
            } else {
                log.info("  直接转发: {}:{}", rule.getTargetIp(), rule.getTargetPort());
            }

            if (rule.getSourceIp() != null && !rule.getSourceIp().isEmpty()) {
                log.info("  源IP白名单: {}", rule.getSourceIp());
            }
            log.info("==========================================");

        } catch (IllegalArgumentException e) {
            // 配置错误（如IP地址非法），属于预期的校验失败，只记录警告信息，不打印完整堆栈
            log.warn("UDP代理服务器配置错误，规则ID: {}，原因: {}", rule.getRuleId(), e.getMessage());
            stop();
        } catch (Exception e) {
            // 意外的运行时异常，打印完整堆栈便于排查
            log.error("UDP代理服务器启动失败，规则ID: {}", rule.getRuleId(), e);
            stop();
        }
    }

    /**
     * 停止UDP代理服务器
     */
    public void stop() {
        if (!running) {
            log.debug("UDP代理服务器未运行，无需停止，规则ID: {}", rule.getRuleId());
            return;
        }
        
        running = false;
        log.info("正在停止UDP代理服务器，规则ID: {}", rule.getRuleId());

        // 1. 关闭所有客户端连接
        for (Channel channel : clientChannels.values()) {
            if (channel != null) {
                try {
                    channel.close().sync();
                } catch (InterruptedException e) {
                    log.error("关闭客户端通道异常", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        clientChannels.clear();

        // 2. ⭐ 同步关闭服务器通道（释放端口）
        if (serverChannel != null) {
            try {
                serverChannel.close().sync();
                log.info("✅ 服务器通道已关闭，端口已释放");
            } catch (InterruptedException e) {
                log.error("关闭服务器通道异常", e);
                Thread.currentThread().interrupt();
            }
        }
        
        // 3. ⭐ 同步关闭EventLoopGroup（释放线程资源）
        if (group != null) {
            try {
                group.shutdownGracefully().sync();
                log.info("✅ EventLoopGroup已关闭");
            } catch (InterruptedException e) {
                log.error("关闭EventLoopGroup异常", e);
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("==========================================");
        log.info("  ✅ UDP代理服务器已完全停止");
        log.info("  规则ID: {}", rule.getRuleId());
        log.info("==========================================");
    }

    public boolean isRunning() {
        return running;
    }

    public UdpProxyRule getRule() {
        return rule;
    }

    /**
     * 合并其他规则到当前服务（端口复用时调用）
     * 建立 sourceIp -> rule 的映射，收到数据时根据发送方IP路由到正确规则
     * @param mergedRule 被合并的规则（与当前服务共享监听端口）
     */
    public void mergeRule(UdpProxyRule mergedRule) {
        if (mergedRule == null || mergedRule.getSourceIp() == null || mergedRule.getSourceIp().isEmpty()) return;
        for (String ip : mergedRule.getSourceIp().split(",")) {
            String trimmed = ip.trim();
            if (!trimmed.isEmpty()) {
                extraAllowedIps.add(trimmed);
                ipRuleMapping.put(trimmed, mergedRule);
                log.info("【发布网关】合并规则 IP映射: {} -> ruleId={}, chainId={}",
                        trimmed, mergedRule.getRuleId(), mergedRule.getChainId());
            }
        }
    }

    /**
     * 端口复用时，从当前服务中移除指定规则的 IP 映射（用于链路停用）
     * @param ruleId 被停用的规则ID
     * @return 是否移除了至少一条映射
     */
    public boolean removeRule(String ruleId) {
        boolean removed = false;
        java.util.Iterator<java.util.Map.Entry<String, UdpProxyRule>> it =
                ipRuleMapping.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, UdpProxyRule> entry = it.next();
            if (ruleId.equals(entry.getValue().getRuleId())) {
                extraAllowedIps.remove(entry.getKey());
                it.remove();
                removed = true;
                log.info("【发布网关-UDP】已从服务 [{}] 中移除合并规则 IP 映射: {} -> {}",
                        this.rule.getRuleId(), entry.getKey(), ruleId);
            }
        }
        return removed;
    }

    /**
     * 根据发送方IP解析对应的规则（端口复用时路由到正确链路）
     * @param senderIp 数据发送方IP
     * @return 匹配的规则，未匹配时返回当前服务自身的规则
     */
    private UdpProxyRule resolveRule(String senderIp) {
        UdpProxyRule mapped = ipRuleMapping.get(senderIp);
        return mapped != null ? mapped : this.rule;
    }

    /**
     * UDP代理处理器
     */
    private class UdpProxyHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            InetSocketAddress sender = packet.sender();
            String senderIp  = sender.getAddress().getHostAddress();
            String senderKey = senderIp + ":" + sender.getPort();
            ByteBuf content = packet.content();
            int dataLength = content.readableBytes();
            UdpProxyRule effectiveRule = resolveRule(senderIp);
            if (rejectDirectUdp) {
                log.warn("[ClientRelay] reject direct UDP ingress: source={}:{}, ruleId={}, listenPort={}",
                        senderIp, sender.getPort(), effectiveRule.getRuleId(), effectiveRule.getListenPort());
                return;
            }
            if (!cryptoService.isSvacModuleReady()) {
                log.warn("publish gateway SVAC module unavailable, drop UDP packet: source={}, status={}",
                        sender, cryptoService.getSvacModuleStatus());
                reportFailure("SVAC_MODULE_UNAVAILABLE", effectiveRule, senderIp, sender.getPort(),
                        "svac module unavailable", "SVAC_MODULE_UNAVAILABLE",
                        cryptoService.getSvacModuleStatus());
                return;
            }
            log.debug("==========================================");
            log.debug("  【发布网关】收到数据包");
            log.debug("  来源: {}", sender);
            log.debug("  数据长度: {} 字节", dataLength);
            log.debug("==========================================");

            // ── 来源IP白名单过滤（发布网关已有的粗粒度 IP 校验）──
            // 允许条件：在 ipRuleMapping（合并规则）中存在，或属于主规则的 sourceIp
            boolean allowed = ipRuleMapping.containsKey(senderIp);
            if (!allowed && rule.getSourceIp() != null && !rule.getSourceIp().isEmpty()) {
                for (String ip : rule.getSourceIp().split(",")) {
                    if (senderIp.equals(ip.trim())) { allowed = true; break; }
                }
            }
            if (!allowed) {
                log.warn("【发布网关】拒绝非授权来源 - 来源IP: {}, 主规则授权: {}, 规则ID: {}",
                        senderIp, rule.getSourceIp(), rule.getRuleId());
                reportFailure("SOURCE_IP_REJECTED", effectiveRule, senderIp, sender.getPort(),
                        "source ip rejected", "SOURCE_IP_NOT_ALLOWED",
                        "sourceIp=" + senderIp + ", allowed=" + rule.getSourceIp());
                return;
            }

            // ── 来源 IP:Port 精细校验（同步调用客户端 netstat 白名单，防同机木马）──
            // 客户端在收到请求后触发 netstat 刷新：此刻 Sigma 的 Socket 必然存在，可被捕获。
            // 不可达时降级放行（见 ClientValidationService 降级策略），不阻断业务。
            if (perPacketClientValidationEnabled && clientValidationService != null) {
                boolean sourceAuthorized = clientValidationService.validateSource(senderIp, sender.getPort(), effectiveRule.getSourceIp());
                if (!sourceAuthorized) {
                    log.warn("【发布网关】来源校验拒绝 - IP:Port={}:{}, 规则ID: {}",
                            senderIp, sender.getPort(), effectiveRule.getRuleId());
                    reportFailure("SOURCE_CHECK_FAILED", effectiveRule, senderIp, sender.getPort(),
                            "source ip port validation failed", "SOURCE_IP_PORT_INVALID",
                            "sourceIp=" + senderIp + ", sourcePort=" + sender.getPort());
                    return;
                }
            }

            // 复制数据
            byte[] data = new byte[dataLength];
            content.readBytes(data);

            if (securePublishIngressService != null) {
                SecurePublishIngressDecision decision = securePublishIngressService.inspect(
                        effectiveRule.getRuleId(), effectiveRule.getChainId(), data, senderIp);
                if (decision.getAction() == SecurePublishIngressDecision.Action.HOLD) {
                    log.debug("[SecurePublish] holding secure package packet: package={}, source={}:{}",
                            decision.getPackageName(), senderIp, sender.getPort());
                    return;
                }
                if (decision.getAction() == SecurePublishIngressDecision.Action.REJECT) {
                    log.warn("[SecurePublish] rejected secure package: package={}, reason={}, source={}:{}",
                            decision.getPackageName(), decision.getReason(), senderIp, sender.getPort());
                    reportFailure("VERIFY_FAILED", effectiveRule, senderIp, sender.getPort(),
                            "secure package verify failed", "VERIFY_SIGNATURE_INVALID",
                            decision.getReason());
                    return;
                }
                if (decision.getAction() == SecurePublishIngressDecision.Action.FORWARD_PAYLOAD) {
                    byte[] verifiedPayload = decision.getForwardData();
                    if (verifiedPayload == null || verifiedPayload.length == 0) {
                        log.warn("[SecurePublish] verified payload empty, drop package={}", decision.getPackageName());
                        reportFailure("CONTENT_PARSE_FAILED", effectiveRule, senderIp, sender.getPort(),
                                "secure package payload empty", "SECURE_PAYLOAD_EMPTY",
                                "package=" + decision.getPackageName());
                        return;
                    }
                    data = verifiedPayload;
                    dataLength = verifiedPayload.length;
                    log.info("[SecurePublish] forwarding verified payload: package={}, payload={}, bytes={}",
                            decision.getPackageName(), decision.getPayloadName(), dataLength);
                }
            }

            if (trafficReconciliationService != null) {
                trafficReconciliationService.recordGatewayReceive(effectiveRule, senderIp, sender.getPort(), dataLength);
            }

            if (!observeFileAssembly(effectiveRule, data, senderIp, sender.getPort())) {
                return;
            }

            // === 数据上报到管控平台 ===
            if (dataReportService != null) {
                log.debug("【发布网关】数据归属 - 来源IP: {}, 匹配规则: {}, chainId: {}",
                        senderIp, effectiveRule.getRuleId(), effectiveRule.getChainId());
                dataReportService.reportAsync(effectiveRule.getRuleId(), effectiveRule.getChainId(), data, senderIp, effectiveRule.getManufacturer(), effectiveRule.getTargetIp(), effectiveRule.getTargetPort());
            }

            // 判断是否需要加密转发（用 effectiveRule 决定转发目标，支持端口复用下各链路独立路由）
            boolean needEncrypt = Boolean.TRUE.equals(effectiveRule.getEncryptEnabled()) &&
                    effectiveRule.getTerminalGatewayIp() != null &&
                    effectiveRule.getTerminalGatewayPort() != null;

            if (needEncrypt && cryptoService.isSvacMode()) {
                String rawTargetIp = effectiveRule.getTerminalGatewayIp();
                int rawTargetPort = effectiveRule.getTerminalGatewayPort();

                byte[] encrypted = cryptoService.encrypt(data);
                if (encrypted == null) {
                    log.warn("publish gateway SVAC UDP raw packet encrypt failed, drop packet");
                    reportFailure("ENCRYPT_FAILED", effectiveRule, senderIp, sender.getPort(),
                            "svac raw udp encrypt failed", "ENCRYPT_RESULT_EMPTY",
                            "payloadBytes=" + data.length);
                    return;
                }
                if (cryptoPacketStore != null) {
                    cryptoPacketStore.saveRawUdp(
                            effectiveRule.getRuleId(), effectiveRule.getChainId(),
                            senderIp, rawTargetIp, rawTargetPort, data, encrypted);
                }
                log.debug("publish gateway SVAC UDP raw packet encrypted: {} bytes -> {} bytes",
                        data.length, encrypted.length);

                Channel outboundChannel = clientChannels.get(senderKey);
                if (outboundChannel == null || !outboundChannel.isActive()) {
                    createAndForward(ctx, sender, senderKey, encrypted, rawTargetIp, rawTargetPort, dataLength, effectiveRule);
                } else {
                    forwardToTarget(outboundChannel, encrypted, rawTargetIp, rawTargetPort, dataLength);
                }
                return;
            }

            // ── 转码处理（透传模式下 transcodeService.transcode 原样返回 data） ──
            byte[] bodyData = (transcodeService != null)
                    ? transcodeService.transcode(data, TranscodeService.DataType.TEXT)
                    : data;

            // ── 封装 Message（Header + Body），UDP 自动分片 ──
            java.util.List<Message> messages = MessageBuilder.buildForUdp(
                    bodyData, transcodeEnabled,
                    MessageHeader.MSG_TYPE_PASSTHROUGH, MessageHeader.ENCODING_NONE);

            // 确定转发目标（来自 effectiveRule，确保合并链路路由到各自的终端网关端口）
            String targetIp   = needEncrypt ? effectiveRule.getTerminalGatewayIp() : effectiveRule.getTargetIp();
            int    targetPort = needEncrypt ? effectiveRule.getTerminalGatewayPort() : effectiveRule.getTargetPort();

            // 获取或创建到目标的UDP通道（每个分片使用同一通道发出）
            Channel outboundChannel = clientChannels.get(senderKey);

            for (Message msg : messages) {
                // 序列化 Message
                byte[] msgBytes = msg.toBytes();

                // 加密整个 Message（包含 Header + Body）
                byte[] finalData = msgBytes;
                if (needEncrypt) {
                    byte[] encrypted = cryptoService.encrypt(msgBytes);
                    if (encrypted != null) {
                        if (cryptoPacketStore != null) {
                            cryptoPacketStore.saveMessage(
                                    effectiveRule.getRuleId(), effectiveRule.getChainId(), "UDP",
                                    senderIp, targetIp, targetPort,
                                    msg.getHeader(), msgBytes, encrypted);
                        }
                        log.debug("【发布网关】Message已加密：{}字节 -> {}字节", msgBytes.length, encrypted.length);
                        finalData = encrypted;
                    } else {
                        log.warn("【发布网关】加密失败，丢弃该分片（messageId={})",
                                msg.getHeader().getMessageId());
                        reportFailure("ENCRYPT_FAILED", effectiveRule, senderIp, sender.getPort(),
                                "message encrypt failed", "ENCRYPT_RESULT_EMPTY",
                                "messageId=" + msg.getHeader().getMessageId());
                        continue;
                    }
                }

                if (outboundChannel == null || !outboundChannel.isActive()) {
                    createAndForward(ctx, sender, senderKey, finalData, targetIp, targetPort, dataLength, effectiveRule);
                    // 首包创建通道后，后续分片复用同一通道
                    outboundChannel = clientChannels.get(senderKey);
                } else {
                    forwardToTarget(outboundChannel, finalData, targetIp, targetPort, dataLength);
                }
            }
        }

        /**
         * 创建新通道并转发
         */
        private void createAndForward(ChannelHandlerContext ctx, InetSocketAddress sender,
                                      String senderKey, byte[] data, String targetIp,
                                      int targetPort, int originalLength,
                                      UdpProxyRule effectiveRule) {
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.RCVBUF_ALLOCATOR,
                            new FixedRecvByteBufAllocator(MAX_UDP_DATAGRAM_BYTES))
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx2, DatagramPacket responsePacket) {
                            // 接收响应（可能来自终端网关或情报板）
                            ByteBuf responseBuf = responsePacket.content();
                            int responseLen = responseBuf.readableBytes();
                            byte[] responseData = new byte[responseLen];
                            responseBuf.readBytes(responseData);

                            // 解密：终端网关加密回传的情报板响应，解密后转发给 Sigma
                            byte[] finalResponse = responseData;
                            if (Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                                byte[] decrypted = cryptoService.decrypt(responseData);
                                if (decrypted != null) {
                                    log.debug("【发布网关】响应已解密：{}字节 -> {}字节",
                                            responseData.length, decrypted.length);
                                    finalResponse = decrypted;
                                } else {
                                    log.error("【发布网关】响应解密失败，丢弃该响应（禁止透传密文），规则ID: {}",
                                            rule.getRuleId());
                                    return;
                                }
                            }

                            log.debug("【发布网关】收到响应 - 长度: {} 字节，转发回: {}",
                                    finalResponse.length, sender);


                            ByteBuf buf = Unpooled.copiedBuffer(finalResponse);
                            DatagramPacket replyPacket = new DatagramPacket(buf, sender);
                            serverChannel.writeAndFlush(replyPacket);
                        }
                    });

            // 异步绑定
            b.bind(0).addListener((ChannelFutureListener) bindFuture -> {
                if (bindFuture.isSuccess()) {
                    Channel newChannel = bindFuture.channel();
                    clientChannels.put(senderKey, newChannel);
                    log.debug("【发布网关】为客户端 {} 创建新的出站通道", senderKey);

                    forwardToTarget(newChannel, data, targetIp, targetPort, originalLength);
                } else {
                    log.error("【发布网关】创建出站通道失败", bindFuture.cause());
                    reportFailure("FORWARD_FAILED", effectiveRule,
                            sender.getAddress().getHostAddress(), sender.getPort(),
                            "create outbound channel failed", "OUTBOUND_CHANNEL_CREATE_FAILED",
                            bindFuture.cause() == null ? null : bindFuture.cause().getMessage());
                }
            });
        }

        /**
         * 转发到目标
         */
        private void forwardToTarget(Channel channel, byte[] data, String targetIp,
                                     int targetPort, int originalLength) {
            InetSocketAddress targetAddress = new InetSocketAddress(targetIp, targetPort);
            ByteBuf buf = Unpooled.copiedBuffer(data);
            DatagramPacket outPacket = new DatagramPacket(buf, targetAddress);

            channel.writeAndFlush(outPacket).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.debug("【发布网关】数据已转发 - 目标: {}:{}, 原始字节: {}, 发送字节: {}",
                            targetIp, targetPort, originalLength, data.length);
                } else {
                    log.error("【发布网关】转发失败", future.cause());
                    reportFailure("FORWARD_FAILED", rule, null, null,
                            "udp forward failed", "UDP_FORWARD_FAILED",
                            future.cause() == null ? null : future.cause().getMessage());
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【发布网关】处理异常，规则ID: {}", rule.getRuleId(), cause);
            reportFailure("PUBLISH_GATEWAY_EXCEPTION", rule, null, null,
                    "publish gateway handler exception", "HANDLER_EXCEPTION",
                    cause == null ? null : cause.getMessage());
        }
    }

    private void reportFailure(String eventType, UdpProxyRule currentRule, String sourceIp, Integer sourcePort,
                               String summary, String errorCode, String errorMessage) {
        if (diagnosticLogReporter == null || currentRule == null) {
            return;
        }
        DiagnosticLogReport report = new DiagnosticLogReport();
        report.setEventType(eventType);
        report.setEventLevel("error");
        report.setStage("publish_gateway");
        report.setServiceName("gateway-udp-proxy");
        report.setChainId(currentRule.getChainId());
        report.setChainCode(currentRule.getChainCode());
        report.setSourceIp(sourceIp);
        report.setSourcePort(sourcePort);
        report.setBoardIp(currentRule.getTargetIp());
        report.setBoardPort(currentRule.getTargetPort());
        report.setVerifyStatus("VERIFY_FAILED".equals(eventType) ? "fail" : null);
        report.setResultStatus("fail");
        report.setSummary(summary);
        report.setErrorCode(errorCode);
        report.setErrorMessage(errorMessage);
        report.setRefTable("udp_proxy_rule");
        report.setRefId(currentRule.getId() == null ? currentRule.getRuleId() : String.valueOf(currentRule.getId()));
        report.setDedupKey(eventType + ":" + currentRule.getRuleId() + ":" + sourceIp + ":" + sourcePort);
        diagnosticLogReporter.reportAsync(report);
    }

    private boolean observeFileAssembly(UdpProxyRule effectiveRule, byte[] data, String senderIp, int senderPort) {
        if (udpFileAssemblyService == null || effectiveRule == null || data == null || data.length == 0) {
            return true;
        }
        try {
            FileAssemblyResult result = udpFileAssemblyService.accept(FileAssemblyRequest.builder()
                    .ruleId(effectiveRule.getRuleId())
                    .chainId(effectiveRule.getChainId())
                    .manufacturer(effectiveRule.getManufacturer())
                    .sourceIp(senderIp)
                    .sourcePort(senderPort)
                    .targetIp(effectiveRule.getTargetIp())
                    .targetPort(effectiveRule.getTargetPort())
                    .payload(data)
                    .receivedAt(System.currentTimeMillis())
                    .build());
            if (result != null && result.isCompleted() && result.getFile() != null) {
                log.info("【重组文件】UDP入口完成: fileId={}, file={}, size={}B, sha256={}, securePublishPresent={}, ruleId={}",
                        result.getFile().getFileId(), result.getFile().getFileName(),
                        result.getFile().getTotalSize(), result.getFile().getSha256(),
                        result.getFile().getSecurePublishBlockPresent(), effectiveRule.getRuleId());
                if (Boolean.FALSE.equals(result.getFile().getSecurePublishAllowed())) {
                    log.warn("[SecurePublish-JPEG] block current UDP packet after failed verify: file={}, reason={}, ruleId={}, source={}:{}",
                            result.getFile().getFileName(), result.getFile().getSecurePublishVerifyReason(),
                            effectiveRule.getRuleId(), senderIp, senderPort);
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("【重组文件】UDP入口旁路处理失败，不影响转发: ruleId={}, error={}",
                    effectiveRule.getRuleId(), e.getMessage(), e);
        }
        return true;
    }
}
