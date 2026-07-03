package com.gateway.device.transport.netty;

import com.gateway.device.protocol.model.DeviceContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 设备 Channel 连接池 —— 维护 deviceKey → Channel 映射。
 *
 * <p>TCP/HTTP Channel 按 deviceKey 池化复用，内建空闲检测和健康上报。
 * UDP 设备共享单个 DatagramChannel。</p>
 */
@Slf4j
public class DeviceChannelPool {

    private static final AttributeKey<String> DEVICE_KEY_ATTR = AttributeKey.valueOf("deviceKey");

    /**
     * deviceKey = vendor:ip:port
     */
    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();

    /**
     * 共享的 UDP Channel（所有 UDP 设备复用）
     */
    @Setter
    @Getter
    private volatile Channel sharedUdpChannel;

    /**
     * 健康检查器（可选，设置后池化 Channel 自动上报健康状态）
     */
    @Setter
    private ConnectionHealthChecker healthChecker;

    // ════════════════════════════════════════════════════
    // Key 生成
    // ════════════════════════════════════════════════════

    /**
     * 生成 deviceKey
     */
    public static String deviceKey(String vendor, String ip, int port) {
        return vendor + ":" + ip + ":" + port;
    }

    /**
     * 从 DeviceContext 生成 deviceKey
     */
    public static String deviceKey(DeviceContext device) {
        return device.getVendor().name() + ":" + device.getIp() + ":" + device.getPort();
    }

    // ════════════════════════════════════════════════════
    // TCP/HTTP 连接池操作
    // ════════════════════════════════════════════════════

    private static String parseHost(String deviceKey) {
        int first = deviceKey.indexOf(':');
        int last = deviceKey.lastIndexOf(':');
        return deviceKey.substring(first + 1, last);
    }

    private static int parsePort(String deviceKey) {
        int last = deviceKey.lastIndexOf(':');
        return Integer.parseInt(deviceKey.substring(last + 1));
    }

    /**
     * 获取或创建 TCP/HTTP Channel，复用 deviceKey 维度的活跃连接。
     *
     * @param deviceKey 设备标识
     * @param group     Netty EventLoopGroup
     * @param config    传输配置
     * @return 活跃的 Channel
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
                        // 空闲检测：仅检测读空闲
                        ch.pipeline().addLast("idle",
                                new IdleStateHandler(idleTimeoutSec, 0, 0, TimeUnit.SECONDS));
                        // 空闲/异常 → 从池中移除
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

        cf.sync(); // 阻塞等待连接建立
        Channel channel = cf.channel();
        channels.put(deviceKey, channel);
        log.info("新建池化 Channel: {}", deviceKey);
        return channel;
    }

    // ════════════════════════════════════════════════════
    // 基础操作
    // ════════════════════════════════════════════════════

    /**
     * 从池中移除并关闭 Channel（故障/超时时调用）。
     */
    public void removeAndClose(String deviceKey) {
        Channel ch = channels.remove(deviceKey);
        if (ch != null && ch.isOpen()) {
            ch.close();
            log.debug("已移除并关闭 Channel: {}", deviceKey);
        }
    }

    /**
     * 上报健康状态 —— 心跳成功。
     */
    public void markAlive(String deviceKey) {
        if (healthChecker != null) {
            healthChecker.markAlive(deviceKey);
        }
    }

    public Channel get(String deviceKey) {
        return channels.get(deviceKey);
    }

    // ════════════════════════════════════════════════════
    // 内部工具
    // ════════════════════════════════════════════════════

    public boolean contains(String deviceKey) {
        return channels.containsKey(deviceKey);
    }

    /**
     * 关闭所有 Channel
     */
    public void closeAll() {
        Channel udp = sharedUdpChannel;
        if (udp != null && udp.isOpen()) {
            udp.close();
        }
        channels.values().forEach(ch -> {
            if (ch != null && ch.isOpen()) {
                ch.close();
            }
        });
        channels.clear();
        log.info("连接池已关闭");
    }
}
