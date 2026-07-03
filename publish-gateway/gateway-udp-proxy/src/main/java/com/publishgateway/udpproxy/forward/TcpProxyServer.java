package com.publishgateway.udpproxy.forward;

import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.entity.message.Message;
import com.publishgateway.udpproxy.entity.message.MessageBuilder;
import com.publishgateway.udpproxy.entity.message.MessageHeader;
import com.publishgateway.udpproxy.service.CryptoPacketStore;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.DataReportService;
import com.publishgateway.udpproxy.service.TranscodeService;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * TCP代理服务器（发布网关版本 - 支持加密转发）
 *
 * 数据流：
 * Sigma --[TCP]--> 本代理(监听端口) --[加密TCP]--> 终端网关 --[TCP]--> 情报板
 */
@Slf4j
public class TcpProxyServer {
    private final UdpProxyRule rule;
    private final CryptoService cryptoService;
    private final CryptoPacketStore cryptoPacketStore;
    private final DataReportService dataReportService;
    /** 转码开关：false=透传模式，true=转码模式 */
    private final boolean transcodeEnabled;
    private final TranscodeService transcodeService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;



    /**
     * 存储每个入站连接对应的出站通道
     * Key: 入站 ChannelId, Value: 出站连接到终端网关/情报板的 Channel
     */
    private final Map<String, Channel> outboundChannels = new ConcurrentHashMap<>();
    
    /**
     * 动态追加的允许IP集合（端口复用时合并其他规则的 sourceIp）
     */
    private final Set<String> extraAllowedIps = ConcurrentHashMap.newKeySet();

    /**
     * 端口复用时，sourceIp -> 对应的规则映射
     * 用于根据发送方IP路由到正确的 ruleId/chainId 进行数据上报和告警
     */
    private final Map<String, UdpProxyRule> ipRuleMapping = new ConcurrentHashMap<>();

    public TcpProxyServer(UdpProxyRule rule, CryptoService cryptoService,
                          CryptoPacketStore cryptoPacketStore,
                          DataReportService dataReportService,
                          boolean transcodeEnabled, TranscodeService transcodeService) {
        this.rule               = rule;
        this.cryptoService      = cryptoService;
        this.cryptoPacketStore  = cryptoPacketStore;
        this.dataReportService  = dataReportService;
        this.transcodeEnabled   = transcodeEnabled;
        this.transcodeService   = transcodeService;
    }

    /**
     * 启动TCP代理服务器
     */
    public void start() {
        if (running) {
            log.warn("TCP代理服务器已在运行，规则ID: {}", rule.getRuleId());
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();


        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, 2048 * 1024)
                    .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new TcpProxyHandler());
                        }
                    });

            InetSocketAddress bindAddress;
            if (rule.getListenIp() != null && !rule.getListenIp().isEmpty()) {
                try {
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
            log.info("  TCP代理服务器启动成功（发布网关）");
            log.info("  规则ID: {}", rule.getRuleId());
            log.info("  规则名称: {}", rule.getRuleName());
            log.info("  监听地址: {}:{} (TCP)", actualListenIp, rule.getListenPort());

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
        } catch (InterruptedException e) {
            log.warn("TCP代理服务器配置错误，规则ID: {}，原因: {}", rule.getRuleId(), e.getMessage());
            stop();
        } catch (Exception e) {
            log.error("TCP代理服务器启动失败，规则ID: {}", rule.getRuleId(), e);
            stop();
        }
    }

    /**
     * 停止TCP代理服务器
     */
    public void stop() {
        if (!running) {
            log.debug("TCP代理服务器未运行，无需停止，规则ID: {}", rule.getRuleId());
            return;
        }

        running = false;
        log.info("正在停止TCP代理服务器，规则ID: {}", rule.getRuleId());

        // 关闭所有出站连接
        for (Channel ch : outboundChannels.values()) {
            if (ch != null) {
                try {
                    ch.close().sync();
                } catch (InterruptedException e) {
                    log.error("关闭出站通道异常", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        outboundChannels.clear();

        if (serverChannel != null) {
            try {
                serverChannel.close().sync();
                log.info("✅ TCP服务器通道已关闭，端口已释放");
            } catch (InterruptedException e) {
                log.error("关闭服务器通道异常", e);
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (workerGroup != null) workerGroup.shutdownGracefully().sync();
            if (bossGroup != null) bossGroup.shutdownGracefully().sync();
            log.info("✅ TCP EventLoopGroup已关闭");
        } catch (InterruptedException e) {
            log.error("关闭EventLoopGroup异常", e);
            Thread.currentThread().interrupt();
        }

        log.info("==========================================");
        log.info("  ✅ TCP代理服务器已完全停止");
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
                log.info("【发布网关TCP】合并规则 IP映射: {} -> ruleId={}, chainId={}",
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
                log.info("【发布网关-TCP】已从服务 [{}] 中移除合并规则 IP 映射: {} -> {}",
                        this.rule.getRuleId(), entry.getKey(), ruleId);
            }
        }
        return removed;
    }

    /**
     * 根据发送方IP解析对应的规则（端口复用时路由到正确链路）
     */
    private UdpProxyRule resolveRule(String senderIp) {
        UdpProxyRule mapped = ipRuleMapping.get(senderIp);
        return mapped != null ? mapped : this.rule;
    }

    /**
     * TCP代理处理器（每个入站连接独立一个实例）
     */
    private class TcpProxyHandler extends ChannelInboundHandlerAdapter {

        /** 对应的出站通道（首包到达时建立） */
        private volatile Channel outboundChannel;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            InetSocketAddress sender = (InetSocketAddress) ctx.channel().remoteAddress();
            String senderIp = sender.getAddress().getHostAddress();

            // TODO: 白名单功能暂时屏蔽，后续需要时取消注释即可恢复
            // if (rule.getSourceIp() != null && !rule.getSourceIp().isEmpty()) {
            //     boolean allowed = extraAllowedIps.contains(senderIp);
            //     if (!allowed) {
            //         String[] allowedIps = rule.getSourceIp().split(",");
            //         for (String ip : allowedIps) {
            //             if (ip.trim().equals(senderIp)) { allowed = true; break; }
            //         }
            //     }
            //     if (!allowed) {
            //         log.warn("【发布网关TCP】拒绝非授权来源 - 规则ID: {}, 来源IP: {}, 授权IP列表: {}",
            //                 rule.getRuleId(), senderIp, rule.getSourceIp());
            //         ctx.close();
            //         return;
            //     }
            // }

            log.info("【发布网关TCP】新连接建立 - 规则ID: {}, 来源: {}", rule.getRuleId(), sender);

            // 确定转发目标
            boolean needEncrypt = Boolean.TRUE.equals(rule.getEncryptEnabled())
                    && rule.getTerminalGatewayIp() != null
                    && rule.getTerminalGatewayPort() != null;
            String targetIp = needEncrypt ? rule.getTerminalGatewayIp() : rule.getTargetIp();
            int targetPort = needEncrypt ? rule.getTerminalGatewayPort() : rule.getTargetPort();

            // 暂停读取，等出站连接建立完再读
            ctx.channel().config().setAutoRead(false);

            // 建立到目标的出站TCP连接
            Bootstrap outBootstrap = new Bootstrap();
            outBootstrap.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 加密模式下添加长度前缀解码器，与终端网关响应帧 [4B长度][密文] 匹配
                            if (needEncrypt) {
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                        10 * 1024 * 1024, 0, 4, 0, 4));
                            }
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx2, Object msg) {
                                    // 收到目标（终端网关/情报板）的响应，解密后转回给源
                                    ByteBuf buf = (ByteBuf) msg;
                                    try {
                                        int len = buf.readableBytes();
                                        byte[] responseData = new byte[len];
                                        buf.readBytes(responseData);

                                        byte[] finalResponse = responseData;
                                        if (Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                                            byte[] decrypted = cryptoService.decrypt(responseData);
                                            if (decrypted != null) {
                                                log.info("【发布网关TCP】响应已解密：{}字节 -> {}字节",
                                                        responseData.length, decrypted.length);
                                                finalResponse = decrypted;
                                            } else {
                                                log.error("【发布网关TCP】响应解密失败，丢弃该响应（禁止透传密文），规则ID: {}",
                                                        rule.getRuleId());
                                                return;
                                            }
                                        }

                                        log.info("【发布网关TCP】收到响应 - {}字节，转发回客户端", finalResponse.length);
                                        ctx.channel().writeAndFlush(Unpooled.copiedBuffer(finalResponse));
                                    } finally {
                                        buf.release();
                                    }
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx2) {
                                    log.info("【发布网关TCP】出站连接断开，延迟关闭入站连接，规则ID: {}", rule.getRuleId());
                                    // 延迟关闭，打断「终端网关断开 → Sigma 立即重连」的级联风暴
                                    ctx.channel().eventLoop().schedule(() -> {
                                        if (ctx.channel().isActive()) ctx.close();
                                    }, 2, TimeUnit.SECONDS);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx2, Throwable cause) {
                                    log.error("【发布网关TCP】出站通道异常，规则ID: {}", rule.getRuleId(), cause);
                                    ctx2.close();
                                }
                            });
                        }
                    });

            outBootstrap.connect(targetIp, targetPort).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    outboundChannel = future.channel();
                    String channelId = ctx.channel().id().asShortText();
                    outboundChannels.put(channelId, outboundChannel);
                    log.info("【发布网关TCP】出站连接建立成功 - 目标: {}:{}", targetIp, targetPort);
                    // 出站连接就绪后，恢复读取入站数据
                    ctx.channel().config().setAutoRead(true);
                } else {
                    log.error("【发布网关TCP】出站连接失败 - 目标: {}:{}", targetIp, targetPort, future.cause());
                    ctx.close();
                }
            });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                InetSocketAddress sender = (InetSocketAddress) ctx.channel().remoteAddress();
                String senderIp = sender.getAddress().getHostAddress();
                UdpProxyRule effectiveRule = resolveRule(senderIp);
                int dataLength = buf.readableBytes();
                byte[] data = new byte[dataLength];
                buf.readBytes(data);

                log.info("==========================================");
                log.info("  【发布网关TCP】收到数据");
                log.info("  来源: {}", sender);
                log.info("  数据长度: {} 字节", dataLength);
                log.info("==========================================");

                // 数据上报（根据发送方IP路由到正确规则）
                if (dataReportService != null) {
                    log.info("【发布网关TCP】数据归属 - 来源IP: {}, 匹配规则: {}, chainId: {}",
                            senderIp, effectiveRule.getRuleId(), effectiveRule.getChainId());
                    dataReportService.reportAsync(effectiveRule.getRuleId(), effectiveRule.getChainId(), data, senderIp, effectiveRule.getManufacturer(), effectiveRule.getTargetIp(), effectiveRule.getTargetPort());
                }

                // ── 转码处理（透传模式下原样返回） ──
                byte[] bodyData = (transcodeService != null)
                        ? transcodeService.transcode(data, TranscodeService.DataType.TEXT)
                        : data;

                // ── 封装 Message（TCP 不分片，填写 totalLength 供终端网关拆帧） ──
                Message message = MessageBuilder.buildForTcp(
                        bodyData, transcodeEnabled,
                        MessageHeader.MSG_TYPE_PASSTHROUGH, MessageHeader.ENCODING_NONE);
                byte[] msgBytes = message.toBytes();

                // ── 加密整个 Message（Header + Body） ──
                byte[] finalData = msgBytes;
                if (Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                    byte[] encrypted = cryptoService.encrypt(msgBytes);
                    if (encrypted != null) {
                        if (cryptoPacketStore != null) {
                            cryptoPacketStore.saveMessage(
                                    effectiveRule.getRuleId(), effectiveRule.getChainId(), "TCP",
                                    senderIp, rule.getTerminalGatewayIp(), rule.getTerminalGatewayPort(),
                                    message.getHeader(), msgBytes, encrypted);
                        }
                        log.info("【发布网关TCP】Message已加密：{}字节 -> {}字节", msgBytes.length, encrypted.length);
                        finalData = encrypted;
                    } else {
                        log.warn("【发布网关TCP】加密失败，丢弃本包，规则ID: {}", rule.getRuleId());
                        return;
                    }
                }

                // 转发到出站通道
                if (outboundChannel != null && outboundChannel.isActive()) {
                    final byte[] sendData = finalData;
                    // 加密模式下使用 [4B长度前缀][密文块] 格式，供终端网关 TcpFrameDecoder 拆帧
                    ByteBuf sendBuf;
                    if (Boolean.TRUE.equals(rule.getEncryptEnabled())) {
                        sendBuf = Unpooled.buffer(4 + sendData.length);
                        sendBuf.writeInt(sendData.length);
                        sendBuf.writeBytes(sendData);
                    } else {
                        sendBuf = Unpooled.copiedBuffer(sendData);
                    }
                    outboundChannel.writeAndFlush(sendBuf)
                            .addListener((ChannelFutureListener) future -> {
                                if (future.isSuccess()) {
                                    log.info("【发布网关TCP】Message已转发 - 原始: {}字节, 发送: {}字节",
                                            dataLength, sendData.length);
                                } else {
                                    log.error("【发布网关TCP】转发失败", future.cause());
                                }
                            });
                } else {
                    log.warn("【发布网关TCP】出站通道不可用，丢弃数据，规则ID: {}", rule.getRuleId());
                }
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            String channelId = ctx.channel().id().asShortText();
            log.info("【发布网关TCP】入站连接断开 - channelId: {}, 规则ID: {}", channelId, rule.getRuleId());
            outboundChannels.remove(channelId);
            if (outboundChannel != null) {
                outboundChannel.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【发布网关TCP】处理异常，规则ID: {}", rule.getRuleId(), cause);
            ctx.close();
        }
    }
}
