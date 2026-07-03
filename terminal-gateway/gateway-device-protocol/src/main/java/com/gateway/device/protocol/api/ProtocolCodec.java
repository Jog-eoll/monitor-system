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
     * 将厂商命令编码为协议字节
     */
    byte[] encode(C command);

    /**
     * 将协议字节解码为厂商响应对象
     */
    R decode(byte[] response);
}
