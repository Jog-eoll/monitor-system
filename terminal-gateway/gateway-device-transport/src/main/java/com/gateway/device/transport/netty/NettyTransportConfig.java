package com.gateway.device.transport.netty;

import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import lombok.Data;

/**
 * Netty 传输层配置。
 */
@Data
public class NettyTransportConfig {

    /**
     * Whether to start the shared UDP channel on gateway startup.
     */
    private boolean udpEnabled = true;

    /**
     * Local UDP bind port. Use 0 to let the OS choose an available port.
     */
    private int udpLocalPort = 0;

    /**
     * boss 线程数
     */
    private int bossThreads = 1;

    /**
     * worker 线程数，0 表示自动（CPU 核数 × 2）
     */
    private int workerThreads = 0;

    /**
     * 请求默认超时（毫秒）
     */
    private long requestTimeoutMs = GatewayTimeoutConstants.TCP_REQUEST_TIMEOUT_MS;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 心跳间隔（秒）
     */
    private int heartbeatIntervalSec = GatewayTimeoutConstants.HEARTBEAT_INTERVAL_SEC;

    /**
     * 心跳失败多少次后标记离线
     */
    private int heartbeatMaxFailures = 3;

    /**
     * 重连退避初始间隔（秒）
     */
    private int reconnectBaseSec = GatewayTimeoutConstants.RECONNECT_BASE_SEC;

    /**
     * 重连退避最大间隔（秒）
     */
    private int reconnectMaxSec = GatewayTimeoutConstants.RECONNECT_MAX_SEC;

    /**
     * Channel 空闲超时（秒），超时后关闭并从池中移除
     */
    private int channelIdleTimeoutSec = GatewayTimeoutConstants.CHANNEL_IDLE_TIMEOUT_SEC;

    // ── 响应等待线程池 ──

    /**
     * TCP 响应等待线程池核心大小，0=CPU核数
     */
    private int responsePoolCore = 0;

    /**
     * TCP 响应等待线程池最大大小
     */
    private int responsePoolMax = 32;

    /**
     * TCP 响应等待线程池队列容量
     */
    private int responsePoolQueue = 100;

    // ── 连接池 ──

    /**
     * TCP 连接超时（毫秒）
     */
    private int connectTimeoutMs = GatewayTimeoutConstants.TCP_CONNECT_TIMEOUT_MS;

    // ── HTTP ──

    /**
     * HTTP 响应体最大长度（字节），用于 HttpObjectAggregator
     */
    private int httpMaxContentLength = 65536;
}
