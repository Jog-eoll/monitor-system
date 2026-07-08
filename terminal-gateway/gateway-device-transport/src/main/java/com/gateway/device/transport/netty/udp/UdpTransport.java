package com.gateway.device.transport.netty.udp;

import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.transport.netty.NettyTransportConfig;
import com.gateway.device.transport.netty.NettyTransportManager.BroadcastResponse;
import com.gateway.device.transport.netty.common.EventLoopResources;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP 数据报传输 —— 共享单个 DatagramChannel，支持单播/广播/仅发送。
 *
 * <p>所有 UDP 设备共享同一个 NioDatagramChannel，通过 senderIp:port 匹配响应。</p>
 */
@Slf4j
public class UdpTransport implements AutoCloseable {

    private final EventLoopResources resources;
    private final NettyTransportConfig config;
    private final AtomicInteger requestIdSeq = new AtomicInteger(0);
    private final ConcurrentMap<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> senderToRequestId = new ConcurrentHashMap<>();

    @Getter
    private Channel sharedUdpChannel;
    private volatile BroadcastSession activeBroadcast;
    private volatile boolean started;

    public UdpTransport(EventLoopResources resources, NettyTransportConfig config) {
        this.resources = resources;
        this.config = config;
    }

    // ════════════════════════════════════════════════════
    // 工具方法
    // ════════════════════════════════════════════════════

    private static int resolvePort(DeviceContext device) {
        return device.getPort() > 0 ? device.getPort()
                : VendorDefaultPort.getDefaultPort(device.getVendor());
    }

    private boolean isUdpChannelActive() {
        return sharedUdpChannel != null && sharedUdpChannel.isActive();
    }

    private String nextRequestId(DeviceContext device) {
        int seq = requestIdSeq.incrementAndGet() & 0xFFFF;
        return device.getIp() + ":" + resolvePort(device) + ":" + seq;
    }

    // ════════════════════════════════════════════════════
    // 初始化
    // ════════════════════════════════════════════════════

    /**
     * 启动 UDP 监听通道（供 UDP 协议设备使用）。
     */
    public void startUdp(int localPort) throws InterruptedException {
        if (started) {
            log.warn("UDP 通道已启动，跳过重复启动");
            return;
        }

        UdpInboundHandler handler = new UdpInboundHandler(
                senderToRequestId, pendingRequests, () -> activeBroadcast);

        Bootstrap udpBootstrap = new Bootstrap()
                .group(resources.eventLoopGroup())
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(handler);

        sharedUdpChannel = udpBootstrap.bind(localPort).sync().channel();
        started = true;
        log.info("UDP 传输通道已启动，本地端口: {}", localPort);
    }

    // ════════════════════════════════════════════════════
    // 发送
    // ════════════════════════════════════════════════════

    /**
     * 异步 UDP 单播发送并等待响应。
     */
    public CompletableFuture<byte[]> sendAndReceive(DeviceContext device, byte[] payload, Duration timeout) {
        String requestId = nextRequestId(device);
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ScheduledExecutorService scheduler = resources.timeoutScheduler();

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
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
        BroadcastSession session = new BroadcastSession(future, timeout, resources.timeoutScheduler());
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

    @Override
    public void close() {
        if (sharedUdpChannel != null && sharedUdpChannel.isOpen()) {
            sharedUdpChannel.close();
        }
        log.info("UdpTransport 已关闭");
    }
}
