package com.gateway.device.transport.netty.http;

import com.gateway.device.transport.netty.ConnectionHealthChecker;
import com.gateway.device.transport.netty.NettyTransportConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 协议 Channel 连接池 —— deviceKey → Channel 映射。
 *
 * <p>Pipeline: IdleStateHandler → HttpClientCodec → HttpResponseFrameHandler → pool-guard。
 * HttpClientCodec 提供 HTTP 响应帧解析，HttpResponseFrameHandler 手动聚合
 * HttpContent 分块（无大小限制），LastHttpContent 到达时手动拼接原始字节完成 Future。</p>
 */
@Slf4j
public class HttpChannelPool {

    private static final AttributeKey<String> DEVICE_KEY_ATTR = AttributeKey.valueOf("httpDeviceKey");

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final HttpResponseFrameHandler frameHandler = new HttpResponseFrameHandler();

    @Setter
    private ConnectionHealthChecker healthChecker;

    // ════════════════════════════════════════════════════
    // Key 生成
    // ════════════════════════════════════════════════════

    public static String deviceKey(String vendor, String ip, int port) {
        return vendor + ":" + ip + ":" + port;
    }

    private static String parseHost(String deviceKey) {
        int first = deviceKey.indexOf(':');
        int last = deviceKey.lastIndexOf(':');
        return deviceKey.substring(first + 1, last);
    }

    private static int parsePort(String deviceKey) {
        int last = deviceKey.lastIndexOf(':');
        return Integer.parseInt(deviceKey.substring(last + 1));
    }

    // ════════════════════════════════════════════════════
    // 连接池操作
    // ════════════════════════════════════════════════════

    /**
     * 获取或创建 HTTP Channel，复用 deviceKey 维度的活跃连接。
     */
    public Channel getOrCreate(String deviceKey, EventLoopGroup group, NettyTransportConfig config) throws Exception {
        // 快路径
        Channel existing = channels.get(deviceKey);
        if (existing != null && existing.isActive()) {
            log.debug("复用 HTTP 池化 Channel: {}", deviceKey);
            return existing;
        }

        // 慢路径
        if (existing != null) {
            channels.remove(deviceKey, existing);
            existing.close();
        }

        long connectTimeoutMs = config.getConnectTimeoutMs();
        long idleTimeoutSec = config.getChannelIdleTimeoutSec();

        ChannelFuture cf = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeoutMs)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.attr(DEVICE_KEY_ATTR).set(deviceKey);
                        // 读空闲检测
                        ch.pipeline().addLast("idle",
                                new IdleStateHandler(idleTimeoutSec, 0, 0, TimeUnit.SECONDS));
                        // HTTP 客户端编解码器（inbound 解析响应帧，outbound 透传 byte[]）
                        ch.pipeline().addLast("http-codec", new HttpClientCodec());
                        // 手动聚合 HttpContent 分块 → 拼接原始字节 → 完成 Future
                        ch.pipeline().addLast("http-frame", frameHandler);
                        // 空闲/断开 → 池管理
                        ch.pipeline().addLast("pool-guard", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof IdleStateEvent) {
                                    log.info("HTTP Channel 空闲超时: {}", deviceKey);
                                    if (healthChecker != null) {
                                        healthChecker.markFailure(deviceKey);
                                    }
                                    removeAndClose(deviceKey);
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                channels.remove(deviceKey, ctx.channel());
                                if (healthChecker != null) {
                                    healthChecker.markFailure(deviceKey);
                                }
                                ctx.fireChannelInactive();
                            }
                        });
                    }
                })
                .connect(parseHost(deviceKey), parsePort(deviceKey));

        cf.sync();
        Channel channel = cf.channel();
        channels.put(deviceKey, channel);
        log.info("新建 HTTP 池化 Channel: {}", deviceKey);
        return channel;
    }

    // ════════════════════════════════════════════════════
    // 基础操作
    // ════════════════════════════════════════════════════

    public void removeAndClose(String deviceKey) {
        Channel ch = channels.remove(deviceKey);
        if (ch != null) {
            HttpResponseFrameHandler.detachState(ch);
            if (ch.isOpen()) {
                ch.close();
            }
            log.debug("已移除并关闭 HTTP Channel: {}", deviceKey);
        }
    }

    public void markAlive(String deviceKey) {
        if (healthChecker != null) {
            healthChecker.markAlive(deviceKey);
        }
    }

    public void closeAll() {
        channels.values().forEach(ch -> {
            HttpResponseFrameHandler.detachState(ch);
            if (ch.isOpen()) {
                ch.close();
            }
        });
        channels.clear();
        log.info("HTTP 连接池已关闭");
    }
}
