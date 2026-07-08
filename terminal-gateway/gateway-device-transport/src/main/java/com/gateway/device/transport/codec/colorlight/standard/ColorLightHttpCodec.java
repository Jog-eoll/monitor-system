package com.gateway.device.transport.codec.colorlight.standard;

import com.gateway.device.protocol.api.ParsedHttpResponse;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpMethod;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * ColorLight HTTP 协议编解码器 —— 仅做业务对象转换，不处理 HTTP 线缆字节。
 *
 * <p>HTTP 线缆字节编码由 Netty 管道的 {@code HttpClientCodec} 完成，
 * 解码由 {@code HttpClientCodec} + {@code HttpResponseFrameHandler} 完成。
 * Codec 只负责：
 * <ul>
 *   <li>{@link #encodeRequest(ColorLightHttpRequest)} → {@link FullHttpRequest}</li>
 *   <li>{@link #decodeParsed(ParsedHttpResponse)} → {@link ColorLightHttpResponse}</li>
 * </ul>
 * 零 EmbeddedChannel、零 JSON 中间格式。</p>
 */
@Slf4j
public class ColorLightHttpCodec implements ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private static HttpMethod toNettyMethod(ColorLightHttpMethod method) {
        switch (method) {
            case POST:
                return HttpMethod.POST;
            case PUT:
                return HttpMethod.PUT;
            case DELETE:
                return HttpMethod.DELETE;
            default:
                return HttpMethod.GET;
        }
    }

    @Override
    public Object encodeRequest(ColorLightHttpRequest request) {
        if (request == null) return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return buildHttpRequest(request);
    }

    /**
     * 构建 Netty FullHttpRequest 对象，由管道的 HttpClientCodec 编码为线缆字节。
     */
    public FullHttpRequest buildHttpRequest(ColorLightHttpRequest req) {
        ByteBuf bodyBuf = req.getBody() != null && req.getBody().length > 0
                ? Unpooled.wrappedBuffer(req.getBody())
                : Unpooled.EMPTY_BUFFER;

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, toNettyMethod(req.getMethod()), req.getUri(), bodyBuf);

        if (req.getBody() != null && req.getBody().length > 0
                && !req.getHeaders().containsKey(HEADER_CONTENT_TYPE)) {
            request.headers().set(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        }
        if (req.getHost() != null && !req.getHost().isEmpty()) {
            request.headers().set(HttpHeaderNames.HOST, req.getHost());
        }
        req.getHeaders().forEach((k, v) -> request.headers().set(k, v));
        if (req.getAuthorization() != null && !req.getAuthorization().isEmpty()) {
            request.headers().set(HEADER_AUTHORIZATION, req.getAuthorization());
        }

        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodyBuf.readableBytes());
        return request;
    }

    /**
     * 从传输层已解析的 HTTP 响应组件直接构造业务对象 —— 唯一解码路径。
     */
    @Override
    public ColorLightHttpResponse decodeParsed(ParsedHttpResponse parsed) {
        if (parsed == null) return null;
        return ColorLightHttpResponse.builder()
                .statusCode(parsed.getStatusCode())
                .headers(new HashMap<>(parsed.getHeaders()))
                .body(parsed.getBody())
                .build();
    }

    /**
     * 已废弃 —— HTTP 解码由 Netty 管道完成，直接走 {@link #decodeParsed(ParsedHttpResponse)}。
     */
    @Override
    @Deprecated
    public ColorLightHttpResponse decode(byte[] response) {
        throw new UnsupportedOperationException("HTTP Codec 不再处理线缆字节，请使用 decodeParsed()");
    }

    /**
     * 已废弃 —— HTTP 编码由 Netty 管道 {@code HttpClientCodec} 完成。
     * 请使用 {@link #encodeRequest(ColorLightHttpRequest)}。
     */
    @Override
    @Deprecated
    public byte[] encode(ColorLightHttpRequest request) {
        throw new UnsupportedOperationException("HTTP Codec 不再处理线缆字节，请使用 encodeRequest()");
    }
}
