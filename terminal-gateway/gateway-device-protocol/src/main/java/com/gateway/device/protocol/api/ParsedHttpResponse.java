package com.gateway.device.protocol.api;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 传输层已解析的 HTTP 响应组件 —— 纯 POJO，无 Netty 依赖。
 *
 * <p>HTTP 传输层的 pipeline 解析 HTTP 响应后，通过此类将结构化数据传递给
 * {@link ProtocolCodec#decodeParsed(ParsedHttpResponse)}，
 * 绕过原始字节的重复编解码。</p>
 */
@Getter
public class ParsedHttpResponse {

    private final int statusCode;
    private final Map<String, String> headers;
    private final byte[] body;

    public ParsedHttpResponse(int statusCode, Map<String, String> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.body = body != null ? body : new byte[0];
    }
}
