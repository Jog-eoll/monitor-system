package com.gateway.device.protocol.base.jetfileii.standard.builder;

import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;

/**
 * 系统操作指令 (MainCMD=0x04) 便捷构建器。
 */
public final class ControlCommands {

    private ControlCommands() {
    }

    public static byte[] resetHot() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_RESET_HOT)
                .needReply().buildBytes();
    }

    public static byte[] resetCold() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_RESET_COLD)
                .needReply().buildBytes();
    }

    public static byte[] blackScreenOn() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_BLACK_ON)
                .needReply().buildBytes();
    }

    public static byte[] blackScreenOff() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_BLACK_OFF)
                .needReply().buildBytes();
    }

    public static byte[] powerOff(boolean showMsg) {
        byte[] arg = new byte[4];
        arg[0] = showMsg ? (byte) 0 : (byte) 1;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_POWER_OFF)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    public static byte[] powerOn() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_POWER_ON)
                .needReply().buildBytes();
    }

    public static byte[] brightness(int levelPercent) {
        byte[] arg = new byte[4];
        arg[0] = (byte) Math.min(100, Math.max(0, levelPercent));
        arg[1] = (byte) 0xFF;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_BRIGHTNESS)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    public static byte[] setEthDetect(boolean enable, int intervalMin) {
        byte[] arg = new byte[4];
        arg[0] = enable ? (byte) 1 : 0;
        arg[1] = (byte) intervalMin;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_ETH_DETECT_SET)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    public static byte[] clearAllFiles() {
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_CLEAR_PLAY)
                .needReply().buildBytes();
    }

    public static byte[] xgArrow(boolean show) {
        byte[] arg = new byte[4];
        arg[0] = show ? (byte) 1 : 0;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_XG_ARROW_CTL)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    public static byte[] voice(boolean play) {
        byte[] arg = new byte[4];
        arg[0] = play ? (byte) 1 : 0;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_VOICE)
                .needReply().argWithLen(arg, 1).buildBytes();
    }

    public static byte[] fan(boolean on) {
        byte[] arg = new byte[4];
        arg[1] = on ? (byte) 1 : 0;
        return PacketBuilder.create(MainCmd.CONTROL, SubCmd.CTL_FAN)
                .needReply().argWithLen(arg, 1).buildBytes();
    }
}
