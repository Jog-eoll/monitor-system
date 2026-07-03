package com.gateway.device.protocol.base.jetfileii.standard;

import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SyncWord;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketHeader;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;

import java.util.Arrays;

/**
 * JetFileII 协议报文解析器。
 */
public final class JetFileIIParser {

    private JetFileIIParser() {
    }

    /**
     * 从原始字节解析报文。
     *
     * @param raw 原始字节（小端序）
     * @return PacketMessage; 同步头不匹配或长度不足返回 null
     */
    public static PacketMessage parse(byte[] raw) {
        if (raw == null || raw.length < ProtocolConst.HEADER_SIZE) return null;

        // synCode: 固定魔数 [0x55][flag], 非 LE
        int syn = ((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF);
        if (!SyncWord.isSend((short) syn) && !SyncWord.isReply((short) syn)) {
            return null;
        }

        PacketHeader header = PacketHeader.fromBytes(raw, 0);
        int offset = ProtocolConst.HEADER_SIZE;

        byte[] arg = null;
        int argBytes = (header.getArgLen() & 0xFF) * ProtocolConst.ARG_UNIT_SIZE;
        if (argBytes > 0 && raw.length >= offset + argBytes) {
            arg = Arrays.copyOfRange(raw, offset, offset + argBytes);
            offset += argBytes;
        }

        byte[] data = null;
        int dataBytes = header.getDataLen() & 0xFFFF;
        if (dataBytes > 0 && raw.length >= offset + dataBytes) {
            data = Arrays.copyOfRange(raw, offset, offset + dataBytes);
        }

        return PacketMessage.builder().header(header).arg(arg).data(data).build();
    }

    public static boolean isReplyPacket(PacketMessage pkt) {
        return pkt != null && pkt.getHeader() != null && pkt.isReply();
    }

    public static boolean isPictureWrite(PacketMessage pkt) {
        if (pkt == null || pkt.getHeader() == null) return false;
        byte m = pkt.getHeader().getMainCmd();
        byte s = pkt.getHeader().getSubCmd();
        return m == MainCmd.WRITE
                && (s == SubCmd.WRITE_PICTUREFILE
                || s == SubCmd.WRITE_ARRAY_PICTURE);
    }

    public static boolean isPictureReadReply(PacketMessage pkt) {
        if (pkt == null || pkt.getHeader() == null) return false;
        byte m = pkt.getHeader().getMainCmd();
        byte s = pkt.getHeader().getSubCmd();
        return m == MainCmd.READ
                && (s == SubCmd.READ_PICTUREFILE
                || s == SubCmd.READ_ARRAY_PICTURE)
                && pkt.getHeader().getFlag() == 0;
    }
}
