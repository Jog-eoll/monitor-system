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
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全端口透明 TCP 代理（终端网关版本）
 *
 * <pre>
 * 解决问题：
 *   接收发布网关 CatchAll 代理转发的动态端口流量，解密后连接大屏真实端口。
 *
 * 工作原理：
 *   1. 监听 catch-all 端口（如 19999），使用标准 NIO（无需 TPROXY）
 *   2. 连接建立后先读取 2 字节握手，获取原始目标端口（如 41405）
 *   3. 建立到大屏 targetIp:originalPort 的 TCP 连接
 *   4. 入站数据按 [4B密文长度][密文块] 拆帧 → 解密 → MessageAssembler → 转发到大屏
 *
 * 协议格式（发布网关 → 终端网关）：
 *   连接握手: [2B 大端序原始端口号]
 *   数据帧:   [4B 密文长度][密文块]  （与静态 TcpProxyServer 相同格式）
 *
 * 隔离保证：
 *   - 仅当 dynamicPortProxyEnabled=true 时启动（只有 Nova 设备）
 *   - 使用独立端口，不影响现有 TCP/UDP 代理
 * </pre>
 */
@Slf4j
public class CatchAllTcpProxyServer {

    /**
     * CatchAll 加密开关（默认关闭）
     *
     * ★ 为什么默认关闭：
     *   动态端口传输文件数据时，每个 ~1460B 的 TCP 段都要 encrypt/decrypt，
     *   SM4/AES 每次调用有密码器初始化+块填充的固定开销，数百个包累积后
     *   将文件传输时间从秒级拉长到分钟级，导致屏精灵超时报错。
     *   控制通道 16606 已有 TLS 保护敏感指令，动态端口仅传图片文件数据。
     *
     * ★ 不影响 Sigma：
     *   Sigma 走 TcpProxyServer（静态端口代理），不经过 CatchAll。
     *   CatchAll 仅当 dynamicPortProxyEnabled=true 时启动（只有 Nova 设备）。
     *
     * 如需恢复加密，改为 true 即可（但文件传输速度会显著下降）。
     * 注意：发布网关和终端网关的这个开关必须保持一致。
     */
    private static final boolean CATCHALL_ENCRYPT_ENABLED = true;

    private final UdpProxyRule rule;
    private final CryptoService cryptoService;
    private final MessageAssembler messageAssembler;
    private final int catchAllPort;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    private final Map<String, Channel> outboundChannels = new ConcurrentHashMap<>();

    public CatchAllTcpProxyServer(UdpProxyRule rule, CryptoService cryptoService,
                                   MessageAssembler messageAssembler, int catchAllPort) {
        this.rule = rule;
        this.cryptoService = cryptoService;
        this.messageAssembler = messageAssembler;
        this.catchAllPort = catchAllPort;
    }

    /**
     * 启动 CatchAll 代理
     *
     * 必要性：发布网关通过 TPROXY 捕获 Nova 动态端口流量后，加密转发到本端口。
     * 本代理通过 2 字节握手获取原始端口号，连接大屏对应端口完成文件传输。
     */
    public void start() {
        if (running) {
            log.warn("CatchAll TCP 代理已在运行，规则ID: {}", rule.getRuleId());
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 先读 2 字节握手（端口号），再切换到数据帧解码
                            ch.pipeline().addLast(new HandshakeDecoder());
                        }
                    });

            ChannelFuture future = b.bind(catchAllPort).sync();
            serverChannel = future.channel();
            running = true;

            log.info("==========================================");
            log.info("  CatchAll TCP 代理启动成功（终端网关）");
            log.info("  规则ID: {}", rule.getRuleId());
            log.info("  catch-all 端口: {}", catchAllPort);
            log.info("  转发目标IP: {}", rule.getTargetIp());
            log.info("  解密转发: {}", Boolean.TRUE.equals(rule.getDecryptEnabled()) ? "已启用" : "已禁用");
            log.info("==========================================");

        } catch (Exception e) {
            log.error("CatchAll TCP 代理启动失败，规则ID: {}", rule.getRuleId(), e);
            stop();
        }
    }

    /**
     * 停止 CatchAll 代理
     */
    public void stop() {
        if (!running) return;
        running = false;

        outboundChannels.values().forEach(ch -> {
            if (ch != null && ch.isActive()) {
                try { ch.close().sync(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        outboundChannels.clear();

        if (serverChannel != null) {
            try { serverChannel.close().sync(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
        log.info("  CatchAll TCP 代理已停止（终端网关）");
        log.info("  规则ID: {}", rule.getRuleId());
        log.info("==========================================");
    }

    public boolean isRunning() {
        return running;
    }

    // ── 握手解码器 ─────────────────────────────────────────────

    /**
     * 握手解码器：先读 2 字节获取原始目标端口，然后切换到数据帧模式
     *
     * 技术依据：发布网关 CatchAll 代理在连接建立后首先发送 2 字节大端序端口号，
     * 终端网关据此得知应连接大屏的哪个端口。握手完成后替换为 TcpFrameDecoder + ProxyHandler。
     */
    private class HandshakeDecoder extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 2) {
                return;
            }

            int originalPort = in.readUnsignedShort();
            log.info("【CatchAll终端网关】握手完成 - 原始目标端口: {}", originalPort);

            // 握手完成，替换 pipeline：移除自身，添加代理处理器
            ctx.pipeline().remove(this);

            // 加密模式：添加 [4B长度前缀] 拆帧解码器
            if (CATCHALL_ENCRYPT_ENABLED && Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                ctx.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                        10 * 1024 * 1024, 0, 4, 0, 4));
            }
            // 透明模式（默认）：不添加拆帧器，直接接收原始字节流
            ctx.pipeline().addLast(new CatchAllProxyHandler(originalPort));

            // ★ 关键：必须用 pipeline.fireChannelActive() 而非 ctx.fireChannelActive()
            //
            // 原因：ctx 是已被 remove 的 HandshakeDecoder 的 context，
            // remove() 不会更新 ctx.next 指针，导致 ctx.fireChannelActive()
            // 走到 TailContext（stale pointer），CatchAllProxyHandler.channelActive()
            // 永远不会被调用 → outboundChannel 永远为 null → 所有数据被丢弃。
            //
            // pipeline.fireChannelActive() 从 HeadContext 开始遍历当前最新的 pipeline，
            // 能正确到达新添加的 CatchAllProxyHandler。
            ctx.pipeline().fireChannelActive();
        }
    }

    // ── 代理处理器 ─────────────────────────────────────────────

    /**
     * CatchAll 代理处理器
     *
     * 每条入站连接（发布网关 → 终端网关 catch-all）对应一条出站连接（终端网关 → 大屏 originalPort）。
     * CATCHALL_ENCRYPT_ENABLED=false 时：原始字节直接转发到大屏（透明代理）。
     * CATCHALL_ENCRYPT_ENABLED=true 时：解密 → 转发到大屏，大屏响应 → 加密 → 回传发布网关。
     */
    private class CatchAllProxyHandler extends ChannelInboundHandlerAdapter {

        private final int originalPort;
        private Channel outboundChannel;

        CatchAllProxyHandler(int originalPort) {
            this.originalPort = originalPort;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            log.info("【CatchAll终端网关】建立到大屏的连接 - {}:{}", rule.getTargetIp(), originalPort);

            // 暂停读取
            ctx.channel().config().setAutoRead(false);

            // 连接大屏的原始目标端口
            Bootstrap outBoot = new Bootstrap();
            outBoot.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new InfoBoardResponseHandler(ctx.channel()));
                        }
                    });

            outBoot.connect(rule.getTargetIp(), originalPort)
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            outboundChannel = future.channel();
                            String key = ctx.channel().id().asShortText();
                            outboundChannels.put(key, outboundChannel);
                            log.info("【CatchAll终端网关】已连接大屏 {}:{}", rule.getTargetIp(), originalPort);
                            // 恢复读取
                            ctx.channel().config().setAutoRead(true);
                        } else {
                            log.error("【CatchAll终端网关】连接大屏失败 {}:{}", rule.getTargetIp(), originalPort, future.cause());
                            ctx.close();
                        }
                    });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (outboundChannel == null || !outboundChannel.isActive()) {
                    log.error("【CatchAll终端网关】出站通道不可用 - 端口: {}", originalPort);
                    return;
                }

                if (CATCHALL_ENCRYPT_ENABLED && Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                    // 加密模式：解密后转发到大屏
                    byte[] frameBytes = new byte[buf.readableBytes()];
                    buf.readBytes(frameBytes);
                    byte[] decrypted = cryptoService.decrypt(frameBytes);
                    if (decrypted != null) {
                        outboundChannel.writeAndFlush(Unpooled.copiedBuffer(decrypted));
                    } else {
                        log.warn("【CatchAll终端网关】解密失败，丢弃该帧");
                    }
                } else {
                    // 透明模式：零拷贝直接转发到大屏（默认，文件传输高性能）
                    outboundChannel.writeAndFlush(buf.retain());
                }
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            String key = ctx.channel().id().asShortText();
            Channel ob = outboundChannels.remove(key);
            if (ob != null && ob.isActive()) ob.close();
            if (outboundChannel != null && outboundChannel.isActive()) outboundChannel.close();
            log.info("【CatchAll终端网关】连接断开 - 端口: {}", originalPort);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【CatchAll终端网关】处理异常 - 端口: {}", originalPort, cause);
            ctx.close();
        }
    }

    // ── 大屏响应处理器 ─────────────────────────────────────────────

    /**
     * 接收大屏的响应，回传给发布网关
     *
     * CATCHALL_ENCRYPT_ENABLED=false 时：raw bytes 直接转发（透明模式）。
     * CATCHALL_ENCRYPT_ENABLED=true 时：加密后以 [4B长度][密文] 格式回传。
     */
    private class InfoBoardResponseHandler extends ChannelInboundHandlerAdapter {

        private final Channel inboundChannel;

        InfoBoardResponseHandler(Channel inboundChannel) {
            this.inboundChannel = inboundChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (CATCHALL_ENCRYPT_ENABLED && Boolean.TRUE.equals(rule.getDecryptEnabled())) {
                    // 加密模式：加密后以 [4B长度][密文] 格式回传
                    byte[] responseData = new byte[buf.readableBytes()];
                    buf.readBytes(responseData);
                    byte[] encrypted = cryptoService.encrypt(responseData);
                    if (encrypted != null) {
                        ByteBuf sendBuf = Unpooled.buffer(4 + encrypted.length);
                        sendBuf.writeInt(encrypted.length);
                        sendBuf.writeBytes(encrypted);
                        inboundChannel.writeAndFlush(sendBuf);
                    } else {
                        log.warn("【CatchAll终端网关】响应加密失败，透传原始响应");
                        inboundChannel.writeAndFlush(Unpooled.copiedBuffer(responseData));
                    }
                } else {
                    // 透明模式：零拷贝直接回传
                    if (inboundChannel.isActive()) {
                        inboundChannel.writeAndFlush(buf.retain());
                    }
                }
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (inboundChannel.isActive()) inboundChannel.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("【CatchAll终端网关】大屏响应处理异常", cause);
            ctx.close();
        }
    }
}
