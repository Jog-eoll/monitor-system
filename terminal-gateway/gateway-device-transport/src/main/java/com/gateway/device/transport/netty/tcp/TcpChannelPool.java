package com.gateway.device.transport.netty.tcp;

import com.gateway.device.transport.netty.ConnectionHealthChecker;
import com.gateway.device.transport.netty.NettyTransportConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * TCP 二进制协议 Channel 连接池 —— deviceKey → Channel 映射。
 *
 * <p>按 vendor:ip:port 池化复用 NioSocketChannel，内建空闲检测和健康上报。
 * 仅用于原始二进制 TCP 协议（JetFileII、NovaStar），HTTP 有独立的 {@code HttpChannelPool}。</p>
 */
@Slf4j
public class TcpChannelPool {

    static final AttributeKey<String> DEVICE_KEY_ATTR = AttributeKey.valueOf("deviceKey");

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();

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
     * 获取或创建 TCP Channel，复用 deviceKey 维度的活跃连接。
     */
    public Channel getOrCreate(String deviceKey, EventLoopGroup group,
                               NettyTransportConfig config) throws Exception {
        // 快路径：复用已有活跃连接
        Channel existing = channels.get(deviceKey);
        if (existing != null && existing.isActive()) {
            log.debug("复用池化 Channel: {}", deviceKey);
            return existing;
        }

        // 慢路径：清理过期引用，创建新连接
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
                        ch.pipeline().addLast("idle",
                                new IdleStateHandler(idleTimeoutSec, 0, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast("pool-guard", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof IdleStateEvent) {
                                    log.info("Channel 空闲超时，从池中移除: {}", deviceKey);
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
        log.info("新建 TCP 池化 Channel: {}", deviceKey);
        return channel;
    }

    // ════════════════════════════════════════════════════
    // 基础操作
    // ════════════════════════════════════════════════════

    public void removeAndClose(String deviceKey) {
        Channel ch = channels.remove(deviceKey);
        if (ch != null && ch.isOpen()) {
            ch.close();
            log.debug("已移除并关闭 TCP Channel: {}", deviceKey);
        }
    }

    public void markAlive(String deviceKey) {
        if (healthChecker != null) {
            healthChecker.markAlive(deviceKey);
        }
    }

    public Channel get(String deviceKey) {
        return channels.get(deviceKey);
    }

    // ════════════════════════════════════════════════════
    // 生命周期
    // ════════════════════════════════════════════════════

    public void closeAll() {
        channels.values().forEach(ch -> {
            if (ch != null && ch.isOpen()) {
                ch.close();
            }
        });
        channels.clear();
        log.info("TCP 连接池已关闭");
    }
}
