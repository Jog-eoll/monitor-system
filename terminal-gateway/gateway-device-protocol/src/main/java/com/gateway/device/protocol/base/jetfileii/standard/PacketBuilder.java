package com.gateway.device.protocol.base.jetfileii.standard;

import com.gateway.device.protocol.base.jetfileii.standard.checksum.CrcCcittChecksum;
import com.gateway.device.protocol.base.jetfileii.standard.checksum.SumChecksum;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.command.SyncWord;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketHeader;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * JetFileII 报文构建器。
 *
 * <pre>{@code
 *   byte[] pkt = PacketBuilder.create(MAIN_TEST, SUB_TEST_CONNECT)
 *       .destAddr(1, 1).needReply()
 *       .buildBytes();
 * }</pre>
 */
public class PacketBuilder {

    private final byte mainCmd;
    private final byte subCmd;
    private byte argLen;
    private byte flag = ProtocolConst.FLAG_NEED_REPLY;
    ;
    private short sourceAddress;
    private short destAddress;
    private short packetSerial;
    private byte[] arg;
    private byte[] data;
    private boolean useCrc;

    private PacketBuilder(byte mainCmd, byte subCmd) {
        this.mainCmd = mainCmd;
        this.subCmd = subCmd;
    }

    public static PacketBuilder create(byte mainCmd, byte subCmd) {
        return new PacketBuilder(mainCmd, subCmd);
    }

    public static byte[] toBytes(PacketMessage msg, boolean crc) {
        PacketHeader h = msg.getHeader();
        byte[] hdr = h.toBytes();
        byte[] a = msg.getArg();
        byte[] d = msg.getData();

        // Arg 段必须补齐到 argLen * ARG_UNIT_SIZE 字节（协议硬规定）
        int expectedArgLen = (h.getArgLen() & 0xFF) * ProtocolConst.ARG_UNIT_SIZE;
        int actualArgLen = (a != null) ? a.length : 0;
        int total = 16 + expectedArgLen + (d != null ? d.length : 0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(total);
        try {
            bos.write(hdr);
            if (actualArgLen > 0) {
                bos.write(a);
                // 不足补齐 0x00
                for (int i = actualArgLen; i < expectedArgLen; i++) bos.write(0);
            } else if (expectedArgLen > 0) {
                for (int i = 0; i < expectedArgLen; i++) bos.write(0);
            }
            if (d != null) bos.write(d);
        } catch (IOException e) {
            throw new RuntimeException("序列化失败", e);
        }
        byte[] raw = bos.toByteArray();
        int checksum = crc
                ? CrcCcittChecksum.INSTANCE.compute(raw, 4, raw.length - 4)
                : SumChecksum.INSTANCE.compute(raw, 4, raw.length - 4);
        LittleEndianByteBufUtils.writeUShortLE(raw, 2, checksum);
        return raw;
    }

    public static byte[] toBytes(PacketMessage msg) {
        return toBytes(msg, false);
    }

    public PacketBuilder srcAddr(int addr) {
        this.sourceAddress = (short) addr;
        return this;
    }

    public PacketBuilder destAddr(int gg, int uu) {
        this.destAddress = (short) (((gg & 0xFF) << 8) | (uu & 0xFF));
        return this;
    }

    public PacketBuilder broadcast() {
        return destAddr(0, 0);
    }

    public PacketBuilder serial(int s) {
        this.packetSerial = (short) s;
        return this;
    }

    public PacketBuilder needReply() {
        this.flag = ProtocolConst.FLAG_NEED_REPLY;
        return this;
    }

    public PacketBuilder noReply() {
        this.flag = ProtocolConst.FLAG_NO_REPLY;
        return this;
    }

    public PacketBuilder withCrc() {
        this.useCrc = true;
        return this;
    }

    public PacketBuilder arg(byte[] arg) {
        this.arg = arg;
        this.argLen = (byte) ((arg != null && arg.length > 0)
                ? (arg.length + 3) / ProtocolConst.ARG_UNIT_SIZE : 0);
        return this;
    }

    public PacketBuilder argWithLen(byte[] arg, int argLen) {
        this.arg = arg;
        this.argLen = (byte) argLen;
        return this;
    }

    public PacketBuilder data(byte[] data) {
        this.data = data;
        return this;
    }

    // ════════════════════════════════════════════════════
    // 静态工具
    // ════════════════════════════════════════════════════

    public PacketMessage build() {
        return PacketMessage.builder()
                .header(PacketHeader.builder()
                        .synCode(useCrc ? SyncWord.CRC_SEND : SyncWord.NORMAL_SEND)
                        .dataLen((short) (data != null ? data.length : 0))
                        .sourceAddress(sourceAddress).destAddress(destAddress)
                        .packetSerial(packetSerial).mainCmd(mainCmd).subCmd(subCmd)
                        .argLen(argLen).flag(flag).build())
                .arg(arg).data(data).build();
    }

    public byte[] buildBytes() {
        return toBytes(build(), useCrc);
    }
}
