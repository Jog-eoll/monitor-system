package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

/**
 * FNT 字库文件元数据解析 — 从文件尾部 "FONT" 标记区域读取宽高。
 *
 * <p>FNT 尾部结构 (每字段 4B, 小端序, "FONT" 标记后的偏移因版本而异):
 * <pre>
 *   [位图数据...] 04 "FONT" [3×uint32 元数据] [w(4B)][h(4B)][enc(4B)][fileSize(4B)] "LLIB" [padding]
 * </pre>
 * 解析策略: 从文件尾反查 fileSize 字段，再向前定位宽高编码。
 */
public final class FntParser {

    private FntParser() {
    }

    /**
     * 从 FNT 文件字节解析宽高。
     * 从文件尾搜索等于 data.length 的 uint32 LE → 确认为 fileSize 字段，
     * 再向前读取 width(4B) / height(4B) / encoding(4B) / fileSize(4B)。
     */
    public static FntInfo parse(byte[] data) {
        if (data == null || data.length < 48) return null;

        int totalLen = data.length;

        // 搜索终止标记 0xFFFFFFFF，向前定位 fileSize
        int fileSizeOff = findFileSizeOff(data);
        if (fileSizeOff < 12) return null;

        // fileSize 前 3 个 uint32 依次为 encoding, width, height
        int wOff = fileSizeOff - 8;
        int hOff = fileSizeOff - 12;
        long w = LittleEndianByteBufUtils.readUIntLE(data, wOff);
        long h = LittleEndianByteBufUtils.readUIntLE(data, hOff);

        int width = (int) w;
        int height = (int) h;

        // Normal5 等极窄字体: height 字段可能存字节宽 (5px→1B)
        if (height == 1 && width >= 5 && width <= 9) height = width;

        // 异常值容错: 若 w/h 过大，尝试 BE
        if (width < 1 || width > 1024) {
            width = readUint32BE(data, wOff);
        }
        if (height < 1 || height > 1024) {
            height = readUint32BE(data, hOff);
        }

        // 仍异常则放弃
        if (width < 1 || width > 512 || height < 1 || height > 512) return null;

        return new FntInfo(width, height, totalLen);
    }

    /**
     * 从尾部搜索 "LLIB" 标记, 反推 fileSize 位置
     */
    private static int findFileSizeOff(byte[] data) {
        // 搜索 "LLIB"
        int end = Math.max(0, data.length - 64);
        int llib = -1;
        for (int i = data.length - 4; i >= end; i--) {
            if (data[i] == 'L' && data[i + 1] == 'L' && data[i + 2] == 'I' && data[i + 3] == 'B') {
                llib = i;
                break;
            }
        }
        if (llib < 16) return -1;

        // fileSize 位于 "LLIB" 前 12B; 若该处为 0 则取 -8 (极少数变体)
        long v12 = LittleEndianByteBufUtils.readUIntLE(data, llib - 12);
        return (v12 > 0) ? llib - 12 : llib - 8;
    }

    private static int readUint32BE(byte[] data, int off) {
        return ((data[off] & 0xFF) << 24)
                | ((data[off + 1] & 0xFF) << 16)
                | ((data[off + 2] & 0xFF) << 8)
                | (data[off + 3] & 0xFF);
    }

    /**
     * 便捷方法: 直接读取 FNT 文件的宽高，返回 [width, height]
     */
    public static int[] dimensions(byte[] fntData) {
        FntInfo info = parse(fntData);
        if (info == null) return new int[]{0, 0};
        return new int[]{info.width, info.height};
    }

    /**
     * FNT 文件解析结果
     */
    public static class FntInfo {
        public final int width;
        public final int height;
        public final int fileSize;

        FntInfo(int width, int height, int fileSize) {
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
        }

        @Override
        public String toString() {
            return width + "×" + height + "  " + fileSize + "B";
        }
    }
}
