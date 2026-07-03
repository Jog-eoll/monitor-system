package com.gateway.device.transport.codec.colorlight.standard;

import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpMethod;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ColorLight HTTP 协议编解码器 —— FullHttpRequest/FullHttpResponse ↔ byte[]。
 *
 * <p>使用 Netty {@link EmbeddedChannel}（HttpClientCodec + HttpObjectAggregator）
 * 编解码 HTTP 消息，支持 Basic Auth 自动注入。</p>
 */
@Slf4j
public class ColorLightHttpCodec implements ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private static HttpMethod toNettyMethod(
            ColorLightHttpMethod method) {
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
    public byte[] encode(ColorLightHttpRequest request) {
        if (request == null) return new byte[0];
        try {
            FullHttpRequest httpRequest = buildHttpRequest(request);
            return httpRequestToBytes(httpRequest);
        } catch (Exception e) {
            log.error("Failed to encode HTTP request: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    // ── encode ──

    // ── protocol.HttpMethod → Netty HttpMethod 映射 ──

    @Override
    public ColorLightHttpResponse decode(byte[] response) {
        if (response == null || response.length == 0) return null;
        log.debug("[Codec] decode {} bytes: {}", response.length, new String(response, StandardCharsets.UTF_8));
        try {
            FullHttpResponse httpResponse = bytesToHttpResponse(response);
            return buildResponse(httpResponse);
        } catch (Exception e) {
            log.error("Failed to decode HTTP response: {}", e.getMessage(), e);
            return null;
        }
    }

    // ── encode ──

    private FullHttpRequest buildHttpRequest(ColorLightHttpRequest req) {
        ByteBuf bodyBuf = req.getBody() != null && req.getBody().length > 0
                ? Unpooled.wrappedBuffer(req.getBody())
                : Unpooled.EMPTY_BUFFER;

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, toNettyMethod(req.getMethod()), req.getUri(), bodyBuf);

        // 默认 Content-Type（请求头未设置时）
        if (req.getBody() != null && req.getBody().length > 0
                && !req.getHeaders().containsKey(HEADER_CONTENT_TYPE)) {
            request.headers().set(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        }
        // Host 头（HTTP/1.1 必需）
        if (req.getHost() != null && !req.getHost().isEmpty()) {
            request.headers().set(HttpHeaderNames.HOST, req.getHost());
        }
        // 每次请求后关闭连接（HTTP/1.1 标准释放机制）
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        // 自定义 Headers
        req.getHeaders().forEach((k, v) -> request.headers().set(k, v));

        // Authorization（调用方通过 account.toAuthorizationHeader() 或 entry.toAuthorizationHeader() 填入）
        if (req.getAuthorization() != null && !req.getAuthorization().isEmpty()) {
            request.headers().set(HEADER_AUTHORIZATION, req.getAuthorization());
        }

        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodyBuf.readableBytes());
        return request;
    }

    private byte[] httpRequestToBytes(FullHttpRequest request) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder());
        try {
            channel.writeOutbound(request.retain());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuf buf;
            while ((buf = channel.readOutbound()) != null) {
                byte[] chunk = new byte[buf.readableBytes()];
                buf.readBytes(chunk);
                out.write(chunk);
                buf.release();
            }
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to encode HTTP request: {}", e.getMessage(), e);
            return new byte[0];
        } finally {
            channel.close();
        }
    }

    // ── decode ──

    private FullHttpResponse bytesToHttpResponse(byte[] bytes) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new HttpResponseDecoder(),
                new HttpObjectAggregator(65536));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(bytes));
            channel.finish();
            return channel.readInbound();
        } finally {
            channel.close();
        }
    }

    private ColorLightHttpResponse buildResponse(FullHttpResponse httpResponse) {
        if (httpResponse == null) return null;
        Map<String, String> headers = new HashMap<>();
        httpResponse.headers().forEach(e -> headers.put(e.getKey(), e.getValue()));

        byte[] body;
        ByteBuf content = httpResponse.content();
        if (content.readableBytes() > 0) {
            body = new byte[content.readableBytes()];
            content.readBytes(body);
        } else {
            body = new byte[0];
        }
        httpResponse.release();

        return ColorLightHttpResponse.builder()
                .statusCode(httpResponse.status().code())
                .headers(headers)
                .body(body)
                .build();
    }
}
