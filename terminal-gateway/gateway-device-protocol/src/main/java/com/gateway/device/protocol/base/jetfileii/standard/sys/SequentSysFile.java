package com.gateway.device.protocol.base.jetfileii.standard.sys;

import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * SEQUENT.SYS 播放列表文件模型 —— 构建/解析设备播放列表。
 *
 * <p>参考 JetFileII 协议文档 §19 播放列表格式。</p>
 *
 * <pre>{@code
 *   SequentFile sf = new SequentFile();
 *   sf.getHeader().setCurrentIndex(3);
 *   sf.addEntry("d:\\t\\file1.Nmg", false);
 *   sf.addEntry("d:\\t\\file2.Nmg", true);  // 当前播放
 *   byte[] data = sf.toBytes();
 *   // 写入设备...
 * }</pre>
 */
@Getter
public class SequentSysFile {

    /**
     * 扩展模式: 每个条目 332B (path区256B + shortName区包含meta)
     */
    static final int FIELD_EXPANDED = 340;
    private static final int HEADER_SIZE = 18;
    private static final int GAP_SIZE = 26;
    private static final int PAD_LEN = 6;
    /**
     * 扩展模式下 Meta 在条目内的固定偏移
     */
    private static final int META_OFFSET = 320;
    /**
     * 扩展模式下短文件名在条目内的偏移
     */
    private static final int SHORT_NAME_OFFSET = 256;

    private static final byte[] TAIL = {0x08, 0x20, 0x01, 0x01, 0x23, 0x59, 0x01, 0x01};
    private static final byte[] SUFFIX = {0x01, 0x01};

    // ═══════════════════════════════════════════════════
    // 构建
    // ═══════════════════════════════════════════════════
    private final Header header = new Header();
    private final List<Entry> entries = new ArrayList<>();

    // ═══════════════════════════════════════════════════
    // 解析
    // ═══════════════════════════════════════════════════

    /**
     * 从字节数组解析 SEQUENT.SYS
     */
    public static SequentSysFile parse(byte[] data) {
        SequentSysFile sf = new SequentSysFile();
        if (data.length < 18) return sf;

        Header h = Header.fromBytes(data, 0);
        sf.header.currentIndex = h.currentIndex;
        sf.header.dataEnd = h.dataEnd;
        sf.header.field = h.field;

        // 扫描所有 null 结尾的路径字符串
        List<PathPos> paths = scanPaths(data);
        short currentIndex = h.currentIndex;

        for (int i = 0; i < paths.size(); i++) {
            PathPos pp = paths.get(i);
            Entry e = new Entry(pp.path, (i + 1) == currentIndex);

            // 解析元数据
            int metaOff = pp.offset + pp.length + 1;
            if (e.current && metaOff < data.length) metaOff++; // skip status byte
            if (metaOff + 12 <= data.length) {
                e.meta = Meta.fromBytes(data, metaOff);
            }
            sf.entries.add(e);
        }
        return sf;
    }

    // ═══════════════════════════════════════════════════
    // 内部常量 & 工具
    // ═══════════════════════════════════════════════════

    static byte[] gapBytes(int field) {
        return new byte[]{
                0x01, 0x01, 0x08, 0x20, 0x01, 0x01, 0x23, 0x59, 0x01, 0x01,
                'F', 'S', 0x12, 0x00, 0x01, 0x00, 0x01, 0x00,
                (byte) (field & 0xFF), (byte) (field >> 8),
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
    }

    private static List<PathPos> scanPaths(byte[] d) {
        List<PathPos> paths = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < d.length; i++) {
            int b = d[i] & 0xFF;
            if (b >= 0x20) {
                if (start < 0) start = i;
            } else if (b == 0x00 && start >= 0) {
                int len = i - start;
                String s = new String(d, start, len, ProtocolConstant.GB18030);
                if (s.contains("\\") || (s.contains(".") && len > 4)) {
                    paths.add(new PathPos(start, len, s));
                }
                start = -1;
            } else {
                start = -1;
            }
        }
        return paths;
    }

    public Entry addEntry(String path, boolean current) {
        Entry e = new Entry(path, current);
        entries.add(e);
        return e;
    }

    /**
     * 序列化为 SEQUENT.SYS 字节数组
     */
    public byte[] toBytes() {
        int n = entries.size();
        byte[][] eb = new byte[n][];
        int entriesLen = 0;
        for (int i = 0; i < n; i++) {
            eb[i] = entries.get(i).toBytes(header.field);
            entriesLen += eb[i].length;
        }
        int gapsLen = Math.max(0, n - 1) * GAP_SIZE;

        int dataEnd = HEADER_SIZE + PAD_LEN + entriesLen + gapsLen + SUFFIX.length;
        header.dataEnd = (short) dataEnd;
        byte[] hb = header.toBytes();

        int total = dataEnd + TAIL.length;
        byte[] buf = new byte[total];
        int pos = 0;
        System.arraycopy(hb, 0, buf, pos, hb.length);
        pos += hb.length;
        pos += PAD_LEN;
        for (int i = 0; i < n; i++) {
            System.arraycopy(eb[i], 0, buf, pos, eb[i].length);
            pos += eb[i].length;
            if (i < n - 1) {
                byte[] gap = gapBytes(header.field);
                System.arraycopy(gap, 0, buf, pos, gap.length);
                pos += gap.length;
            }
        }
        System.arraycopy(SUFFIX, 0, buf, pos, SUFFIX.length);
        pos += SUFFIX.length;
        System.arraycopy(TAIL, 0, buf, pos, TAIL.length);
        return buf;
    }

    /**
     * SQ 头 (18B)
     */
    @Setter
    @Getter
    public static class Header {
        static final byte[] SIGNATURE = {'S', 'Q'};
        static final short VERSION = 6;
        static final byte[] FS = {'F', 'S'};
        static final short ENTRY_SIZE = 18;

        private short currentIndex = 1;
        private short dataEnd;
        private short addCount = 1;
        private short field = FIELD_EXPANDED;
        private short flags = (short) 0x0101;

        static Header fromBytes(byte[] data, int off) {
            Header h = new Header();
            h.currentIndex = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 4);
            h.dataEnd = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 6);
            h.addCount = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 12);
            h.field = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 16);
            return h;
        }

        byte[] toBytes() {
            byte[] b = new byte[18];
            b[0] = SIGNATURE[0];
            b[1] = SIGNATURE[1];
            LittleEndianByteBufUtils.writeUShortLE(b, 2, VERSION);
            LittleEndianByteBufUtils.writeUShortLE(b, 4, currentIndex);
            LittleEndianByteBufUtils.writeUShortLE(b, 6, dataEnd);
            b[8] = FS[0];
            b[9] = FS[1];
            LittleEndianByteBufUtils.writeUShortLE(b, 10, ENTRY_SIZE);
            LittleEndianByteBufUtils.writeUShortLE(b, 12, addCount);
            LittleEndianByteBufUtils.writeUShortLE(b, 14, flags);
            LittleEndianByteBufUtils.writeUShortLE(b, 16, field);
            return b;
        }
    }

    /**
     * 条目元数据 (12B) — 前 4B 按文件类型区分:
     * PICTURE→0xFFFFFFFF, TEXT有行数→lineCount|0x2F2F0000, 其他→0x00000000
     */
    @Setter
    @Getter
    public static class Meta {
        private int endMarker;
        private short brightness = 127;
        private short duration = 8200;
        private short flags = (short) 0x0101;
        private short reserved;

        static Meta fromBytes(byte[] data, int off) {
            Meta m = new Meta();
            m.endMarker = (int) LittleEndianByteBufUtils.readUIntLE(data, off);
            m.brightness = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 4);
            m.duration = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 6);
            m.flags = (short) LittleEndianByteBufUtils.readUShortLE(data, off + 8);
            return m;
        }

        byte[] toBytes() {
            byte[] b = new byte[12];
            LittleEndianByteBufUtils.writeUIntLE(b, 0, endMarker);
            LittleEndianByteBufUtils.writeUShortLE(b, 4, brightness);
            LittleEndianByteBufUtils.writeUShortLE(b, 6, duration);
            LittleEndianByteBufUtils.writeUShortLE(b, 8, flags);
            LittleEndianByteBufUtils.writeUShortLE(b, 10, reserved);
            return b;
        }
    }

    /**
     * 一个播放条目
     */
    @Setter
    @Getter
    public static class Entry {
        private static final int TEXT_META_FLAG = 0x2F2F;
        private String path;
        private Meta meta = new Meta();
        private boolean current;

        public Entry(String path, boolean current) {
            this.path = path;
            this.current = current;
        }

        /**
         * 标记为 PICTURE(.jpg/.png) 条目
         */
        public Entry pictureEntry() {
            this.meta.endMarker = 0xFFFFFFFF;
            return this;
        }

        /**
         * 标记为 TEXT(.nmg) 条目，设置行数元数据
         */
        public Entry textEntry(int lineCount) {
            this.meta.endMarker = (lineCount & 0xFFFF) | (TEXT_META_FLAG << 16);
            return this;
        }

        boolean hasNonAscii() {
            if (path == null) return false;
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) >= 0x80) return true;
            }
            return false;
        }

        byte[] toBytes(int field) {
            byte[] pb = (path + "\0").getBytes(ProtocolConstant.GB18030);
            byte[] mb = meta.toBytes();

            if (field >= 256) {
                // 扩展格式: path区(256B) + 短文件名区 + Meta@320
                byte[] entry = new byte[META_OFFSET + mb.length];
                System.arraycopy(pb, 0, entry, 0, Math.min(pb.length, SHORT_NAME_OFFSET));
                int lastSep = Math.max(path.lastIndexOf('\\'), path.lastIndexOf(':'));
                if (lastSep >= 0) {
                    byte[] shortName = (path.substring(lastSep + 1) + "\0")
                            .getBytes(ProtocolConstant.GB18030);
                    System.arraycopy(shortName, 0, entry, SHORT_NAME_OFFSET,
                            Math.min(shortName.length, META_OFFSET - SHORT_NAME_OFFSET));
                }
                System.arraycopy(mb, 0, entry, META_OFFSET, mb.length);
                return entry;
            }

            // 紧凑格式: path + pad + meta
            int align = field == 40 ? 4 : 2;
            int pad = (align - pb.length % align) % align;
            byte[] entry = new byte[pb.length + pad + mb.length];
            System.arraycopy(pb, 0, entry, 0, pb.length);
            System.arraycopy(mb, 0, entry, pb.length + pad, mb.length);
            return entry;
        }
    }

    private static class PathPos {
        final int offset, length;
        final String path;

        PathPos(int o, int l, String p) {
            offset = o;
            length = l;
            path = p;
        }
    }
}
