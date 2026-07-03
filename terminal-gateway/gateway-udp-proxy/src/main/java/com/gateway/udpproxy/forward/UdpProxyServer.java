package com.gateway.udpproxy.forward;


import com.gateway.common.service.CryptoService;
import com.gateway.udpproxy.entity.UdpProxyRule;
import com.gateway.udpproxy.entity.message.MessageAssembler;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端网关 - UDP代理服务器（支持解密转发）
 *
 * 数据流：
 * 发布网关 --[加密UDP]--> 本代理 --[解密UDP]--> 情报板
 */
@Slf4j
public class UdpProxyServer {

    private final UdpProxyRule rule;
    private final CryptoService cryptoService;
    /** Message 重组器：解析 Header + 重组分片 + 解码，返回情报板所需的干净数据 */
    private final MessageAssembler messageAssembler;
    private EventLoopGroup group;
    private Channel serverChannel;
    private volatile boolean running = false;
    private static final int MAX_UDP_DATAGRAM_BYTES = 65_535;

    private final Map<String, Channel> clientChannels = new ConcurrentHashMap<>();

    public UdpProxyServer(UdpProxyRule rule, CryptoService cryptoService,
                          MessageAssembler messageAssembler) {
        this.rule             = rule;
        this.cryptoService    = cryptoService;
        this.messageAssembler = messageAssembler;
    }

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
                    // 不设置 SO_REUSEADDR，防止多规则静默绑定同一端口导致路由混乱
                    .handler(new UdpProxyHandler());

            InetSocketAddress bindAddress;
            if (rule.getListenIp() != null && !rule.getListenIp().isEmpty()) {
                bindAddress = new InetSocketAddress(rule.getListenIp(), rule.getListenPort());
            } else {
                bindAddress = new InetSocketAddress(rule.getListenPort());
            }

            ChannelFuture future = bootstrap.bind(bindAddress).sync();
            serverChannel = future.channel();
            running = true;

            String actualListenIp = ((InetSocketAddress) serverChannel.localAddress())
                    .getAddress().getHostAddress();
            log.info("==========================================");
            log.info("  UDP代理服务器启动成功（终端网关）");
            log.info("  规则ID: {}", rule.getRuleId());
            log.info("  规则名称: {}", rule.getRuleName());
            log.info("  监听地址: {}:{} (UDP)", actualListenIp, rule.getListenPort());
            log.info("  解密转发: {}", Boolean.TRUE.equals(rule.getDecryptEnabled()) ? "已启用" : "已禁用");
            log.info("  转发目标: {}:{} (情报板)", rule.getTargetIp(), rule.getTargetPort());
            if (rule.getSourceIp() != null && !rule.getSourceIp().isEmpty()) {
                log.info("  源IP白名单: {} (发布网关)", rule.getSourceIp());
            }
            log.info("==========================================");

        } catch (Exception e) {
            log.error("UDP代理服务器启动失败，规则ID: {}", rule.getRuleId(), e);
            stop();
        }


    }

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
     * UDP代理处理器
     */
    private class UdpProxyHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            InetSocketAddress sender = packet.sender();
            String senderKey = sender.getAddress().getHostAddress() + ":" + sender.getPort();
            ByteBuf content = packet.content();
            int dataLength = content.readableBytes();
            String sourceIp = sender.getAddress().getHostAddress();

            log.info("==========================================");
            log.info("  【终端网关】收到数据包");
            log.info("  来源: {}", sender);
            log.info("  数据长度: {} 字节", dataLength);
            log.info("==========================================");

            // 检查源IP白名单
            if (hasText(rule.getSourceIp())) {
                if (!isAllowedSource(sourceIp)) {
                    log.warn("拒绝UDP包 - 来源IP {} 不在白名单 [{}] 中，规则ID: {}",
                            sourceIp, rule.getSourceIp(), rule.getRuleId());
                    return;
                }
                log.info("【终端网关】来源校验通过: {} 匹配发布网关白名单 [{}]",
                        sourceIp, rule.getSourceIp());
            } else {
                log.debug("【终端网关】未配置来源白名单，允许来源: {}", sourceIp);
            }

            // 复制数据
            byte[] data = new byte[dataLength];
            content.readBytes(data);

            // finalData: 最终转发给情报板的数据（解密+重组+解码后的干净Body）
            byte[] finalData = data;

            // 解密：将发布网关加密的 Message（Header+Body）解密
            if (Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                byte[] decrypted = cryptoService.decrypt(data);
                if (decrypted != null) {
                    log.info("【终端网关】数据已解密：{}字节 -> {}字节", data.length, decrypted.length);
                    // ── MessageAssembler: 解析Header → 重组分片 → 解码 → 返回情报板可直接使用的数据 ──
                    if (cryptoService.isSvacMode()) {
                        finalData = decrypted;
                    } else {
                    byte[] bodyForBoard = messageAssembler.process(decrypted);
                    if (bodyForBoard == null) {
                        // 分片未收齐，等待后续分片
                        log.debug("【终端网关】分片未收齐，等待后续分片...");
                        return;
                    }
                    finalData = bodyForBoard;
                    }
                } else {
                    log.warn("【终端网关】解密失败，丢弃数据包（来源: {}）", sender);
                    return;
                }
            } else {
                log.debug("【终端网关】规则未启用解密，直接转发原始数据");
            }

            // 获取或创建到情报板的UDP通道
            Channel outboundChannel = clientChannels.get(senderKey);
            if (outboundChannel == null || !outboundChannel.isActive()) {
                createAndForward(ctx, sender, senderKey, finalData, dataLength);
            } else {
                forwardToInfoBoard(outboundChannel, finalData, dataLength);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【终端网关】处理异常，规则ID: {}", rule.getRuleId(), cause);
        }
    }

    private boolean isAllowedSource(String sourceIp) {
        if (!hasText(sourceIp) || !hasText(rule.getSourceIp())) {
            return false;
        }
        for (String allowed : rule.getSourceIp().split(",")) {
            if (sourceIp.equals(allowed.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 创建新通道并转发到情报板
     */
    private void createAndForward(ChannelHandlerContext ctx, InetSocketAddress sender,
                                  String senderKey, byte[] data, int originalLength) {
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.RCVBUF_ALLOCATOR,
                        new FixedRecvByteBufAllocator(MAX_UDP_DATAGRAM_BYTES))
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx2, DatagramPacket responsePacket) {
                        // 接收情报板的响应
                        ByteBuf responseBuf = responsePacket.content();
                        int responseLen = responseBuf.readableBytes();
                        byte[] responseData = new byte[responseLen];
                        responseBuf.readBytes(responseData);

                        // 情报板响应回传给发布网关时加密（反向链路保持对称加密）
                        byte[] finalResponse = responseData;
                        if (Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                            byte[] encrypted = cryptoService.encrypt(responseData);
                            if (encrypted != null) {
                                log.info("【终端网关】响应已加密：{}字节 -> {}字节",
                                        responseData.length, encrypted.length);
                                finalResponse = encrypted;
                            } else {
                                log.warn("【终端网关】响应加密失败，透传原始响应");
                            }
                        }

                        log.info("【终端网关】收到情报板响应 - 长度: {} 字节，转发回: {}",
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
                log.info("【终端网关】为发布网关 {} 创建新的出站通道", senderKey);

                forwardToInfoBoard(newChannel, data, originalLength);
            } else {
                log.error("【终端网关】创建出站通道失败", bindFuture.cause());
            }
        });
    }

    /**
     * 转发到情报板
     */
    private void forwardToInfoBoard(Channel channel, byte[] data, int originalLength) {
        InetSocketAddress targetAddress = new InetSocketAddress(
                rule.getTargetIp(), rule.getTargetPort());
        ByteBuf buf = Unpooled.copiedBuffer(data);
        DatagramPacket outPacket = new DatagramPacket(buf, targetAddress);

        channel.writeAndFlush(outPacket).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("【终端网关】数据已转发到情报板 - 目标: {}:{}, 原始字节: {}, 发送字节: {}",
                        rule.getTargetIp(), rule.getTargetPort(), originalLength, data.length);
            } else {
                log.error("【终端网关】转发失败", future.cause());
            }
        });
    }
}


