package com.gateway.device.protocol.base.jetfileii.standard.command;

/**
 * JetFileII 包头固定字段常量。
 */
public final class ProtocolConst {

    /**
     * 广播地址
     */
    public static final byte ADDR_BROADCAST = 0x00;
    /**
     * 需要回送
     */
    public static final byte FLAG_NEED_REPLY = 0x00;
    /**
     * 无须回送
     */
    public static final byte FLAG_NO_REPLY = 0x01;
    /**
     * 回送帧中 Data 为 2 字节状态码
     */
    public static final byte FLAG_STATUS_CODE = 0x01;
    /**
     * 包头固定长度
     */
    public static final int HEADER_SIZE = 16;
    /**
     * 参数段每单元 4 字节
     */
    public static final int ARG_UNIT_SIZE = 4;
    /**
     * 默认分包大小
     */
    public static final int DEFAULT_PACK_SIZE = 1024;

    private ProtocolConst() {
    }
}
