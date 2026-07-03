package com.gateway.device.protocol.base.jetfileii.standard.transfer;

import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;

import java.io.IOException;

/**
 * 传输适配器接口 — 抽象底层传输方式（Netty/DatagramSocket），
 * 对上层提供同步的请求-响应语义。
 */
public interface TransportAdapter {
    PacketMessage sendAndReceive(byte[] req) throws IOException;
}
