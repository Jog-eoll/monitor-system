package com.gateway.device.protocol.base.jetfileii.standard.builder;

import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.ProtocolConstant;

import java.nio.ByteBuffer;

/**
 * 信息读取指令 (MainCMD=0x01) 便捷构建器。
 */
public final class ReadCommands {

    private ReadCommands() {
    }

    /**
     * 系统参数读取 (0x010A)
     */
    public static byte[] readSysParam() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_SYS_PARAM)
                .needReply().buildBytes();
    }

    /**
     * 系统当前状态读取 (0x010B)
     */
    public static byte[] readSysStatus() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_SYS_STATUS)
                .needReply().buildBytes();
    }

    /**
     * 系统信息读取 (0x0112)
     */
    public static byte[] readSysInfo() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_SYSINFO)
                .needReply().buildBytes();
    }

    /**
     * 读取默认显示样式 (0x0110)
     */
    public static byte[] readDefaultStyle() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_DEFAULT_STYLE)
                .needReply().buildBytes();
    }

    /**
     * 亮度信息读取 (0x0116)
     */
    public static byte[] readBrightness() {
        byte[] arg = new byte[4];
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_BRIGHTNESS)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    /**
     * 读取主板识别 ID (0x011B)
     */
    public static byte[] readBoardId() {
        byte[] arg = new byte[4];
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_BOARD_ID)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    /**
     * 读取硬件详细信息 (0x0120)
     */
    public static byte[] readHwDetail() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_HW_DETAIL)
                .needReply().buildBytes();
    }

    /**
     * 读取错误日志 (0x0113)
     */
    public static byte[] readErrorLog() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_ERRLOG)
                .needReply().buildBytes();
    }

    /**
     * 查询 CPU 升级状态 (0x010F)
     */
    public static byte[] readCpuUpgradeStatus() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_CPU_UPGRADE)
                .needReply().buildBytes();
    }

    /**
     * 读取 Flash 写状态 (0x010D)
     */
    public static byte[] readFlashStatus() {
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_FLASH_STATUS)
                .needReply().buildBytes();
    }

    // ── 文件读取 ──────────────────────────────────────

    /**
     * TEXT FILE 读取 (0x0104)
     */
    public static byte[] readTextFile(Partition partition, String fileName, int packSize, int packIndex) {
        return buildLabelRead(SubCmd.READ_TEXTFILE, partition, fileName, packSize, packIndex);
    }

    /**
     * STRING FILE 读取 (0x0105)
     */
    public static byte[] readStringFile(Partition partition, String fileName, int packSize, int packIndex) {
        return buildLabelRead(SubCmd.READ_STRINGFILE, partition, fileName, packSize, packIndex);
    }

    /**
     * PICTURE FILE 读取 (0x0106)
     */
    public static byte[] readPictureFile(Partition partition, String fileName, int packSize, int packIndex) {
        return buildLabelRead(SubCmd.READ_PICTUREFILE, partition, fileName, packSize, packIndex);
    }

    /**
     * ARRAY PICTURE FILE 读取 (0x0107)
     */
    public static byte[] readArrayPicture(Partition partition, String fileName, int packSize, int packIndex) {
        return buildLabelRead(SubCmd.READ_ARRAY_PICTURE, partition, fileName, packSize, packIndex);
    }

    /**
     * 系统文件读取 (0x0102)
     */
    public static byte[] readSysFile(String fileName, int packSize, int packIndex) {
        byte[] name = toFixedBytes(fileName, 12);
        ByteBuffer arg = LittleEndianByteBufUtils.newLEBuffer(16);
        arg.put(name);
        arg.putShort((short) packSize);
        arg.putShort((short) packIndex);
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_SYSFILE)
                .needReply().argWithLen(arg.array(), 4).buildBytes();
    }

    /**
     * 读取指定路径文件 (0x0108)
     */
    public static byte[] readPathFile(String filePath, int packSize, int packIndex) {
        byte[] path = toNullTerminated(filePath);
        byte[] arg = new byte[4 + path.length];
        LittleEndianByteBufUtils.writeUShortLE(arg, 0, packSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 2, packIndex);
        System.arraycopy(path, 0, arg, 4, path.length);
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_PATHFILE)
                .needReply().arg(arg).buildBytes();
    }

    /**
     * 播放日志读取 (0x0109)
     */
    public static byte[] readPlayLog(int packSize, int packIndex) {
        byte[] arg = new byte[4];
        LittleEndianByteBufUtils.writeUShortLE(arg, 0, packSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 2, packIndex);
        return PacketBuilder.create(MainCmd.READ, SubCmd.READ_PLAYLOG)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    // ── 跨大类常用读取 ────────────────────────────────

    /**
     * 读取开关机状态 (0x0405)
     */
    public static byte[] readSwitchStatus() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_SWITCH_STATUS)
                .needReply().buildBytes();
    }

    /**
     * 读取以太网检测设置 (0x0409)
     */
    public static byte[] readEthDetect() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_ETH_DETECT_READ)
                .needReply().buildBytes();
    }

    // ── helper ───────────────────────────────────────

    private static byte[] buildLabelRead(byte subCmd, Partition partition, String fileName,
                                         int packSize, int packIndex) {
        byte[] arg = new byte[20];
        arg[0] = partition.getCode();
        byte[] lbl = toFixedBytes(fileName, 12);
        System.arraycopy(lbl, 0, arg, 4, 12);
        LittleEndianByteBufUtils.writeUShortLE(arg, 16, packSize);
        LittleEndianByteBufUtils.writeUShortLE(arg, 18, packIndex);
        return PacketBuilder.create(MainCmd.READ, subCmd)
                .needReply().argWithLen(arg, 5).buildBytes();
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
