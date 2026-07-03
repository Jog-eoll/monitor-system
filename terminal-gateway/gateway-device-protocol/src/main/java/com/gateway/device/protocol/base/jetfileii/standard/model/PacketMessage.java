package com.gateway.device.protocol.base.jetfileii.standard.model;

import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.command.SyncWord;
import lombok.Builder;
import lombok.Data;

/**
 * JetFileII 协议完整报文。
 *
 * <p>结构: PacketHeader(16B) + Arg(argLen × 4B) + Data(dataLen B)</p>
 */
@Data
@Builder
public class PacketMessage {

    private PacketHeader header;
    private byte[] arg;
    private byte[] data;

    public int getArgBytes() {
        return (header.getArgLen() & 0xFF) * ProtocolConst.ARG_UNIT_SIZE;
    }

    public int getDataBytes() {
        return header.getDataLen() & 0xFFFF;
    }

    public boolean isReply() {
        return SyncWord.isReply(header.getSynCode());
    }

    /**
     * 回送包中是否为状态码（非数据）
     */
    public boolean isStatusReply() {
        return isReply() && header.getFlag() == ProtocolConst.FLAG_STATUS_CODE;
    }

    /**
     * 获取回送状态码
     */
    public short getStatusCode() {
        if (isStatusReply() && data != null && data.length >= 2) {
            return (short) ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8));
        }
        return 0;
    }
}
