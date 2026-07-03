package com.gateway.device.protocol.base.jetfileii.standard.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * JetFileII 分区号（盘符）。
 *
 * <p>协议层使用 {@link #getCode()} (byte 0-5)，
 * 路径层使用 {@link #getDrive()} (char 'A'-'F')。</p>
 */
@Getter
@AllArgsConstructor
public enum Partition {

    A((byte) 0, 'A', "A盘"),
    C((byte) 2, 'C', "C盘"),
    D((byte) 3, 'D', "D盘(默认)"),
    E((byte) 4, 'E', "E盘"),
    F((byte) 5, 'F', "F盘");

    /**
     * 协议层 wire byte（0-based 索引）
     */
    private final byte code;
    /**
     * 路径层盘符字母
     */
    private final char drive;
    /**
     * 中文标签
     */
    private final String label;

    /**
     * 按盘符字母查找
     */
    public static Partition ofDrive(char drive) {
        for (Partition p : values()) if (p.drive == drive) return p;
        return null;
    }

    /**
     * 按协议 code 查找
     */
    public static Partition ofCode(int code) {
        for (Partition p : values()) if (p.code == (byte) code) return p;
        return null;
    }
}
