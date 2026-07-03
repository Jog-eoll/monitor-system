package com.gateway.device.protocol.base.jetfileii.standard.builder;

import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.model.DefaultSet;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.ProtocolConstant;

/**
 * 信息写入指令 (MainCMD=0x02) 便捷构建器。
 */
public final class WriteCommands {


    private WriteCommands() {
    }

    /**
     * TEXT FILE 写入 (0x0204)
     */
    public static byte[] writeTextFile(Partition partition, byte buzzer, String fileName,
                                       long totalSize, byte[] data, int packIndex, int totalPack) {
        byte[] arg = buildFileArg(partition, buzzer, fileName, totalSize, data, packIndex, totalPack);
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_TEXTFILE)
                .needReply().argWithLen(arg, 6).data(data).buildBytes();
    }

    /**
     * STRING FILE 写入 (0x0205)
     */
    public static byte[] writeStringFile(Partition partition, String fileName,
                                         long totalSize, byte[] data, int packIndex, int totalPack) {
        byte[] arg = buildFileArg(partition, (byte) 0, fileName, totalSize, data, packIndex, totalPack);
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_STRINGFILE)
                .needReply().argWithLen(arg, 6).data(data).buildBytes();
    }

    /**
     * PICTURE FILE 写入 (0x0206)
     */
    public static byte[] writePictureFile(Partition partition, String fileName,
                                          long totalSize, byte[] data, int packIndex, int totalPack) {
        byte[] arg = buildFileArg(partition, (byte) 0, fileName, totalSize, data, packIndex, totalPack);
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_PICTUREFILE)
                .needReply().argWithLen(arg, 6).data(data).buildBytes();
    }

    /**
     * ARRAY PICTURE FILE 写入 (0x0207)
     */
    public static byte[] writeArrayPicture(Partition partition, String fileName,
                                           long totalSize, byte[] data, int packIndex, int totalPack) {
        byte[] arg = buildFileArg(partition, (byte) 0, fileName, totalSize, data, packIndex, totalPack);
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_ARRAY_PICTURE)
                .needReply().argWithLen(arg, 6).data(data).buildBytes();
    }

    /**
     * 系统文件写入 (0x0202)
     */
    public static byte[] writeSysFile(String fileName, long totalSize,
                                      byte[] data, int packIndex, int totalPack) {
        byte[] arg = new byte[24];
        byte[] name = toFixedBytes(fileName, 12);
        System.arraycopy(name, 0, arg, 0, 12);
        LittleEndianByteBufUtils.writeUIntLE(arg, 12, totalSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 16, data != null ? data.length : 0);
        LittleEndianByteBufUtils.writeUShortLE(arg, 18, totalPack);
        LittleEndianByteBufUtils.writeUShortLE(arg, 20, packIndex);
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_SYSFILE)
                .needReply().argWithLen(arg, 6).data(data).buildBytes();
    }

    /**
     * 紧急消息写入 (0x0209)
     */
    public static byte[] writeEmergencyMsg(String text, int durationSec, boolean sound) {
        byte[] textBytes;
        try {
            textBytes = (text != null) ? text.getBytes(ProtocolConstant.GB18030) : new byte[0];
        } catch (Exception e) {
            textBytes = text.getBytes(ProtocolConstant.GB18030);
        }
        byte[] arg = new byte[4];
        LittleEndianByteBufUtils.writeUShortLE(arg, 0, durationSec);
        arg[2] = sound ? (byte) 1 : 0;
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_EMERGENCY)
                .needReply().argWithLen(arg, 1).data(textBytes).buildBytes();
    }

    /**
     * 写文件到指定路径 (0x0208)
     */
    public static byte[] writePathFile(String filePath, long totalSize,
                                       byte[] data, int packIndex, int totalPack) {
        byte[] path = toNullTerminated(filePath);
        byte[] arg = new byte[10 + path.length];
        LittleEndianByteBufUtils.writeUIntLE(arg, 0, totalSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 4, data != null ? data.length : 0);
        LittleEndianByteBufUtils.writeUShortLE(arg, 6, totalPack);
        LittleEndianByteBufUtils.writeUShortLE(arg, 8, packIndex);
        System.arraycopy(path, 0, arg, 10, path.length);
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_PATHFILE)
                .needReply().arg(arg).data(data).buildBytes();
    }

    /**
     * 写入默认显示样式 (0x020C)，传入完整的 DEFAULT_SET 结构 (52B)
     */
    public static byte[] writeDefaultStyle(DefaultSet defaultSet) {
        byte[] data = defaultSet != null ? defaultSet.toBytes() : new byte[52];
        return PacketBuilder.create(MainCmd.WRITE, SubCmd.WRITE_DEFAULT_STYLE)
                .needReply().data(data).buildBytes();
    }

    // ── 控制类写入 ────────────────────────────────────

    /**
     * 亮度调节 (0x0407)
     */
    public static byte[] writeBrightness(int levelPercent) {
        byte[] arg = new byte[4];
        arg[0] = (byte) Math.min(100, Math.max(0, levelPercent));
        arg[1] = (byte) 0xFF;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_BRIGHTNESS)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    // ── helper ───────────────────────────────────────

    private static byte[] buildFileArg(Partition partition, byte buzzer, String fileName,
                                       long totalSize, byte[] data, int packIndex, int totalPack) {
        byte[] arg = new byte[24];
        arg[0] = partition.getCode();
        arg[1] = buzzer;
        byte[] lbl = toFixedBytes(fileName, 12);
        System.arraycopy(lbl, 0, arg, 2, 12);
        LittleEndianByteBufUtils.writeUIntLE(arg, 14, totalSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 18, data != null ? data.length : 0);
        LittleEndianByteBufUtils.writeUShortLE(arg, 20, totalPack);
        LittleEndianByteBufUtils.writeUShortLE(arg, 22, packIndex);
        return arg;
    }

    private static byte[] toFixedBytes(String s, int len) {
        byte[] out = new byte[len];
        if (s != null) {
            byte[] src = s.getBytes(ProtocolConstant.GB18030);
            System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        }
        return out;
    }

    private static byte[] toNullTerminated(String s) {
        byte[] src = (s != null) ? s.getBytes(ProtocolConstant.GB18030) : new byte[0];
        byte[] out = new byte[src.length + 1];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }
}
