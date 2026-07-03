package com.gateway.udpproxy.forward;

import com.gateway.common.service.CryptoService;
import com.gateway.udpproxy.entity.UdpProxyRule;
import com.gateway.udpproxy.entity.message.MessageAssembler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 终端网关 - TCP代理服务器（支持解密转发）
 *
 * 数据流：
 * 发布网关 --[加密TCP]--> 本代理 --[解密TCP]--> 情报板
 *
 * 拆帧策略：
 *   TCP为流式协议，每帧格式：[20字节固定Header][Body]
 *   Header第17~20字节（偏移16）存储 totalLength（int，大端），
 *   totalLength = Header(20) + Body长度。
 *   使用自定义 {@link TcpFrameDecoder} 按 totalLength 拆帧。
 */
@Slf4j
public class TcpProxyServer {

    /** 自定义消息头长度（字节） */
    private static final int HEADER_LENGTH = 20;
    /** totalLength 字段在 Header 中的偏移量（字节），对应 MessageHeader 第5个int字段 */
    private static final int TOTAL_LENGTH_OFFSET = 16;

    private final UdpProxyRule rule;
    private final CryptoService cryptoService;
    private final MessageAssembler messageAssembler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    /** 每条入站连接对应一个到情报板的出站 TCP Channel */
    private final Map<String, Channel> outboundChannels = new ConcurrentHashMap<>();

    // ── 熔断器（Circuit Breaker）────────────────────────────────────
    /** 连续失败次数阈值：超过后触发熔断 */
    private static final int FAILURE_THRESHOLD = 5;
    /** 熔断持续时间（ms）：熔断期间拒绝新入站连接 */
    private static final long CIRCUIT_BREAK_DURATION_MS = 15_000L;
    /** 级联关闭延迟（ms）：下游断开后延迟关闭入站，打断重连风暴 */
    private static final long CASCADE_CLOSE_DELAY_MS = 2_000L;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 熔断截止时间戳（ms）；0 表示未熔断 */
    private volatile long circuitBreakUntil = 0L;

    /** 检查熔断器是否开启（open = 拒绝新连接） */
    private boolean isCircuitOpen() {
        if (circuitBreakUntil == 0) return false;
        if (System.currentTimeMillis() < circuitBreakUntil) return true;
        // 熔断超时，进入 HALF-OPEN，重置状态允许一次探测
        circuitBreakUntil = 0L;
        return false;
    }

    /** 记录一次下游失败，达到阈值时触发熔断 */
    private void recordOutboundFailure() {
        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= FAILURE_THRESHOLD) {
            circuitBreakUntil = System.currentTimeMillis() + CIRCUIT_BREAK_DURATION_MS;
            consecutiveFailures.set(0);
            log.warn("【TCP终端网关-熔断】情报板连续失败 {} 次，熔断开启 {} 秒，规则ID: {}",
                    fails, CIRCUIT_BREAK_DURATION_MS / 1000, rule.getRuleId());
        }
    }

    /** 下游连接成功时重置失败计数 */
    private void resetFailureCount() {
        consecutiveFailures.set(0);
    }

    public TcpProxyServer(UdpProxyRule rule, CryptoService cryptoService,
                          MessageAssembler messageAssembler) {
        this.rule             = rule;
        this.cryptoService    = cryptoService;
        this.messageAssembler = messageAssembler;
    }

    // ── 生命周期 ────────────────────────────────────────────────────

    public void start() {
        if (running) {
            log.warn("TCP代理服务器已在运行，规则ID: {}", rule.getRuleId());
            return;
        }

        bossGroup  = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     // 加密模式使用 [4B长度前缀][密文块] 拆帧，非加密模式使用自定义 20B Header 拆帧
                     ch.pipeline()
                       .addLast(new TcpFrameDecoder(Boolean.TRUE.equals(rule.getDecryptEnabled())))
                       .addLast(new TcpProxyHandler());
                 }
             });

            InetSocketAddress bindAddr = new InetSocketAddress(rule.getListenPort());
            ChannelFuture future = b.bind(bindAddr).sync();
            serverChannel = future.channel();
            running = true;

            log.info("==========================================");
            log.info("  TCP代理服务器启动成功（终端网关）");
            log.info("  规则ID: {}", rule.getRuleId());
            log.info("  监听地址: :{} (TCP)", rule.getListenPort());
            log.info("  解密转发: {}", Boolean.TRUE.equals(rule.getDecryptEnabled()) ? "已启用" : "已禁用");
            log.info("  转发目标: {}:{} (情报板)", rule.getTargetIp(), rule.getTargetPort());
            log.info("==========================================");

        } catch (Exception e) {
            log.error("TCP代理服务器启动失败，规则ID: {}", rule.getRuleId(), e);
            stop();
        }
    }

    public void stop() {
        if (!running) {
            log.debug("TCP代理服务器未运行，无需停止，规则ID: {}", rule.getRuleId());
            return;
        }
        running = false;
        log.info("正在停止TCP代理服务器，规则ID: {}", rule.getRuleId());

        // 关闭所有出站连接
        outboundChannels.values().forEach(ch -> {
            if (ch != null && ch.isActive()) {
                try { ch.close().sync(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        outboundChannels.clear();

        // 关闭服务器监听通道
        if (serverChannel != null) {
            try {
                serverChannel.close().sync();
                log.info("✅ TCP服务器通道已关闭，端口已释放");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 关闭线程组
        if (workerGroup != null) {
            try { workerGroup.shutdownGracefully().sync(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (bossGroup != null) {
            try { bossGroup.shutdownGracefully().sync(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

    // ── 拆帧解码器 ────────────────────────────────────────────────────

    /**
     * 自定义TCP拆帧解码器（支持加密/非加密双模式）
     *
     * <pre>
     * 非加密模式（Sigma等明文转发场景）：
     *   帧格式：[Header(20B)][Body(N B)]
     *   Header偏移16处：totalLength(int, big-endian) = 20 + N
     *
     * 加密模式（诺瓦等 TLS 加密转发场景）：
     *   帧格式：[4B密文长度(int, big-endian)][密文块(M B)]
     *   发布网关在加密后会附加 4 字节明文长度前缀，终端网关据此拆帧后再解密。
     *   原因：加密后自定义 Header 被打乱，偏移16处不再是有效的 totalLength。
     * </pre>
     */
    private static class TcpFrameDecoder extends ByteToMessageDecoder {

        private final boolean encryptedMode;

        TcpFrameDecoder(boolean encryptedMode) {
            this.encryptedMode = encryptedMode;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (encryptedMode) {
                decodeEncrypted(ctx, in, out);
            } else {
                decodePlainMessage(ctx, in, out);
            }
        }

        /**
         * 加密模式拆帧：[4字节大端序密文长度][密文块]
         */
        private void decodeEncrypted(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (true) {
                if (in.readableBytes() < 4) {
                    return;
                }
                in.markReaderIndex();
                int encryptedLength = in.readInt();

                // 防御异常长度
                if (encryptedLength <= 0 || encryptedLength > 10 * 1024 * 1024) {
                    log.warn("【TcpFrameDecoder-加密】密文长度异常: {}，关闭连接", encryptedLength);
                    ctx.close();
                    return;
                }

                // 数据不够一帧，回退等待
                if (in.readableBytes() < encryptedLength) {
                    in.resetReaderIndex();
                    return;
                }

                byte[] frame = new byte[encryptedLength];
                in.readBytes(frame);
                out.add(Unpooled.wrappedBuffer(frame));
            }
        }

        /**
         * 非加密模式拆帧：自定义 20 字节 Header，偏移 16 处为 totalLength
         */
        private void decodePlainMessage(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (true) {
                if (in.readableBytes() < HEADER_LENGTH) {
                    return;
                }
                in.markReaderIndex();
                int totalLength = in.getInt(in.readerIndex() + TOTAL_LENGTH_OFFSET);

                if (totalLength < HEADER_LENGTH) {
                    log.warn("【TcpFrameDecoder】totalLength异常: {}，关闭连接", totalLength);
                    ctx.close();
                    return;
                }

                if (in.readableBytes() < totalLength) {
                    in.resetReaderIndex();
                    return;
                }

                byte[] frame = new byte[totalLength];
                in.readBytes(frame);
                out.add(Unpooled.wrappedBuffer(frame));
            }
        }
    }

    // ── 代理处理器 ────────────────────────────────────────────────────

    /**
     * TCP代理业务处理器
     *
     * 每条入站连接（发布网关 → 终端网关）对应一条出站连接（终端网关 → 情报板）。
     * 连接建立时创建出站TCP连接，连接关闭时同步关闭出站连接。
     */
    private class TcpProxyHandler extends ChannelInboundHandlerAdapter {

        private Channel outboundChannel;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            String clientKey = remote.getAddress().getHostAddress() + ":" + remote.getPort();

            // 检查源IP白名单
            if (rule.getSourceIp() != null && !rule.getSourceIp().isEmpty()) {
                String sourceIp = remote.getAddress().getHostAddress();
                if (!rule.getSourceIp().equals(sourceIp)) {
                    log.warn("【TCP终端网关】拒绝连接 - 来源IP {} 不在白名单中", sourceIp);
                    ctx.close();
                    return;
                }
            }

            log.info("【TCP终端网关】收到来自发布网关的连接: {}", clientKey);

            // 熔断检查：情报板连续失败次数超阈值时，延迟关闭入站，拒绝新请求
            if (isCircuitOpen()) {
                log.warn("【TCP终端网关-熔断】熔断器开启，拒绝新入站连接，规则ID: {}", rule.getRuleId());
                ctx.channel().eventLoop().schedule(
                        () -> ctx.close(), CASCADE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
                return;
            }

            // 建立到情报板的出站 TCP 连接
            Bootstrap outBoot = new Bootstrap();
            outBoot.group(ctx.channel().eventLoop())
                   .channel(NioSocketChannel.class)
                   .option(ChannelOption.TCP_NODELAY, true)
                   .handler(new ChannelInitializer<SocketChannel>() {
                       @Override
                       protected void initChannel(SocketChannel ch) {
                           ch.pipeline().addLast(new InfoBoardResponseHandler(ctx.channel()));
                       }
                   });

            outBoot.connect(rule.getTargetIp(), rule.getTargetPort())
                   .addListener((ChannelFutureListener) future -> {
                       if (future.isSuccess()) {
                           outboundChannel = future.channel();
                           outboundChannels.put(clientKey, outboundChannel);
                           resetFailureCount(); // 连接成功，重置熔断计数
                           log.info("【TCP终端网关】已连接情报板: {}:{}", rule.getTargetIp(), rule.getTargetPort());
                           // 激活入站通道读取
                           ctx.channel().read();
                       } else {
                           log.error("【TCP终端网关】连接情报板失败: {}:{}", rule.getTargetIp(), rule.getTargetPort(), future.cause());
                           recordOutboundFailure(); // 记录失败，达阈值时触发熔断
                           // 延迟关闭入站，避免立即关闭引发级联重连风暴
                           ctx.channel().eventLoop().schedule(
                                   () -> ctx.close(), CASCADE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
                       }
                   });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf frameBuf = (ByteBuf) msg;
            try {
                int frameLen = frameBuf.readableBytes();
                byte[] frameBytes = new byte[frameLen];
                frameBuf.readBytes(frameBytes);

                log.info("【TCP终端网关】收到一帧数据，长度: {} 字节", frameLen);

                // 对这一帧进行解密+重组，得到最终转发给情报板的数据
                final byte[] dataToForward = processFrame(frameBytes);
                if (dataToForward == null) {
                    // 解密失败或分片未收齐，不转发
                    return;
                }

                // 转发到情报板
                if (outboundChannel != null && outboundChannel.isActive()) {
                    ByteBuf outBuf = Unpooled.copiedBuffer(dataToForward);
                    outboundChannel.writeAndFlush(outBuf).addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            log.info("【TCP终端网关】数据已转发到情报板 {}:{}, 字节数: {}",
                                    rule.getTargetIp(), rule.getTargetPort(), dataToForward.length);
                        } else {
                            log.error("【TCP终端网关】转发到情报板失败", f.cause());
                        }
                    });
                } else {
                    log.error("【TCP终端网关】出站通道不可用，无法转发数据");
                }
            } finally {
                frameBuf.release();
            }
        }

        /**
         * 对单帧进行解密 + MessageAssembler 处理
         *
         * @return 可直接转发给情报板的数据；解密失败或分片未收齐时返回 null
         */
        private byte[] processFrame(byte[] frameBytes) {
            if (!Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                return frameBytes;
            }
            byte[] decrypted = cryptoService.decrypt(frameBytes);
            if (decrypted == null) {
                log.warn("【TCP终端网关】解密失败，丢弃该帧");
                return null;
            }
            log.info("【TCP终端网关】数据已解密：{}字节 -> {}字节", frameBytes.length, decrypted.length);
            byte[] bodyForBoard = messageAssembler.process(decrypted);
            if (bodyForBoard == null) {
                log.debug("【TCP终端网关】分片未收齐，等待后续分片...");
                return null;
            }
            return bodyForBoard;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            if (remote != null) {
                String clientKey = remote.getAddress().getHostAddress() + ":" + remote.getPort();
                Channel ob = outboundChannels.remove(clientKey);
                if (ob != null && ob.isActive()) {
                    ob.close();
                }
            }
            if (outboundChannel != null && outboundChannel.isActive()) {
                outboundChannel.close();
            }
            log.info("【TCP终端网关】发布网关连接断开: {}", remote);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【TCP终端网关】处理异常，规则ID: {}", rule.getRuleId(), cause);
            ctx.close();
        }
    }

    // ── 情报板响应处理器 ────────────────────────────────────────────────

    /**
     * 接收情报板的 TCP 响应，加密后回传给发布网关
     */
    private class InfoBoardResponseHandler extends ChannelInboundHandlerAdapter {

        private final Channel inboundChannel; // 发布网关方向的入站通道

        InfoBoardResponseHandler(Channel inboundChannel) {
            this.inboundChannel = inboundChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                byte[] responseData = new byte[buf.readableBytes()];
                buf.readBytes(responseData);

                // 情报板响应回传给发布网关时加密（反向链路保持对称加密）
                if (Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                    byte[] encrypted = cryptoService.encrypt(responseData);
                    if (encrypted != null) {
                        log.info("【TCP终端网关】响应已加密：{}字节 -> {}字节",
                                responseData.length, encrypted.length);
                        // 加密模式使用 [4B长度前缀][密文块] 格式，与发布网关拆帧协议匹配
                        ByteBuf sendBuf = Unpooled.buffer(4 + encrypted.length);
                        sendBuf.writeInt(encrypted.length);
                        sendBuf.writeBytes(encrypted);
                        log.info("【TCP终端网关】收到情报板响应，加密后回传给发布网关，长度: {} 字节", encrypted.length);
                        inboundChannel.writeAndFlush(sendBuf);
                        return;
                    } else {
                        log.warn("【TCP终端网关】响应加密失败，透传原始响应");
                    }
                }

                log.info("【TCP终端网关】收到情报板响应，长度: {} 字节，回传给发布网关", responseData.length);
                ByteBuf outBuf = Unpooled.copiedBuffer(responseData);
                inboundChannel.writeAndFlush(outBuf);
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("【TCP终端网关】情报板连接断开");
            recordOutboundFailure(); // 情报板主动断开也计入失败
            if (inboundChannel.isActive()) {
                // 延迟关闭入站，打断「情报板断开 → 发布网关断开 → Sigma 立即重连」的级联风暴
                inboundChannel.eventLoop().schedule(() -> {
                    if (inboundChannel.isActive()) inboundChannel.close();
                }, CASCADE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【TCP终端网关】情报板响应处理异常", cause);
            ctx.close();
        }
    }
}
