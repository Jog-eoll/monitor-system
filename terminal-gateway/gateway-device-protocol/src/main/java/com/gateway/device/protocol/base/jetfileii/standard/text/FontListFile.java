package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FONTLIST.LST 字体列表文件模型 — 协议文档附录名词解释。
 *
 * <p>二进制结构:</p>
 * <pre>
 *   Header: "FL"(2B) + entryCount(2B LE)
 *   Entry × N (每条 28B):
 *     fileName[12]   — 字体文件名 (不足补 0x00)
 *     fontCode[4]    — 字体代码 (ASCII LE, 如 '5' → 0x00000035)
 *     fileSize[4]    — 文件大小 (LE)
 *     checksum[4]    — 校验值 (LE)
 *     height(1B)     — 字高
 *     width(1B)      — 字宽
 *     attr1(1B)      — 属性1 (1=小字/2=大字/4=32px)
 *     attr2(1B)      — 属性2 (同 height)
 * </pre>
 */
public class FontListFile {

    private static final byte[] MAGIC = {'F', 'L'};

    private final List<FontEntry> entries;

    public FontListFile(List<FontEntry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static FontListFile parse(byte[] data) {
        if (data == null || data.length < 4)
            throw new IllegalArgumentException("数据太短");
        if (data[0] != 'F' || data[1] != 'L')
            throw new IllegalArgumentException("不是有效的 FONTLIST.LST (缺少 FL 标识)");

        int count = LittleEndianByteBufUtils.readUShortLE(data, 2);
        int expectedLen = 4 + count * 28;
        if (data.length < expectedLen)
            throw new IllegalArgumentException("数据长度不足: 期望≥" + expectedLen + " 实际=" + data.length);

        List<FontEntry> entries = new ArrayList<>(count);
        int off = 4;
        for (int i = 0; i < count; i++) {
            entries.add(FontEntry.fromBytes(data, off));
            off += 28;
        }
        return new FontListFile(entries);
    }

    public List<FontEntry> entries() {
        return entries;
    }

    // ── 序列化 ────────────────────────────────────────

    public int count() {
        return entries.size();
    }

    // ── 解析 ──────────────────────────────────────────

    public byte[] toBytes() {
        byte[] buf = new byte[4 + entries.size() * 28];
        buf[0] = 'F';
        buf[1] = 'L';
        LittleEndianByteBufUtils.writeUShortLE(buf, 2, entries.size());
        int off = 4;
        for (FontEntry e : entries) {
            off = e.writeTo(buf, off);
        }
        return buf;
    }

    /**
     * 根据文件名查找条目
     */
    public FontEntry findByName(String fileName) {
        for (FontEntry e : entries) if (e.fileName.equals(fileName)) return e;
        return null;
    }

    /**
     * 根据字体代码查找条目
     */
    public FontEntry findByCode(char fontCode) {
        for (FontEntry e : entries) if (e.fontCode == fontCode) return e;
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FONTLIST (" + entries.size() + " 字体)\n");
        for (FontEntry e : entries) {
            sb.append(String.format("  %-16s 代码='%c' 大小=%-8d %d×%d\n",
                    e.fileName, e.fontCode, e.fileSize, e.width, e.height));
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════
    // FontEntry
    // ══════════════════════════════════════════════════════

    @Data
    @Builder
    public static class FontEntry {
        /**
         * 字体文件名 (≤12 字符)
         */
        private String fileName;
        /**
         * 字体代码 (对应 {@link JetFileIIFont})
         */
        private char fontCode;
        /**
         * 字体文件大小 (字节)
         */
        private int fileSize;
        /**
         * 校验值
         */
        private int checksum;
        /**
         * 字宽
         */
        private int width;
        /**
         * 字高
         */
        private int height;
        /**
         * 属性1
         */
        private byte attr1;
        /**
         * 属性2
         */
        private byte attr2;

        /**
         * 从缓冲区解析
         */
        static FontEntry fromBytes(byte[] data, int off) {
            // fileName[12]
            int nameEnd = off + 12;
            while (nameEnd > off && data[nameEnd - 1] == 0x00) nameEnd--;
            String fileName = new String(data, off, nameEnd - off, ProtocolConstant.GB18030);
            off += 12;

            int fontCode = (int) LittleEndianByteBufUtils.readUIntLE(data, off);
            off += 4;
            int fileSize = (int) LittleEndianByteBufUtils.readUIntLE(data, off);
            off += 4;
            int checksum = (int) LittleEndianByteBufUtils.readUIntLE(data, off);
            off += 4;

            int height = data[off++] & 0xFF;
            int width = data[off++] & 0xFF;
            byte attr1 = data[off++];
            byte attr2 = data[off++];

            return FontEntry.builder()
                    .fileName(fileName)
                    .fontCode((char) fontCode)
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .width(width)
                    .height(height)
                    .attr1(attr1)
                    .attr2(attr2)
                    .build();
        }

        /**
         * 写入到缓冲区
         */
        int writeTo(byte[] buf, int off) {
            // fileName[12]
            byte[] nameBytes = fileName.getBytes(ProtocolConstant.GB18030);
            int nameLen = Math.min(nameBytes.length, 12);
            System.arraycopy(nameBytes, 0, buf, off, nameLen);
            for (int i = nameLen; i < 12; i++) buf[off + i] = 0x00;
            off += 12;

            // fontCode[4] (LE)
            LittleEndianByteBufUtils.writeUIntLE(buf, off, fontCode);
            off += 4;

            // fileSize[4] (LE)
            LittleEndianByteBufUtils.writeUIntLE(buf, off, fileSize);
            off += 4;

            // checksum[4] (LE)
            LittleEndianByteBufUtils.writeUIntLE(buf, off, checksum);
            off += 4;

            // height, width, attr1, attr2
            buf[off++] = (byte) height;
            buf[off++] = (byte) width;
            buf[off++] = attr1;
            buf[off++] = attr2;
            return off;
        }
    }
}
