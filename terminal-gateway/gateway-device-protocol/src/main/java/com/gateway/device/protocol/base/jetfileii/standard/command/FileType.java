package com.gateway.device.protocol.base.jetfileii.standard.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * JetFileII 文件类型标识。
 */
@Getter
@AllArgsConstructor
public enum FileType {

    PICTURE((byte) 'P', "PICTURE FILE"),
    FLW((byte) 'F', "FLW FILE"),
    STRING((byte) 'S', "STRING FILE"),
    /**
     * (自有格式)NMG[文件编辑]
     */
    TEXT((byte) 'T', "TEXT FILE"),
    /**
     * (自有格式)PMG[文件编辑II]
     */
    ARRAY_PICTURE((byte) 'A', "ARRAY PICTURE FILE"),
    /**
     * (自有格式)QST[新文件编辑]
     */
    ARRAY_QST((byte) 'Q', "QST FILE");

    private final byte code;
    private final String label;

    /**
     * 构建完整设备路径，如 {@code D:\T\default.nmg}
     */
    public String resolvePath(Partition partition, String fileName) {
        return partition.getDrive() + ":\\" + (char) this.code + "\\" + fileName;
    }

    /**
     * 默认 D 分区
     */
    public String resolvePath(String fileName) {
        return resolvePath(Partition.D, fileName);
    }
}
