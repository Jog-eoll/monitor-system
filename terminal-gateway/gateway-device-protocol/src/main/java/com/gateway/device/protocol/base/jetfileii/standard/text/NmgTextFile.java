package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileMagic;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.DateTime;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.TempHumidity;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.Wind;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.Builder;
import lombok.Data;

import java.util.Arrays;

/**
 * TEXT FILE 文本体模型 — 控制前缀 + 显示文本 + EOF + 注释。
 *
 * <p>纯文本体不含 NG 头或扩展块，直接用于 FileTransfer 上传/下载。</p>
 */
@Data
@Builder
public class NmgTextFile {


    /**
     * 控制前缀 (默认 38B)
     */
    private byte[] controlPrefix;
    /**
     * 显示文本区 (含控制符、CR、字体切换等)
     */
    private byte[] displayText;
    /**
     * 注释区 (128B)
     */
    private byte[] note;

    // ── 序列化 ────────────────────────────────────────

    public static byte[] buildDefaultNote() {
        byte[] note = new byte[128];
        byte[] text = "NoteNmg file version:v4.01".getBytes(ProtocolConstant.GB18030);
        System.arraycopy(text, 0, note, 0, Math.min(text.length, 128));
        Arrays.fill(note, text.length, 128, (byte) 0x20);
        return note;
    }

    // ── 辅助 ─────────────────────────────────────────

    /**
     * 从 Nmg 原始字节中提取可读文本。
     *
     * <p>自动跳过 NG 头 (若有)、控制前缀、字体切换序列、CR→\\n。</p>
     */
    public static String extractText(byte[] raw) {
        int off = 0;
        if (raw.length >= 2 && raw[0] == 'N' && raw[1] == 'G') {
            off = 18;
        } else if (raw.length >= FileMagic.TEXT_FILE_HEAD.length
                && raw[0] == FileMagic.TEXT_FILE_HEAD[0]
                && raw[1] == FileMagic.TEXT_FILE_HEAD[1]) {
            off = FileMagic.TEXT_FILE_HEAD.length;
        }

        int start = off;
        for (int i = off; i < raw.length - 1; i++) {
            if (raw[i] == 0x07 && (raw[i + 1] == '0' || raw[i + 1] == '1')) {
                start = i + 2;
                break;
            }
        }

        int end = raw.length;
        for (int i = start; i < raw.length; i++) {
            if (raw[i] == 0x04) {
                end = i;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < end) {
            int b = raw[i] & 0xFF;

            if (b == 0x1C && i + 1 < end) {
                i++;
                if (raw[i] == '/') i += 4;
                else i++;
                if (i < end && raw[i] == 0x1A) i += 2;
                continue;
            }

            if (b == 0x0B && i + 1 < end) {
                byte sub = raw[i + 1];
                String label = specialCharLabel(sub);
                sb.append(label != null ? "[" + label + "]" : "[\\x0B" + String.format("%02X", sub) + "]");
                i += 2;
                continue;
            }

            if (b == 0x0D) {
                sb.append('\n');
                i++;
                continue;
            }

            if (b >= 0x20 && b < 0x7F) {
                sb.append((char) b);
                i++;
                continue;
            }

            if (b >= 0xA1 && i + 1 < end) {
                sb.append(new String(raw, i, 2, ProtocolConstant.GB18030));
                i += 2;
                continue;
            }
            i++;
        }
        return sb.toString();
    }

    // ── 文本提取 ─────────────────────────────────────

    private static String specialCharLabel(byte subCode) {
        TempHumidity th = TempHumidity.ofCode(subCode);
        if (th != null) return th.getLabel();
        DateTime dt = DateTime.ofCode(subCode);
        if (dt != null) return dt.getLabel();
        Wind w = Wind.ofCode(subCode);
        return w != null ? w.getLabel() : null;
    }

    /**
     * 输出纯文本体 (控制前缀 + 显示文本 + EOF + 注释), 无 NG 头
     */
    public byte[] toBytes() {
        byte[] prefix = controlPrefix != null ? controlPrefix : new byte[0];
        byte[] text = displayText != null ? displayText : new byte[0];
        byte[] note0 = note != null ? note : buildDefaultNote();

        byte[] result = new byte[FileMagic.TEXT_FILE_HEAD.length + prefix.length + text.length + 1 + note0.length];
        int off = 0;
        System.arraycopy(FileMagic.TEXT_FILE_HEAD, 0, result, off, FileMagic.TEXT_FILE_HEAD.length);
        off += FileMagic.TEXT_FILE_HEAD.length;
        System.arraycopy(prefix, 0, result, off, prefix.length);
        off += prefix.length;
        System.arraycopy(text, 0, result, off, text.length);
        off += text.length;
        result[off++] = FileMagic.TEXT_FILE_EOF;
        System.arraycopy(note0, 0, result, off, note0.length);
        return result;
    }
}
