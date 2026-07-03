package com.gateway.udpproxy.entity.message;

import lombok.Builder;
import lombok.Data;

/**
 * 自定义消息头（固定 20 字节），兼容 TCP 流式拆包与 UDP 分片重组
 *
 * 字节布局：
 *   [0]     magic          协议魔数，固定 0xAB，用于识别是否为本协议报文
 *   [1]     version        协议版本号，当前为 0x01
 *   [2]     msgType        消息类型：0x01=透传原始数据，0x02=文本，0x03=图片
 *   [3]     encoding       编码方式：0x00=无/透传，0x01=UTF-8，0x02=JPEG，0x03=PNG
 *   [4-7]   messageId      消息ID（int）：UDP用于分片重组，TCP用于日志追踪
 *   [8-9]   fragmentIndex  当前分片序号（short，从0开始）：TCP固定填0
 *   [10-11] fragmentTotal  总分片数（short）：TCP固定填1，UDP为实际片数
 *   [12-15] bodyLength     本分片 Body 的字节数（int）
 *   [16-19] totalLength    消息总字节数含Header（int）：TCP拆包依据，UDP填0
 */
@Data
@Builder
public class MessageHeader {

    /** Header 固定字节长度 */
    public static final int HEADER_LENGTH = 20;

    // ── 魔数与版本 ────────────────────────────────
    /** 协议魔数，固定值，用于校验报文合法性 */
    public static final byte MAGIC   = (byte) 0xAB;
    /** 当前协议版本 */
    public static final byte VERSION = 0x01;

    // ── 消息类型常量 ──────────────────────────────
    /** 透传：原始 Sigma/JetFileII 数据，终端网关不解析直接转发 */
    public static final byte MSG_TYPE_PASSTHROUGH = 0x01;
    /** 文本内容（转码模式下使用） */
    public static final byte MSG_TYPE_TEXT        = 0x02;
    /** 图片内容（转码模式下使用） */
    public static final byte MSG_TYPE_IMAGE       = 0x03;

    // ── 编码方式常量 ──────────────────────────────
    /** 无编码/透传，Body 原样处理 */
    public static final byte ENCODING_NONE = 0x00;
    /** UTF-8 文本编码 */
    public static final byte ENCODING_UTF8 = 0x01;
    /** JPEG 图片 */
    public static final byte ENCODING_JPEG = 0x02;
    /** PNG 图片 */
    public static final byte ENCODING_PNG  = 0x03;

    // ── 字段 ──────────────────────────────────────
    /** 协议魔数 */
    private byte  magic;
    /** 协议版本 */
    private byte  version;
    /** 消息类型 */
    private byte  msgType;
    /** 编码方式 */
    private byte  encoding;
    /** 消息ID：UDP分片重组键，TCP日志追踪用 */
    private int   messageId;
    /** 当前分片序号（从0开始）；TCP固定为0 */
    private short fragmentIndex;
    /** 总分片数；TCP固定为1 */
    private short fragmentTotal;
    /** 本分片 Body 的字节数 */
    private int   bodyLength;
    /** 消息总字节数（Header + Body）；TCP拆包依据，UDP填0 */
    private int   totalLength;

    /**
     * 将 Header 序列化为 20 字节数组（大端序）
     */
    public byte[] toBytes() {
        byte[] buf = new byte[HEADER_LENGTH];
        buf[0] = magic;
        buf[1] = version;
        buf[2] = msgType;
        buf[3] = encoding;
        writeInt(buf, 4, messageId);
        writeShort(buf, 8, fragmentIndex);
        writeShort(buf, 10, fragmentTotal);
        writeInt(buf, 12, bodyLength);
        writeInt(buf, 16, totalLength);
        return buf;
    }

    /**
     * 从字节数组解析 Header（终端网关侧解密后调用）
     *
     * @param buf    数据缓冲区
     * @param offset 起始偏移量
     * @return 解析出的 MessageHeader；魔数校验失败返回 null
     */
    public static MessageHeader fromBytes(byte[] buf, int offset) {
        if (buf == null || buf.length < offset + HEADER_LENGTH) {
            return null;
        }
        if (buf[offset] != MAGIC) {
            // 魔数不匹配，不是本协议报文
            return null;
        }
        return MessageHeader.builder()
                .magic(buf[offset])
                .version(buf[offset + 1])
                .msgType(buf[offset + 2])
                .encoding(buf[offset + 3])
                .messageId(readInt(buf, offset + 4))
                .fragmentIndex(readShort(buf, offset + 8))
                .fragmentTotal(readShort(buf, offset + 10))
                .bodyLength(readInt(buf, offset + 12))
                .totalLength(readInt(buf, offset + 16))
                .build();
    }

    // ── 工具方法：大端序读写 ──────────────────────

    private static void writeInt(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value >> 24);
        buf[offset + 1] = (byte) (value >> 16);
        buf[offset + 2] = (byte) (value >> 8);
        buf[offset + 3] = (byte)  value;
    }

    private static void writeShort(byte[] buf, int offset, short value) {
        buf[offset]     = (byte) (value >> 8);
        buf[offset + 1] = (byte)  value;
    }

    private static int readInt(byte[] buf, int offset) {
        return ((buf[offset]     & 0xFF) << 24)
             | ((buf[offset + 1] & 0xFF) << 16)
             | ((buf[offset + 2] & 0xFF) << 8)
             |  (buf[offset + 3] & 0xFF);
    }

    private static short readShort(byte[] buf, int offset) {
        return (short) (((buf[offset] & 0xFF) << 8)
                       | (buf[offset + 1] & 0xFF));
    }
}
