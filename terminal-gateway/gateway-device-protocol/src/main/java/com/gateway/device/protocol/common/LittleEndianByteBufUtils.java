package com.gateway.device.protocol.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 小端序字节缓冲区工具。
 * <p>
 * 协议所有多字节字段均为 Little-Endian，本工具类封装常用读写操作。
 * </p>
 */
public final class LittleEndianByteBufUtils {

    /**
     * hex dump 最大输出字节数，0 表示不限制。
     */
    public static final int HEX_DUMP_MAX_LENGTH = 512;

    // ════════════════════════════════════════════════════
    // 读取
    // ════════════════════════════════════════════════════

    private LittleEndianByteBufUtils() {
    }

    public static int readUShortLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }

    // ════════════════════════════════════════════════════
    // 写入
    // ════════════════════════════════════════════════════

    public static long readUIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFFL)
                | ((buf[offset + 1] & 0xFFL) << 8)
                | ((buf[offset + 2] & 0xFFL) << 16)
                | ((buf[offset + 3] & 0xFFL) << 24);
    }

    public static void writeUShortLE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    // ════════════════════════════════════════════════════
    // 转换
    // ════════════════════════════════════════════════════

    public static void writeUIntLE(byte[] buf, int offset, long value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    public static byte[] shortToLE(int value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF)};
    }

    public static byte[] intToLE(long value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    public static ByteBuffer newLEBuffer(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    // ════════════════════════════════════════════════════
    // 调试
    // ════════════════════════════════════════════════════

    public static ByteBuffer wrapLE(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static String toHex(byte[] data) {
        if (data == null) return "NULL";
        return toHex(data, 0, data.length);
    }

    public static String toHex(byte[] data, int offset, int length) {
        if (data == null) return "NULL";
        int end = Math.min(offset + length, data.length);
        int limit = HEX_DUMP_MAX_LENGTH > 0 ? Math.min(end - offset, HEX_DUMP_MAX_LENGTH) : end - offset;
        StringBuilder sb = new StringBuilder(limit * 3 + 32);
        for (int i = offset; i < offset + limit; i++) {
            if (i > offset) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        if (limit < end - offset) {
            sb.append(" ...(truncated, total=").append(end - offset).append(" bytes)");
        }
        return sb.toString();
    }

    public static String toString(byte[] data) {
        if (data == null) return "NULL";
        String str = new String(data, StandardCharsets.UTF_8);
        return str.substring(0, Math.min(str.length(), HEX_DUMP_MAX_LENGTH));
    }
}
