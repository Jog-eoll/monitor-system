package com.gateway.device.protocol.base.jetfileii.standard.command;

/**
 * JetFileII 同步字。
 */
public final class SyncWord {

    public static final short NORMAL_SEND = (short) 0x55A7;
    public static final short CRC_SEND = (short) 0x55A3;
    public static final short NORMAL_REPLY = (short) 0x55A8;
    public static final short CRC_REPLY = (short) 0x55A4;

    private SyncWord() {
    }

    public static boolean isReply(short sync) {
        return sync == NORMAL_REPLY || sync == CRC_REPLY;
    }

    public static boolean isSend(short sync) {
        return sync == NORMAL_SEND || sync == CRC_SEND;
    }
}
