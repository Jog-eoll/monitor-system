package com.gateway.device.protocol.base.jetfileii.standard.command;

/**
 * JetFileII 文件魔术头。
 */
public final class FileMagic {

    /**
     * TEXT FILE 文件头: 0x01 'Z' '0' '0' 0x02 'A' 'A'
     */
    public static final byte[] TEXT_FILE_HEAD = {0x01, 'Z', '0', '0', 0x02, 'A', 'A'};
    /**
     * TEXT FILE 文件尾
     */
    public static final byte TEXT_FILE_EOF = 0x04;
    /**
     * ARRAY PICTURE FILE 文件头: 0x01 'Z' '0' '0' 0x02 'C' 'C' 'P' 'D'
     */
    public static final byte[] ARRAY_PIC_HEAD = {0x01, 'Z', '0', '0', 0x02, 'C', 'C', 'P', 'D'};

    private FileMagic() {
    }
}
