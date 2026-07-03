package com.gateway.device.protocol.api;

import com.gateway.device.protocol.model.DeviceContext;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 设备传输层抽象 —— 屏蔽底层 Netty/原生 Socket 差异。
 *
 * <p>gateway-protocol 通过此接口委托 I/O，不直接依赖 Netty。
 * gateway-device-core 中的 NettyTransportManager 实现此接口。</p>
 */
public interface DeviceTransport {

    /**
     * 异步发送并等待响应
     */
    CompletableFuture<byte[]> sendAndReceive(DeviceContext device, byte[] payload, Duration timeout);

    /**
     * 仅发送（广播/通知等无需响应场景）
     */
    CompletableFuture<Void> sendOnly(DeviceContext device, byte[] payload);

    /**
     * TCP 连通性探测 —— 仅验证端口可达，不发送应用层数据。
     *
     * <p>用于子网扫描等仅需确认设备存活的场景。
     * 默认返回 false，Netty 传输层覆盖为 Bootstrap.connect() 实现。</p>
     *
     * @return true 表示 TCP 握手成功，false 表示连接超时/拒绝
     */
    default CompletableFuture<Boolean> tcpProbe(DeviceContext device, Duration timeout) {
        return CompletableFuture.completedFuture(false);
    }
}
