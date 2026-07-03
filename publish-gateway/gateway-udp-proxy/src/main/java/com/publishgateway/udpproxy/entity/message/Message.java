package com.publishgateway.udpproxy.entity.message;

import lombok.Getter;

/**
 * 完整消息体 = Header（20字节固定头）+ Body（变长载荷）
 *
 * 透传模式：Body = Sigma/JetFileII 原始数据
 * 转码模式：Body = 转码后的文本或图片内容
 *
 * 使用场景：
 *   发布网关：构建 Message → toBytes() → 加密 → 发送
 *   终端网关：解密 → fromBytes() → 读 Header → 解码 Body → 重组 → 转发情报板
 */
@Getter
public class Message {

    private final MessageHeader header;
    private final byte[]        body;

    public Message(MessageHeader header, byte[] body) {
        this.header = header;
        this.body   = body;
    }

    /**
     * 序列化为字节数组，用于加密和网络传输
     * 结构：[Header 20字节][Body N字节]
     */
    public byte[] toBytes() {
        byte[] headerBytes = header.toBytes();
        byte[] result = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, result, 0,                 headerBytes.length);
        System.arraycopy(body,        0, result, headerBytes.length, body.length);
        return result;
    }

    /**
     * 从解密后的字节数组解析 Message（终端网关侧使用）
     *
     * @param buf 解密后的原始字节
     * @return 解析出的 Message；Header 魔数校验失败或长度不足返回 null
     */
    public static Message fromBytes(byte[] buf) {
        if (buf == null || buf.length < MessageHeader.HEADER_LENGTH) {
            return null;
        }
        MessageHeader header = MessageHeader.fromBytes(buf, 0);
        if (header == null) {
            return null;
        }
        int bodyLen = header.getBodyLength();
        if (buf.length < MessageHeader.HEADER_LENGTH + bodyLen) {
            return null;
        }
        byte[] body = new byte[bodyLen];
        System.arraycopy(buf, MessageHeader.HEADER_LENGTH, body, 0, bodyLen);
        return new Message(header, body);
    }

    /**
     * 判断是否为透传消息
     */
    public boolean isPassthrough() {
        return header.getMsgType() == MessageHeader.MSG_TYPE_PASSTHROUGH;
    }

    /**
     * 判断是否为最后一个分片（或不分片的完整消息）
     */
    public boolean isLastFragment() {
        return header.getFragmentIndex() == header.getFragmentTotal() - 1;
    }

    /**
     * 判断是否需要分片重组（分片总数大于1）
     */
    public boolean isFragmented() {
        return header.getFragmentTotal() > 1;
    }

    @Override
    public String toString() {
        return String.format("Message{msgType=0x%02X, encoding=0x%02X, messageId=%d, "
                + "fragment=%d/%d, bodyLength=%d, totalLength=%d}",
                header.getMsgType(), header.getEncoding(), header.getMessageId(),
                header.getFragmentIndex() + 1, header.getFragmentTotal(),
                header.getBodyLength(), header.getTotalLength());
    }
}
