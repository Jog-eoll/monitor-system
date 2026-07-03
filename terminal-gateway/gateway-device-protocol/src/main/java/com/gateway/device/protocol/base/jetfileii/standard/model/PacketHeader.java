package com.gateway.device.protocol.base.jetfileii.standard.model;

import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import lombok.Builder;
import lombok.Data;

/**
 * JetFileII 协议 — 固定 16 字节包头。
 *
 * <pre>
 *   偏移  大小  字段
 *   0     2B    synCode        同步魔数: byte[0]=0x55, byte[1]=0xA7/0xA3(发)/0xA8/0xA4(回)
 *   2     2B    checkSum       校验和(从偏移4逐字节累加至末尾，截低16位)
 *   4     2B    dataLen        Data 字段字节数 (LE)
 *   6     2B    sourceAddress  源地址 (LE)
 *   8     2B    destAddress    目的地址: 高字节=GG(组), 低字节=UU(单元) (LE)
 *   10    2B    packetSerial   包序号 (LE)
 *   12    1B    mainCmd        大类命令
 *   13    1B    subCmd         小类命令
 *   14    1B    argLen         参数长度(单位=4B)
 *   15    1B    flag           发送:0=需回送/1=不用; 回送:1=Data是2B状态码/0=数据
 * </pre>
 *
 * <p>注意: synCode 是固定魔数(0x55+标志字节)，不是 LE 数值，其余多字节字段均为 LE。</p>
 */
@Data
@Builder
public class PacketHeader {

    private short synCode;
    private short checkSum;
    private short dataLen;
    private short sourceAddress;
    private short destAddress;
    private short packetSerial;
    private byte mainCmd;
    private byte subCmd;
    private byte argLen;
    private byte flag;

    /**
     * 从 16 字节解析包头
     */
    public static PacketHeader fromBytes(byte[] data, int offset) {
        // synCode: 固定魔数 byte[0]=0x55, byte[1]=标志
        int syn = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        return PacketHeader.builder()
                .synCode((short) syn)
                .checkSum((short) LittleEndianByteBufUtils.readUShortLE(data, offset + 2))
                .dataLen((short) LittleEndianByteBufUtils.readUShortLE(data, offset + 4))
                .sourceAddress((short) LittleEndianByteBufUtils.readUShortLE(data, offset + 6))
                .destAddress((short) LittleEndianByteBufUtils.readUShortLE(data, offset + 8))
                .packetSerial((short) LittleEndianByteBufUtils.readUShortLE(data, offset + 10))
                .mainCmd(data[offset + 12])
                .subCmd(data[offset + 13])
                .argLen(data[offset + 14])
                .flag(data[offset + 15])
                .build();
    }

    /**
     * 序列化为 16 字节（checkSum 填 0，由外层计算）
     */
    public byte[] toBytes() {
        byte[] buf = new byte[16];
        // synCode: 固定魔数 0x55 + 标志字节（非LE，字节序固定）
        buf[0] = (byte) 0x55;
        buf[1] = (byte) (synCode & 0xFF);
        // 其余多字节字段均为 LE
        LittleEndianByteBufUtils.writeUShortLE(buf, 2, checkSum);
        LittleEndianByteBufUtils.writeUShortLE(buf, 4, dataLen);
        LittleEndianByteBufUtils.writeUShortLE(buf, 6, sourceAddress);
        LittleEndianByteBufUtils.writeUShortLE(buf, 8, destAddress);
        LittleEndianByteBufUtils.writeUShortLE(buf, 10, packetSerial);
        buf[12] = mainCmd;
        buf[13] = subCmd;
        buf[14] = argLen;
        buf[15] = flag;
        return buf;
    }

    public byte getGG() {
        return (byte) ((destAddress >> 8) & 0xFF);
    }

    public byte getUU() {
        return (byte) (destAddress & 0xFF);
    }
}
