package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileMagic;
import lombok.Builder;
import lombok.Data;

/**
 * JetFileII TextFile 文件头 — 固定 7 字节。
 *
 * <p>对应文档第 14.1 节:
 * 0x51 'Z' '0' '0' 0x53 'A' 'X'
 * </p>
 */
@Data
@Builder
public class TextFileHeader {

    /**
     * 固定 7 字节文件头
     */
    private byte[] head;

    public static TextFileHeader createDefault() {
        return TextFileHeader.builder()
                .head(FileMagic.TEXT_FILE_HEAD.clone())
                .build();
    }
}
