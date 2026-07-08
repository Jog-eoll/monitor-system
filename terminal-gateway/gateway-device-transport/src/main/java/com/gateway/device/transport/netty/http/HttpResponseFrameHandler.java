package com.gateway.device.transport.netty.http;

import com.gateway.device.protocol.api.ParsedHttpResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

/**
 * HTTP 响应帧处理器 —— 手动聚合 HttpResponse + HttpContent 分块，无大小限制。
 *
 * <p>替代 HttpObjectAggregator（固定上限 64KB）。
 * LastHttpContent 到达后直接产出 {@link ParsedHttpResponse} 供 Codec 端
 * 通过 {@code ProtocolCodec.decodeParsed()} 消费，零中间序列化。</p>
 */
@Slf4j
@ChannelHandler.Sharable
class HttpResponseFrameHandler extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<FrameState> FRAME_STATE_KEY =
            AttributeKey.valueOf("httpFrameState");

    static void attachState(Channel channel, CompletableFuture<ParsedHttpResponse> future,
                            ScheduledFuture<?> timeoutTask) {
        channel.attr(FRAME_STATE_KEY).set(new FrameState(future, timeoutTask));
    }

    static void detachState(Channel channel) {
        channel.attr(FRAME_STATE_KEY).set(null);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel channel = ctx.channel();

        if (msg instanceof HttpResponse) {
            handleResponse(channel, (HttpResponse) msg);
        } else if (msg instanceof HttpContent) {
            handleContent(channel, (HttpContent) msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleResponse(Channel channel, HttpResponse response) {
        FrameState state = channel.attr(FRAME_STATE_KEY).get();
        if (state == null) {
            log.debug("收到 HttpResponse 但无 pending request: channel={}", channel.id());
            return;
        }

        state.statusCode = response.status().code();
        response.headers().forEach(e -> state.headers.put(e.getKey(), e.getValue()));
        log.debug("HTTP 响应头: status={} channel={}", state.statusCode, channel.id());
    }

    private void handleContent(Channel channel, HttpContent chunk) {
        FrameState state = channel.attr(FRAME_STATE_KEY).get();
        if (state == null) {
            chunk.release();
            return;
        }

        int readable = chunk.content().readableBytes();
        if (readable > 0) {
            try {
                chunk.content().readBytes(state.bodyBuf, readable);
            } catch (Exception e) {
                log.warn("读取 HttpContent 分块异常", e);
            }
        }

        if (chunk instanceof LastHttpContent) {
            state.timeoutTask.cancel(false);
            ParsedHttpResponse parsed = new ParsedHttpResponse(
                    state.statusCode, state.headers, state.bodyBuf.toByteArray());
            state.future.complete(parsed);
            channel.attr(FRAME_STATE_KEY).set(null);
            log.debug("HTTP 响应完成: status={} bodyBytes={} channel={}",
                    state.statusCode, state.bodyBuf.size(), channel.id());
        }

        chunk.release();
    }

    // ════════════════════════════════════════════════════

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        FrameState state = channel.attr(FRAME_STATE_KEY).getAndSet(null);
        if (state != null && !state.future.isDone()) {
            state.timeoutTask.cancel(false);
            state.future.completeExceptionally(
                    new IllegalStateException("HTTP channel inactive before response complete"));
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        FrameState state = channel.attr(FRAME_STATE_KEY).getAndSet(null);
        if (state != null && !state.future.isDone()) {
            state.timeoutTask.cancel(false);
            state.future.completeExceptionally(cause);
        }
        ctx.fireExceptionCaught(cause);
    }

    private static class FrameState {
        final CompletableFuture<ParsedHttpResponse> future;
        final ScheduledFuture<?> timeoutTask;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream bodyBuf = new ByteArrayOutputStream();
        int statusCode;

        FrameState(CompletableFuture<ParsedHttpResponse> future, ScheduledFuture<?> timeoutTask) {
            this.future = future;
            this.timeoutTask = timeoutTask;
        }
    }
}
