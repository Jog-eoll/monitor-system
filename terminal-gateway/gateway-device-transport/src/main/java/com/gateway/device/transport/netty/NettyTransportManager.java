package com.gateway.device.transport.netty;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.DeviceContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty 统一传输管理器 —— 管理 EventLoopGroup + Channel 池。
 *
 * <p>UDP 设备共享单个 DatagramChannel，TCP/HTTP 设备池化复用 Channel。
 * 全链路异步：发送 → CompletableFuture，响应到达后 complete。</p>
 */
@Slf4j
public class NettyTransportManager implements DeviceTransport, AutoCloseable {

    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "transport-timeout");
                t.setDaemon(true);
                return t;
            });
    private final NettyTransportConfig config;
    private final EventLoopGroup eventLoopGroup;
    private final DeviceChannelPool channelPool;
    @Getter
    private final ConnectionHealthChecker healthChecker;
    private final AtomicInteger requestIdSeq = new AtomicInteger(0);
    private final ConcurrentMap<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> senderToRequestId = new ConcurrentHashMap<>();
    private final ExecutorService responseExecutor;
    private Channel sharedUdpChannel;
    private volatile BroadcastSession activeBroadcast;

    public NettyTransportManager(NettyTransportConfig config, DeviceChannelPool channelPool,
                                 ConnectionHealthChecker healthChecker) {
        this.config = config;
        this.channelPool = channelPool;
        this.healthChecker = healthChecker;
        int workers = config.getWorkerThreads() > 0 ? config.getWorkerThreads()
                : Runtime.getRuntime().availableProcessors() * 2;
        this.eventLoopGroup = new NioEventLoopGroup(workers);

        // 有界线程池，替代 CachedThreadPool
        int core = config.getResponsePoolCore() > 0 ? config.getResponsePoolCore()
                : Runtime.getRuntime().availableProcessors();
        this.responseExecutor = new ThreadPoolExecutor(
                core, config.getResponsePoolMax(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getResponsePoolQueue()),
                r -> {
                    Thread t = new Thread(r, "netty-tcp-response");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());

        // 池层健康上报
        channelPool.setHealthChecker(healthChecker);
        healthChecker.setOfflineListener(channelPool::removeAndClose);
    }

    // ════════════════════════════════════════════════════
    // 工具方法
    // ════════════════════════════════════════════════════

    /**
     * 解析有效端口：设备端口优先，否则按厂商默认
     */
    private static int resolvePort(DeviceContext device) {
        return device.getPort() > 0 ? device.getPort()
                : VendorDefaultPort.getDefaultPort(device.getVendor());
    }

    /**
     * 安全移除 pipeline handler（不抛异常）
     */
    private static void removeHandlerSafely(ChannelPipeline pipeline, String name) {
        try {
            pipeline.remove(name);
        } catch (Exception ignored) {
        }
    }

    /**
     * UDP 共享通道是否可用
     */
    private boolean isUdpChannelActive() {
        return sharedUdpChannel != null && sharedUdpChannel.isActive();
    }

    // ════════════════════════════════════════════════════
    // 初始化
    // ════════════════════════════════════════════════════

    /**
     * 启动 UDP 监听通道（供 UDP 协议设备使用）
     */
    public void startUdp(int localPort) throws InterruptedException {
        if (sharedUdpChannel != null && sharedUdpChannel.isActive()) {
            log.info("UDP transport channel already started, localAddress={}", sharedUdpChannel.localAddress());
            return;
        }
        Bootstrap udpBootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new UdpInboundHandler());

        sharedUdpChannel = udpBootstrap.bind(localPort).sync().channel();
        channelPool.setSharedUdpChannel(sharedUdpChannel);
        log.info("UDP 传输通道已启动，本地端口: {}", localPort);
    }

    // ════════════════════════════════════════════════════
    // 发送 API
    // ════════════════════════════════════════════════════

    /**
     * 异步发送并等待响应。
     */
    public CompletableFuture<byte[]> sendAndReceive(DeviceContext device, byte[] payload, Duration timeout) {
        TransportType type = device.getTransportType();
        if (type == TransportType.HTTP || type == TransportType.TCP) {
            return tcpSendAndReceive(device, payload, timeout);
        }
        return udpSendAndReceive(device, payload, timeout);
    }

    // ── UDP 发送 ──

    private CompletableFuture<byte[]> udpSendAndReceive(DeviceContext device, byte[] payload, Duration timeout) {
        String requestId = nextRequestId(device);
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            future.completeExceptionally(new TimeoutException("request " + requestId + " timeout"));
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        future.whenComplete((r, e) -> {
            timeoutTask.cancel(false);
            pendingRequests.remove(requestId);
            senderToRequestId.values().remove(requestId);
        });
        pendingRequests.put(requestId, future);

        try {
            int port = resolvePort(device);
            InetSocketAddress target = new InetSocketAddress(device.getIp(), port);
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            DatagramPacket packet = new DatagramPacket(buf, target);

            if (!isUdpChannelActive()) {
                future.completeExceptionally(new IllegalStateException("UDP 通道未启动"));
                return future;
            }

            String senderKey = target.getAddress().getHostAddress() + ":" + port;
            senderToRequestId.put(senderKey, requestId);

            sharedUdpChannel.writeAndFlush(packet).addListener((ChannelFuture f) -> {
                if (!f.isSuccess()) {
                    pendingRequests.remove(requestId);
                    senderToRequestId.remove(senderKey, requestId);
                    future.completeExceptionally(f.cause());
                }
            });
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }

        return future;
    }

    // ── TCP/HTTP 发送（池化连接） ──

    private CompletableFuture<byte[]> tcpSendAndReceive(DeviceContext device, byte[] payload, Duration timeout) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        int port = resolvePort(device);
        long timeoutMs = timeout.toMillis();
        String deviceKey = DeviceChannelPool.deviceKey(device.getVendor().name(), device.getIp(), port);

        eventLoopGroup.next().execute(() -> {
            Channel channel;
            try {
                channel = channelPool.getOrCreate(deviceKey, eventLoopGroup, config);
            } catch (Exception e) {
                future.completeExceptionally(e);
                return;
            }

            // 临时挂载 per-request 响应处理器
            TcpResponseHandler handler = new TcpResponseHandler();
            ChannelPipeline pipeline = channel.pipeline();
            // 移除旧的 handler（如果上次请求未正常清理）
            removeHandlerSafely(pipeline, "tcp-handler");
            pipeline.addLast("tcp-handler", handler);

            ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
                if (!future.isDone()) {
                    future.completeExceptionally(
                            new TimeoutException("TCP request timeout " + device.getIp() + ":" + port));
                    handler.forceComplete();
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);

            channel.writeAndFlush(Unpooled.wrappedBuffer(payload))
                    .addListener((ChannelFuture writeFuture) -> {
                        if (!writeFuture.isSuccess()) {
                            channelPool.removeAndClose(deviceKey);
                            future.completeExceptionally(writeFuture.cause());
                            timeoutTask.cancel(false);
                        }
                    });

            // 在独立线程中阻塞等待响应，避免阻塞 event loop
            responseExecutor.execute(() -> {
                try {
                    byte[] response = handler.getResponse(timeoutMs);
                    timeoutTask.cancel(false);
                    removeHandlerSafely(pipeline, "tcp-handler");
                    if (!future.isDone()) {
                        future.complete(response);
                        // 健康上报
                        channelPool.markAlive(deviceKey);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                    removeHandlerSafely(pipeline, "tcp-handler");
                } catch (Exception e) {
                    log.warn("TCP 响应处理异常 deviceKey={}", deviceKey, e);
                    future.completeExceptionally(e);
                    removeHandlerSafely(pipeline, "tcp-handler");
                }
            });
        });

        return future;
    }

    /**
     * TCP 连通性探测 —— 仅通过 TCP 握手验证端口可达，不发送应用层数据。
     *
     * <p>连接成功立即关闭 channel，连接失败或超时返回 false。</p>
     */
    @Override
    public CompletableFuture<Boolean> tcpProbe(DeviceContext device, Duration timeout) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        int port = resolvePort(device);
        long timeoutMs = timeout.toMillis();

        eventLoopGroup.next().execute(() -> {
            ChannelFuture cf = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(timeoutMs, Integer.MAX_VALUE))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 空 pipeline —— 仅检测 TCP 握手
                        }
                    })
                    .connect(device.getIp(), port);

            cf.addListener((ChannelFuture f) -> {
                if (f.isSuccess()) {
                    future.complete(true);
                    f.channel().close();
                } else {
                    future.complete(false);
                }
            });
        });

        timeoutScheduler.schedule(() -> {
            if (!future.isDone()) {
                future.complete(false);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * 仅发送（广播/通知等无需响应场景）。
     */
    public CompletableFuture<Void> sendOnly(DeviceContext device, byte[] payload) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            int port = resolvePort(device);
            InetSocketAddress target = new InetSocketAddress(device.getIp(), port);
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            DatagramPacket packet = new DatagramPacket(buf, target);

            if (!isUdpChannelActive()) {
                future.completeExceptionally(new IllegalStateException("UDP 通道未启动"));
                return future;
            }

            sharedUdpChannel.writeAndFlush(packet).addListener((ChannelFuture f) -> {
                if (f.isSuccess()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(f.cause());
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * 广播发送并收集多设备响应。
     */
    public CompletableFuture<List<BroadcastResponse>> broadcastAndCollect(
            byte[] payload, InetSocketAddress target, Duration timeout) {
        CompletableFuture<List<BroadcastResponse>> future = new CompletableFuture<>();
        BroadcastSession session = new BroadcastSession(future, timeout);
        activeBroadcast = session;

        future.whenComplete((r, e) -> {
            activeBroadcast = null;
            session.timeoutTask.cancel(false);
        });

        try {
            if (!isUdpChannelActive()) {
                future.completeExceptionally(new IllegalStateException("UDP 通道未启动"));
                return future;
            }
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            DatagramPacket packet = new DatagramPacket(buf, target);
            sharedUdpChannel.writeAndFlush(packet);
        } catch (Exception e) {
            activeBroadcast = null;
            session.timeoutTask.cancel(false);
            future.completeExceptionally(e);
        }

        return future;
    }

    private String nextRequestId(DeviceContext device) {
        int seq = requestIdSeq.incrementAndGet() & 0xFFFF;
        return device.getIp() + ":" + resolvePort(device) + ":" + seq;
    }

    // ════════════════════════════════════════════════════
    // 生命周期
    // ════════════════════════════════════════════════════

    @Override
    public void close() {
        log.info("关闭 NettyTransportManager...");
        timeoutScheduler.shutdown();
        responseExecutor.shutdown();
        channelPool.closeAll();
        healthChecker.shutdown();
        eventLoopGroup.shutdownGracefully();
    }

    // ════════════════════════════════════════════════════
    // TCP 响应处理器（内部类）
    // ════════════════════════════════════════════════════

    /**
     * TCP 响应处理器 —— 收集所有 ByteBuf 并计数倒数。
     *
     * <p>作为临时 handler 挂载到池化 Channel 的 pipeline 尾部，
     * 请求完成后移除。</p>
     */
    @ChannelHandler.Sharable
    private static class TcpResponseHandler extends ChannelInboundHandlerAdapter {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                synchronized (buffer) {
                    try {
                        buffer.write(bytes);
                    } catch (Exception ignored) {
                    }
                }
                log.debug("[TCP] channelRead {} bytes (total={})", bytes.length, buffer.size());
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("[TCP] channelInactive (total buffered={} bytes)", buffer.size());
            latch.countDown();
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("[TCP] exceptionCaught: {}", cause.getMessage());
            latch.countDown();
            ctx.fireExceptionCaught(cause);
        }

        byte[] getResponse(long timeoutMs) throws InterruptedException {
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            synchronized (buffer) {
                byte[] data = buffer.toByteArray();
                if (!completed) {
                    log.debug("[TCP] getResponse timeout, partial={} bytes", data.length);
                } else {
                    log.debug("[TCP] getResponse complete, total={} bytes", data.length);
                }
                return data;
            }
        }

        /**
         * 供超时回调强制唤醒等待线程，避免空等满超时
         */
        void forceComplete() {
            latch.countDown();
        }
    }

    // ════════════════════════════════════════════════════
    // 广播响应
    // ════════════════════════════════════════════════════

    @Getter
    @AllArgsConstructor
    public static class BroadcastResponse {
        private final byte[] data;
        private final String senderIp;
        private final int senderPort;
    }

    // ════════════════════════════════════════════════════
    // UDP 入站处理器（内部类）
    // ════════════════════════════════════════════════════

    /**
     * UDP 入站处理器 —— O(1) 索引匹配 pending 请求。
     */
    private class UdpInboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            byte[] data = new byte[packet.content().readableBytes()];
            packet.content().readBytes(data);

            String senderKey = packet.sender().getAddress().getHostAddress()
                    + ":" + packet.sender().getPort();

            String matchedId = senderToRequestId.get(senderKey);
            if (matchedId != null) {
                CompletableFuture<byte[]> future = pendingRequests.remove(matchedId);
                if (future != null) {
                    future.complete(data);
                }
            } else {
                BroadcastSession session = activeBroadcast;
                if (session != null) {
                    session.responses.putIfAbsent(senderKey, data);
                } else {
                    log.debug("收到未匹配的 UDP 响应: sender={} size={}", senderKey, data.length);
                }
            }
        }
    }

    /**
     * 单次广播收集会话
     */
    private class BroadcastSession {
        final ConcurrentHashMap<String, byte[]> responses = new ConcurrentHashMap<>();
        final ScheduledFuture<?> timeoutTask;

        BroadcastSession(CompletableFuture<List<BroadcastResponse>> future, Duration timeout) {
            this.timeoutTask = timeoutScheduler.schedule(() -> {
                activeBroadcast = null;
                List<BroadcastResponse> result = new ArrayList<>();
                responses.forEach((key, data) -> {
                    int colon = key.lastIndexOf(':');
                    String ip = colon > 0 ? key.substring(0, colon) : key;
                    int port;
                    try {
                        port = Integer.parseInt(key.substring(colon + 1));
                    } catch (NumberFormatException e) {
                        port = 0;
                    }
                    result.add(new BroadcastResponse(data, ip, port));
                });
                future.complete(result);
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
