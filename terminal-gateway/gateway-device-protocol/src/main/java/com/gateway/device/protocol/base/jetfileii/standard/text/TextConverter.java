package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.common.constant.ProtocolConstant;
import org.apache.commons.lang3.StringUtils;

/**
 * 普通文本 → JetFileII TextFile 格式转换器。
 *
 * <p>将 UTF-8 文本转换为情报板可识别的 TextFile 格式。</p>
 */
public final class TextConverter {

    private TextConverter() {
    }

    /**
     * 将 UTF-8 编码的文本字符串转换为完整 TextFile 字节数组。
     *
     * @param utf8Text UTF-8 文本内容
     * @return 完整 TextFile 字节 (7B 文件头 + 文本数据 + 0x04), 文本为空返回 null
     */
    public static byte[] convert(String utf8Text) {
        if (StringUtils.isEmpty(utf8Text)) {
            return null;
        }
        TextFile tf = fromString(utf8Text);
        return tf.toFullBytes();
    }

    /**
     * 从 UTF-8 文本字符串构建 TextFile 对象。
     */
    public static TextFile fromString(String utf8Text) {
        byte[] gb18030Body = utf8Text.getBytes(ProtocolConstant.GB18030);
        return TextFile.builder()
                .header(TextFileHeader.createDefault())
                .textBody(gb18030Body)
                .build();
    }

    /**
     * 仅返回文本数据部分（不含 7B 文件头和 1B 文件尾），
     * 可直接作为通信报文的 Data 段。
     */
    public static byte[] toBodyBytes(TextFile tf) {
        if (tf == null) return null;
        byte[] body = tf.getTextBody();
        return body != null ? body : new byte[0];
    }
}
