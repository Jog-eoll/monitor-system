package com.publishgateway.udpproxy.relay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.publishgateway.udpproxy.assembly.FileAssemblyRequest;
import com.publishgateway.udpproxy.assembly.FileAssemblyResult;
import com.publishgateway.udpproxy.assembly.UdpFileAssemblyService;
import com.publishgateway.udpproxy.config.ClientRelayProperties;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.entity.message.Message;
import com.publishgateway.udpproxy.entity.message.MessageBuilder;
import com.publishgateway.udpproxy.entity.message.MessageHeader;
import com.publishgateway.udpproxy.mapper.UdpProxyRuleMapper;
import com.publishgateway.udpproxy.secure.SecurePublishIngressDecision;
import com.publishgateway.udpproxy.secure.SecurePublishIngressService;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.CryptoPacketStore;
import com.publishgateway.udpproxy.service.ContentReleaseTokenService;
import com.publishgateway.udpproxy.service.ContentReleaseTokenVerifyResult;
import com.publishgateway.udpproxy.service.DataReportService;
import com.publishgateway.udpproxy.service.TrafficReconciliationService;
import com.publishgateway.udpproxy.service.TranscodeService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Receives UDP packets relayed by Windows info-publish-client.
 */
@Slf4j
@Component
public class ClientRelayReceiver implements ApplicationRunner, Ordered {

    @Resource
    private ClientRelayProperties properties;

    @Resource
    private UdpProxyRuleMapper ruleMapper;

    @Resource
    private CryptoService cryptoService;

    @Resource
    private CryptoPacketStore cryptoPacketStore;

    @Resource
    private DataReportService dataReportService;

    @Resource
    private TranscodeService transcodeService;

    @Resource
    private TrafficReconciliationService trafficReconciliationService;

    @Resource
    private ContentReleaseTokenService contentReleaseTokenService;

    @Resource
    private SecurePublishIngressService securePublishIngressService;

    @Resource
    private UdpFileAssemblyService udpFileAssemblyService;


    @Value("${gateway.transcode.enabled:false}")
    private boolean transcodeEnabled;

    private final RelayPacketCodec codec = new RelayPacketCodec();
    private final Map<String, Channel> outboundChannels = new ConcurrentHashMap<>();
    private final AtomicLong packetsReceived = new AtomicLong();
    private final AtomicLong packetsAccepted = new AtomicLong();
    private final AtomicLong packetsRejected = new AtomicLong();
    private final AtomicLong packetsForwarded = new AtomicLong();
    private final AtomicLong responsesRelayed = new AtomicLong();

    private volatile EventLoopGroup group;
    private volatile Channel serverChannel;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isEnabled()) {
            start();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @PreDestroy
    public void stop() {
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Channel channel : outboundChannels.values()) {
            if (channel != null) {
                channel.close();
            }
        }
        outboundChannels.clear();
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("running", serverChannel != null && serverChannel.isActive());
        status.put("relayPort", properties.getRelayPort());
        status.put("trustedClientIps", properties.getTrustedClientIps());
        status.put("verifySignature", properties.isVerifySignature());
        status.put("rejectDirectUdp", properties.isRejectDirectUdp());
        status.put("packetsReceived", packetsReceived.get());
        status.put("packetsAccepted", packetsAccepted.get());
        status.put("packetsRejected", packetsRejected.get());
        status.put("packetsForwarded", packetsForwarded.get());
        status.put("responsesRelayed", responsesRelayed.get());
        status.put("outboundChannels", outboundChannels.size());
        if (contentReleaseTokenService != null) {
            status.put("contentToken", contentReleaseTokenService.getStatus());
        }
        return status;
    }

    private void start() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 2048 * 1024)
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                    .handler(new RelayHandler());
            serverChannel = bootstrap.bind(properties.getRelayPort()).sync().channel();
            log.info("[ClientRelay] receiver started: port={}", properties.getRelayPort());
        } catch (Exception e) {
            log.error("[ClientRelay] receiver start failed: {}", e.getMessage(), e);
            stop();
        }
    }

    private class RelayHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            packetsReceived.incrementAndGet();
            InetSocketAddress sender = packet.sender();
            String relayClientIp = sender.getAddress().getHostAddress();
            if (!isTrusted(relayClientIp)) {
                packetsRejected.incrementAndGet();
                log.warn("[ClientRelay] reject untrusted client: {}", relayClientIp);
                return;
            }

            try {
                ByteBuf content = packet.content();
                byte[] bytes = new byte[content.readableBytes()];
                content.readBytes(bytes);
                RelayPacketCodec.RelayPacket relayPacket = codec.decode(
                        bytes,
                        properties.isVerifySignature(),
                        properties.getSignatureSecret());
                UdpProxyRule rule = resolveRule(relayPacket);
                if (rule == null) {
                    packetsRejected.incrementAndGet();
                    log.warn("[ClientRelay] no enabled rule for dst={}:{}, src={}:{}, process={}",
                            relayPacket.getOriginalDstIp(),
                            relayPacket.getOriginalDstPort(),
                            relayPacket.getOriginalSrcIp(),
                            relayPacket.getOriginalSrcPort(),
                            relayPacket.getProcessName());
                    return;
                }
                packetsAccepted.incrementAndGet();
                handleRelayPayload(ctx, sender, relayPacket, rule);
            } catch (Exception e) {
                packetsRejected.incrementAndGet();
                log.warn("[ClientRelay] packet handling failed: {}", e.getMessage());
            }
        }
    }

    private boolean isTrusted(String relayClientIp) {
        List<String> trusted = properties.getTrustedClientIps();
        if (trusted == null || trusted.isEmpty()) {
            return true;
        }
        boolean hasRule = false;
        for (String item : trusted) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            hasRule = true;
            if (relayClientIp.equals(item.trim())) {
                return true;
            }
        }
        return !hasRule;
    }

    private UdpProxyRule resolveRule(RelayPacketCodec.RelayPacket relayPacket) {
        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getStatus, "ENABLED")
                .eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (UdpProxyRule rule : rules) {
            if (matchesRule(rule, relayPacket)) {
                return rule;
            }
        }
        return null;
    }

    private boolean matchesRule(UdpProxyRule rule, RelayPacketCodec.RelayPacket packet) {
        if (rule == null || packet == null || rule.getListenPort() == null) {
            return false;
        }
        if (rule.getListenPort() != packet.getOriginalDstPort()) {
            return false;
        }
        if (rule.getSourceIp() != null && !rule.getSourceIp().trim().isEmpty()) {
            boolean sourceMatched = false;
            for (String ip : rule.getSourceIp().split(",")) {
                if (packet.getOriginalSrcIp().equals(ip.trim())) {
                    sourceMatched = true;
                    break;
                }
            }
            if (!sourceMatched) {
                return false;
            }
        }
        if (rule.getListenIp() != null && !rule.getListenIp().trim().isEmpty()
                && !"0.0.0.0".equals(rule.getListenIp().trim())) {
            return packet.getOriginalDstIp().equals(rule.getListenIp().trim());
        }
        return true;
    }

    private void handleRelayPayload(ChannelHandlerContext ctx,
                                    InetSocketAddress relayClient,
                                    RelayPacketCodec.RelayPacket relayPacket,
                                    UdpProxyRule rule) {
        byte[] data = relayPacket.getPayload();
        if (data == null || data.length == 0) {
            return;
        }
        String sourceIp = relayPacket.getOriginalSrcIp();
        if (securePublishIngressService != null) {
            SecurePublishIngressDecision decision = securePublishIngressService.inspect(
                    rule.getRuleId(), rule.getChainId(), data, sourceIp);
            if (decision.getAction() == SecurePublishIngressDecision.Action.HOLD) {
                log.debug("[SecurePublish] holding relayed secure package packet: package={}, source={}:{}",
                        decision.getPackageName(), sourceIp, relayPacket.getOriginalSrcPort());
                return;
            }
            if (decision.getAction() == SecurePublishIngressDecision.Action.REJECT) {
                packetsRejected.incrementAndGet();
                log.warn("[SecurePublish] rejected relayed secure package: package={}, reason={}, source={}:{}",
                        decision.getPackageName(), decision.getReason(), sourceIp, relayPacket.getOriginalSrcPort());
                return;
            }
            if (decision.getAction() == SecurePublishIngressDecision.Action.FORWARD_PAYLOAD) {
                byte[] verifiedPayload = decision.getForwardData();
                if (verifiedPayload == null || verifiedPayload.length == 0) {
                    packetsRejected.incrementAndGet();
                    log.warn("[SecurePublish] verified relayed payload empty, drop package={}", decision.getPackageName());
                    return;
                }
                data = verifiedPayload;
                relayPacket.setPayload(verifiedPayload);
                log.info("[SecurePublish] forwarding verified relayed payload: package={}, payload={}, bytes={}",
                        decision.getPackageName(), decision.getPayloadName(), verifiedPayload.length);
            }
        }
        if (contentReleaseTokenService != null) {
            ContentReleaseTokenVerifyResult tokenResult = contentReleaseTokenService.verifyRelayPacket(relayPacket, rule);
            if (tokenResult != null && !tokenResult.isAllowed()) {
                packetsRejected.incrementAndGet();
                log.warn("[ClientRelay] content token rejected: reason={}, tokenId={}, fileId={}, src={}:{}, dst={}:{}",
                        tokenResult.getReason(),
                        relayPacket.getContentTokenId(),
                        relayPacket.getContentFileId(),
                        relayPacket.getOriginalSrcIp(),
                        relayPacket.getOriginalSrcPort(),
                        relayPacket.getOriginalDstIp(),
                        relayPacket.getOriginalDstPort());
                return;
            }
        }
        if (trafficReconciliationService != null) {
            trafficReconciliationService.recordGatewayReceive(rule, sourceIp, relayPacket.getOriginalSrcPort(), data.length);
        }
        FileAssemblyResult assemblyResult = observeFileAssembly(rule, relayPacket, data, sourceIp);
        boolean assemblyRejected = isSecurePublishRejected(assemblyResult);
        boolean assemblyAllowed = isSecurePublishAllowed(assemblyResult);
        boolean needEncrypt = Boolean.TRUE.equals(rule.getEncryptEnabled())
                && rule.getTerminalGatewayIp() != null
                && rule.getTerminalGatewayPort() != null;
        String targetIp = needEncrypt ? rule.getTerminalGatewayIp() : rule.getTargetIp();
        int targetPort = needEncrypt ? rule.getTerminalGatewayPort() : rule.getTargetPort();
        String channelKey = relayClient.getAddress().getHostAddress() + ":" + relayClient.getPort()
                + "|" + sourceIp + ":" + relayPacket.getOriginalSrcPort()
                + "->" + targetIp + ":" + targetPort;
        if (assemblyRejected) {
            packetsRejected.incrementAndGet();
            log.warn("[SecurePublish] block relay packet after verify failed: ruleId={}, reason={}",
                    rule.getRuleId(), securePublishReason(assemblyResult));
            return;
        }
        reportRelayPayload(rule, data, sourceIp);
        byte[] bodyData = transcodeService != null
                ? transcodeService.transcode(data, TranscodeService.DataType.TEXT)
                : data;
        List<Message> messages = MessageBuilder.buildForUdp(
                bodyData,
                transcodeEnabled,
                MessageHeader.MSG_TYPE_PASSTHROUGH,
                MessageHeader.ENCODING_NONE);
        Channel outbound = outboundChannels.get(channelKey);
        for (Message msg : messages) {
            byte[] finalData = msg.toBytes();
            if (needEncrypt) {
                byte[] encrypted = cryptoService.encrypt(finalData);
                if (encrypted == null) {
                    log.warn("[ClientRelay] encrypt failed, ruleId={}", rule.getRuleId());
                    continue;
                }
                if (cryptoPacketStore != null) {
                    cryptoPacketStore.saveMessage(
                            rule.getRuleId(), rule.getChainId(), "CLIENT_RELAY_UDP",
                            sourceIp, targetIp, targetPort,
                            msg.getHeader(), finalData, encrypted);
                }
                finalData = encrypted;
            }
            if (outbound == null || !outbound.isActive()) {
                createAndForward(ctx, channelKey, finalData, targetIp, targetPort, relayClient, relayPacket, rule);
                outbound = outboundChannels.get(channelKey);
            } else {
                forward(outbound, finalData, targetIp, targetPort);
            }
        }
    }

    private FileAssemblyResult observeFileAssembly(UdpProxyRule rule,
                                                   RelayPacketCodec.RelayPacket relayPacket,
                                                   byte[] data,
                                                   String sourceIp) {
        if (udpFileAssemblyService == null || rule == null || relayPacket == null || data == null || data.length == 0) {
            return null;
        }
        try {
            FileAssemblyResult result = udpFileAssemblyService.accept(FileAssemblyRequest.builder()
                    .ruleId(rule.getRuleId())
                    .chainId(rule.getChainId())
                    .manufacturer(rule.getManufacturer())
                    .sourceIp(sourceIp)
                    .sourcePort(relayPacket.getOriginalSrcPort())
                    .targetIp(rule.getTargetIp())
                    .targetPort(rule.getTargetPort())
                    .payload(data)
                    .receivedAt(System.currentTimeMillis())
                    .build());
            if (result != null && result.isCompleted() && result.getFile() != null) {
                log.info("【重组文件】ClientRelay入口完成: fileId={}, file={}, size={}B, sha256={}, securePublishPresent={}, ruleId={}",
                        result.getFile().getFileId(), result.getFile().getFileName(),
                        result.getFile().getTotalSize(), result.getFile().getSha256(),
                        result.getFile().getSecurePublishBlockPresent(), rule.getRuleId());
                if (Boolean.FALSE.equals(result.getFile().getSecurePublishAllowed())) {
                    log.warn("[SecurePublish-JPEG] block current ClientRelay packet after failed verify: file={}, reason={}, ruleId={}, source={}:{}",
                            result.getFile().getFileName(), result.getFile().getSecurePublishVerifyReason(),
                            rule.getRuleId(), sourceIp, relayPacket.getOriginalSrcPort());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("【重组文件】ClientRelay旁路处理失败，不影响转发: ruleId={}, error={}",
                    rule.getRuleId(), e.getMessage(), e);
        }
        return null;
    }

    private boolean isSecurePublishRejected(FileAssemblyResult result) {
        return result != null
                && result.isCompleted()
                && result.getFile() != null
                && Boolean.FALSE.equals(result.getFile().getSecurePublishAllowed());
    }

    private boolean isSecurePublishAllowed(FileAssemblyResult result) {
        return result != null
                && result.isCompleted()
                && result.getFile() != null
                && Boolean.TRUE.equals(result.getFile().getSecurePublishAllowed());
    }

    private String securePublishReason(FileAssemblyResult result) {
        if (result == null || result.getFile() == null || result.getFile().getSecurePublishVerifyReason() == null) {
            return "SECURE_PUBLISH_VERIFY_FAILED";
        }
        return result.getFile().getSecurePublishVerifyReason();
    }

    private void reportRelayPayload(UdpProxyRule rule, byte[] data, String sourceIp) {
        if (dataReportService == null || rule == null || data == null || data.length == 0) {
            return;
        }
        dataReportService.reportAsync(rule.getRuleId(), rule.getChainId(), data,
                sourceIp, rule.getManufacturer(), rule.getTargetIp(), rule.getTargetPort());
    }










    private void createAndForward(ChannelHandlerContext ctx, String channelKey,
                                  byte[] data, String targetIp, int targetPort,
                                  InetSocketAddress relayClient,
                                  RelayPacketCodec.RelayPacket relayPacket,
                                  UdpProxyRule rule) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext outboundCtx, DatagramPacket packet) {
                        handleTargetResponse(ctx, packet, relayClient, relayPacket, rule);
                    }
                });
        bootstrap.bind(0).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                outboundChannels.put(channelKey, channel);
                forward(channel, data, targetIp, targetPort);
            } else {
                log.warn("[ClientRelay] create outbound channel failed: {}", future.cause().getMessage());
            }
        });
    }

    private void handleTargetResponse(ChannelHandlerContext inboundCtx,
                                      DatagramPacket packet,
                                      InetSocketAddress relayClient,
                                      RelayPacketCodec.RelayPacket requestPacket,
                                      UdpProxyRule rule) {
        try {
            ByteBuf responseBuf = packet.content();
            int responseLen = responseBuf.readableBytes();
            byte[] responseData = new byte[responseLen];
            responseBuf.readBytes(responseData);

            byte[] finalResponse = responseData;
            if (Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                byte[] decrypted = cryptoService.decrypt(responseData);
                if (decrypted == null) {
                    packetsRejected.incrementAndGet();
                    log.warn("[ClientRelay] decrypt response failed, ruleId={}", rule.getRuleId());
                    return;
                }
                finalResponse = decrypted;
            }

            boolean needEncrypt = Boolean.TRUE.equals(rule.getEncryptEnabled())
                    && rule.getTerminalGatewayIp() != null
                    && rule.getTerminalGatewayPort() != null;
            String targetIp = needEncrypt ? rule.getTerminalGatewayIp() : rule.getTargetIp();
            int targetPort = needEncrypt ? rule.getTerminalGatewayPort() : rule.getTargetPort();

            writeRelayResponse(inboundCtx, relayClient, requestPacket, finalResponse);
            responsesRelayed.incrementAndGet();
            log.debug("[ClientRelay] relayed response to {}:{}, bytes={}",
                    relayClient.getAddress().getHostAddress(), relayClient.getPort(), finalResponse.length);
        } catch (Exception e) {
            packetsRejected.incrementAndGet();
            log.warn("[ClientRelay] response handling failed: {}", e.getMessage());
        }
    }

    private void writeRelayResponse(ChannelHandlerContext inboundCtx,
                                    InetSocketAddress relayClient,
                                    RelayPacketCodec.RelayPacket requestPacket,
                                    byte[] payload) {
        RelayPacketCodec.RelayPacket responsePacket = new RelayPacketCodec.RelayPacket();
        responsePacket.setPid(requestPacket.getPid());
        responsePacket.setProcessName(requestPacket.getProcessName());
        responsePacket.setOriginalSrcIp(requestPacket.getOriginalSrcIp());
        responsePacket.setOriginalSrcPort(requestPacket.getOriginalSrcPort());
        responsePacket.setOriginalDstIp(requestPacket.getOriginalDstIp());
        responsePacket.setOriginalDstPort(requestPacket.getOriginalDstPort());
        responsePacket.setTimestamp(System.currentTimeMillis());
        responsePacket.setPayload(payload);

        byte[] relayBytes = codec.encode(
                responsePacket,
                properties.isVerifySignature(),
                properties.getSignatureSecret());
        inboundCtx.channel().writeAndFlush(new DatagramPacket(
                Unpooled.copiedBuffer(relayBytes), relayClient));
    }

    private void forward(Channel channel, byte[] data, String targetIp, int targetPort) {
        if (channel == null || data == null || targetIp == null) {
            return;
        }
        ByteBuf buf = Unpooled.copiedBuffer(data);
        DatagramPacket packet = new DatagramPacket(buf, new InetSocketAddress(targetIp, targetPort));
        channel.writeAndFlush(packet).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                packetsForwarded.incrementAndGet();
            } else {
                log.warn("[ClientRelay] forward failed: {}", future.cause().getMessage());
            }
        });
    }
}
