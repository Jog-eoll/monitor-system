package com.gateway.device.transport.netty.udp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * UDP 入站处理器 —— O(1) 索引匹配 pending 请求，未匹配则转发到活跃的广播会话。
 */
@Slf4j
class UdpInboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final ConcurrentMap<String, String> senderToRequestId;
    private final ConcurrentMap<String, CompletableFuture<byte[]>> pendingRequests;
    private final BroadcastSessionHolder broadcastHolder;

    UdpInboundHandler(ConcurrentMap<String, String> senderToRequestId,
                      ConcurrentMap<String, CompletableFuture<byte[]>> pendingRequests,
                      BroadcastSessionHolder broadcastHolder) {
        this.senderToRequestId = senderToRequestId;
        this.pendingRequests = pendingRequests;
        this.broadcastHolder = broadcastHolder;
    }

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
            BroadcastSession session = broadcastHolder.get();
            if (session != null) {
                session.responses.putIfAbsent(senderKey, data);
            } else {
                log.debug("收到未匹配的 UDP 响应: sender={} size={}", senderKey, data.length);
            }
        }
    }

    /**
     * 广播会话持有者 —— 函数式接口，避免循环依赖。
     */
    @FunctionalInterface
    interface BroadcastSessionHolder {
        BroadcastSession get();
    }
}
