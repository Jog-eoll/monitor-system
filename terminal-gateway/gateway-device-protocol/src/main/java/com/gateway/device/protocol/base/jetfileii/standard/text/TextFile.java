package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileMagic;
import lombok.Builder;
import lombok.Data;

/**
 * JetFileII TextFile 完整结构（文本显示文件）。
 *
 * <p>对应文档第 14 节:
 * TextFileHeader(7B) | TextBody(NB) | EOF(1B, 0x04)
 * </p>
 */
@Data
@Builder
public class TextFile {

    /**
     * 7 字节文件头
     */
    private TextFileHeader header;
    /**
     * 显示内容 + 控制字符（GB18030 编码）
     */
    private byte[] textBody;

    /**
     * 获取完整的文本数据（含文件头和文件尾）
     */
    public byte[] toFullBytes() {
        byte[] head = header != null ? header.getHead() : new byte[0];
        byte[] body = textBody != null ? textBody : new byte[0];
        byte[] result = new byte[head.length + body.length + 1];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(body, 0, result, head.length, body.length);
        result[result.length - 1] = FileMagic.TEXT_FILE_EOF;
        return result;
    }
}
