package com.gateway.device.protocol.base.jetfileii.standard.transfer;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileMagic;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

import java.util.Arrays;

/**
 * JetFileII 文件数据格式解析工具 — 独立于传输层。
 *
 * <p>处理 NMG/TEXT FILE/ARRAY PICTURE 的文件体格式，不涉及协议通信。</p>
 */
public final class FileDataParser {

    private FileDataParser() {
    }

    /**
     * 从 Nmg 数据中剥离 NG 封装头 (18B)，按 dataSize 截取纯文本体。
     */
    public static byte[] stripToBody(byte[] data) {
        if (data.length >= 18 && data[0] == 'N' && data[1] == 'G') {
            int dataSize = LittleEndianByteBufUtils.readUShortLE(data, 6);
            int bodyLen = Math.min(dataSize, data.length - 18);
            byte[] body = new byte[bodyLen];
            System.arraycopy(data, 18, body, 0, bodyLen);
            return body;
        }
        return data;
    }

    /**
     * 规范化 TEXT FILE body。
     *
     * @param keepHeader true=保留 HEAD+EOF, false=剥离为纯内容
     */
    public static byte[] normalizeTextFile(byte[] body, boolean keepHeader) {
        if (body.length < 7) return body;
        byte[] head = FileMagic.TEXT_FILE_HEAD;
        boolean headAt0 = matchesAt(body, 0, head);
        boolean headAt7 = matchesAt(body, head.length, head);
        boolean hasDoubleHead = headAt0 && headAt7;

        if (keepHeader) {
            if (!hasDoubleHead) return body;
            int start = head.length;
            int end = body.length;
            if (end > start && body[end - 1] == FileMagic.TEXT_FILE_EOF) end--;
            return Arrays.copyOfRange(body, start, end);
        } else {
            int start = 0;
            while (start + head.length <= body.length && matchesAt(body, start, head)) {
                start += head.length;
            }
            int end = body.length;
            int headCount = start / head.length;
            for (int i = 0; i < headCount && end > start
                    && body[end - 1] == FileMagic.TEXT_FILE_EOF; i++) {
                end--;
            }
            if (start == 0 && end == body.length) return body;
            return Arrays.copyOfRange(body, start, end);
        }
    }

    /**
     * 重建 NG 封装头 (18B)，宽高从 ARRAY PICTURE FILE 头解析。
     */
    public static byte[] wrapWithNG(byte[] body) {
        int width = LittleEndianByteBufUtils.readUShortLE(body, 11);
        int height = LittleEndianByteBufUtils.readUShortLE(body, 13);
        return wrapWithNG(body, width, height);
    }

    /**
     * 重建 NG 封装头 (18B)。
     *
     * <pre>
     *   "NG" (2B) | width (2B LE) | height (2B LE) | bodySize (4B LE) | reserved (8B)
     * </pre>
     */
    public static byte[] wrapWithNG(byte[] body, int width, int height) {
        byte[] out = new byte[18 + body.length];
        out[0] = 'N';
        out[1] = 'G';
        LittleEndianByteBufUtils.writeUShortLE(out, 2, width);
        LittleEndianByteBufUtils.writeUShortLE(out, 4, height);
        LittleEndianByteBufUtils.writeUIntLE(out, 6, body.length);
        out[12] = 1;
        System.arraycopy(body, 0, out, 18, body.length);
        return out;
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] pattern) {
        if (offset + pattern.length > data.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) return false;
        }
        return true;
    }
}
