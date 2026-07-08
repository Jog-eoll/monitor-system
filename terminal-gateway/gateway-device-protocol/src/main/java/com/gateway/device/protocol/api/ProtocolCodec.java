package com.gateway.device.protocol.api;

/**
 * 协议编解码器 —— 命令对象 ↔ 字节数组。
 *
 * <p>在 gateway-device-core 中，Codec 会被适配为 Netty ChannelHandler
 * 注册到 pipeline，实现编解码与传输的解耦。</p>
 *
 * @param <C> 厂商命令对象类型
 * @param <R> 厂商响应对象类型
 */
public interface ProtocolCodec<C, R> {

    /**
     * 将厂商命令编码为协议字节（二进制协议）。
     * HTTP Codec 应改用 {@link #encodeRequest(Object)}。
     */
    byte[] encode(C command);

    /**
     * 将协议字节解码为厂商响应对象（二进制协议）。
     * HTTP Codec 应改用 {@link #decodeParsed(ParsedHttpResponse)}。
     */
    R decode(byte[] response);

    /**
     * 将厂商命令编码为传输层原生请求对象，供 HTTP 传输直接写入 Netty 管道。
     *
     * <p>默认委托 {@link #encode(Object)}（二进制 Codec 返回 byte[]）。
     * HTTP Codec 覆盖此方法返回 Netty HttpRequest 对象，
     * 由管道中的 HttpClientCodec 完成线缆字节编码。</p>
     */
    default Object encodeRequest(C command) {
        return encode(command);
    }

    /**
     * 从传输层已解析的 HTTP 响应组件直接解码 —— 跳过原始字节解析。
     *
     * <p>HTTP Codec 应覆盖此方法为主路径。
     * 默认降级：取 body 走 {@link #decode(byte[])}（二进制 Codec 降级路径）。</p>
     */
    default R decodeParsed(ParsedHttpResponse parsed) {
        return decode(parsed.getBody());
    }
}
