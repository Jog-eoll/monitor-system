package com.gateway.device.transport.netty.tcp;

import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.transport.netty.NettyTransportConfig;
import com.gateway.device.transport.netty.common.EventLoopResources;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * TCP 二进制协议传输 —— 池化 NioSocketChannel，CountDownLatch 阻塞等待响应。
 *
 * <p>适用于 JetFileII、NovaStar 等无帧边界的私有二进制协议。
 * HTTP 协议使用独立的 {@code HttpTransport}。</p>
 */
@Slf4j
public class TcpTransport implements AutoCloseable {

    private final EventLoopResources resources;
    private final NettyTransportConfig config;
    private final TcpChannelPool channelPool;

    public TcpTransport(EventLoopResources resources, NettyTransportConfig config,
                        TcpChannelPool channelPool) {
        this.resources = resources;
        this.config = config;
        this.channelPool = channelPool;
    }

    // ════════════════════════════════════════════════════
    // 工具方法
    // ════════════════════════════════════════════════════

    private static int resolvePort(DeviceContext device) {
        return device.getPort() > 0 ? device.getPort()
                : VendorDefaultPort.getDefaultPort(device.getVendor());
    }

    private static void removeHandlerSafely(ChannelPipeline pipeline, String name) {
        try {
            pipeline.remove(name);
        } catch (Exception ignored) {
        }
    }

    // ════════════════════════════════════════════════════
    // 发送
    // ════════════════════════════════════════════════════

    /**
     * 异步发送二进制数据并等待响应。
     */
    public CompletableFuture<byte[]> sendAndReceive(DeviceContext device, byte[] payload, Duration timeout) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        int port = resolvePort(device);
        long timeoutMs = timeout.toMillis();
        String deviceKey = TcpChannelPool.deviceKey(device.getVendor().name(), device.getIp(), port);
        EventLoopGroup group = resources.eventLoopGroup();
        ScheduledExecutorService timeoutScheduler = resources.timeoutScheduler();
        ExecutorService responseExecutor = resources.responseExecutor();

        group.next().execute(() -> {
            Channel channel;
            try {
                channel = channelPool.getOrCreate(deviceKey, group, config);
            } catch (Exception e) {
                future.completeExceptionally(e);
                return;
            }

            TcpResponseHandler handler = new TcpResponseHandler();
            ChannelPipeline pipeline = channel.pipeline();
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

            responseExecutor.execute(() -> {
                try {
                    byte[] response = handler.getResponse(timeoutMs);
                    timeoutTask.cancel(false);
                    removeHandlerSafely(pipeline, "tcp-handler");
                    if (!future.isDone()) {
                        future.complete(response);
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
     * TCP 连通性探测 —— 仅通过 TCP 握手验证端口可达。
     */
    public CompletableFuture<Boolean> tcpProbe(DeviceContext device, Duration timeout) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        int port = resolvePort(device);
        long timeoutMs = timeout.toMillis();
        EventLoopGroup group = resources.eventLoopGroup();
        ScheduledExecutorService timeoutScheduler = resources.timeoutScheduler();

        group.next().execute(() -> {
            ChannelFuture cf = new Bootstrap()
                    .group(group)
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

    @Override
    public void close() {
        channelPool.closeAll();
        log.info("TcpTransport 已关闭");
    }
}
