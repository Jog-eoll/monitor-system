package com.gateway.device.transport.netty.http;

import com.gateway.device.protocol.api.ParsedHttpResponse;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.transport.netty.NettyTransportConfig;
import com.gateway.device.transport.netty.common.EventLoopResources;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * HTTP 协议传输 —— 池化带 HTTP pipeline 的 Channel，全异步事件驱动。
 *
 * <p>Pipeline: IdleStateHandler → HttpClientCodec → HttpResponseFrameHandler → pool-guard。
 * 请求对象（Netty HttpRequest）直接写入通道，由 HttpClientCodec 编码为线缆字节；
 * 响应由 HttpResponseFrameHandler 聚合为 {@link ParsedHttpResponse} 完成 Future。</p>
 */
@Slf4j
public class HttpTransport implements AutoCloseable {

    private final EventLoopResources resources;
    private final NettyTransportConfig config;
    private final HttpChannelPool channelPool;

    public HttpTransport(EventLoopResources resources, NettyTransportConfig config,
                         HttpChannelPool channelPool) {
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

    // ════════════════════════════════════════════════════
    // 发送
    // ════════════════════════════════════════════════════

    /**
     * 异步 HTTP 发送并等待响应 —— HttpRequest 写入通道，HttpClientCodec 编码。
     *
     * @param httpRequest Codec.encodeRequest() 产出的 Netty HttpRequest 对象
     * @return 传输层已解析的 HTTP 响应组件
     */
    public CompletableFuture<ParsedHttpResponse> sendHttp(DeviceContext device, Object httpRequest, Duration timeout) {
        CompletableFuture<ParsedHttpResponse> future = new CompletableFuture<>();
        int port = resolvePort(device);
        long timeoutMs = timeout.toMillis();
        String deviceKey = HttpChannelPool.deviceKey(device.getVendor().name(), device.getIp(), port);
        EventLoopGroup group = resources.eventLoopGroup();
        ScheduledExecutorService timeoutScheduler = resources.timeoutScheduler();

        group.next().execute(() -> {
            Channel channel;
            try {
                channel = channelPool.getOrCreate(deviceKey, group, config);
            } catch (Exception e) {
                future.completeExceptionally(e);
                return;
            }

            ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
                if (!future.isDone()) {
                    future.completeExceptionally(
                            new TimeoutException("HTTP request timeout " + device.getIp() + ":" + port));
                    HttpResponseFrameHandler.detachState(channel);
                    channelPool.removeAndClose(deviceKey);
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);

            // 将 future + timeout 绑定到 channel attribute，供 HttpResponseFrameHandler 使用
            HttpResponseFrameHandler.attachState(channel, future, timeoutTask);

            channel.writeAndFlush(httpRequest)
                    .addListener((ChannelFuture writeFuture) -> {
                        if (!writeFuture.isSuccess()) {
                            HttpResponseFrameHandler.detachState(channel);
                            channelPool.removeAndClose(deviceKey);
                            timeoutTask.cancel(false);
                            future.completeExceptionally(writeFuture.cause());
                        } else {
                            channelPool.markAlive(deviceKey);
                        }
                    });
        });

        return future;
    }

    @Override
    public void close() {
        channelPool.closeAll();
        log.info("HttpTransport 已关闭");
    }
}
