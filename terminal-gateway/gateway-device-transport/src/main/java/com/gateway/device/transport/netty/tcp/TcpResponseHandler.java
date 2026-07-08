package com.gateway.device.transport.netty.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TCP 二进制协议响应处理器 —— 收集所有 ByteBuf 分块，通道关闭时完成。
 *
 * <p>作为临时 handler 挂载到池化 Channel 的 pipeline 尾部，请求完成后移除。
 * 二进制 TCP 协议无帧边界，依赖 channelInactive 判定响应结束。</p>
 */
@Slf4j
@ChannelHandler.Sharable
public class TcpResponseHandler extends ChannelInboundHandlerAdapter {
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
            buf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        latch.countDown();
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("TCP response handler exception: {}", cause.getMessage());
        latch.countDown();
        ctx.fireExceptionCaught(cause);
    }

    byte[] getResponse(long timeoutMs) throws InterruptedException {
        boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        synchronized (buffer) {
            byte[] data = buffer.toByteArray();
            if (!completed) {
                log.debug("TCP getResponse timeout, partial={} bytes", data.length);
            }
            return data;
        }
    }

    /**
     * 供超时回调强制唤醒等待线程，避免空等满超时。
     */
    void forceComplete() {
        latch.countDown();
    }
}
