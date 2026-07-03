package com.gateway.device.protocol.base.jetfileii.standard.builder;

import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.common.constant.ProtocolConstant;

/**
 * 播放控制指令 (MainCMD=0x06) 便捷构建器。
 */
public final class DisplayCommands {

    private DisplayCommands() {
    }

    public static byte[] replayList() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_REPLAY_LIST)
                .needReply().buildBytes();
    }

    public static byte[] replayCurrent() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_REPLAY_CUR)
                .needReply().buildBytes();
    }

    public static byte[] pause() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_PAUSE)
                .needReply().buildBytes();
    }

    public static byte[] resume() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_RESUME)
                .needReply().buildBytes();
    }

    public static byte[] next() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_NEXT)
                .needReply().buildBytes();
    }

    public static byte[] prev() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_PREV)
                .needReply().buildBytes();
    }

    public static byte[] fastForward() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_FF)
                .needReply().buildBytes();
    }

    public static byte[] rewind() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_REW)
                .needReply().buildBytes();
    }

    public static byte[] nextFrame() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_NEXT_FRAME)
                .needReply().buildBytes();
    }

    public static byte[] buzzer(boolean on) {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_BUZZER)
                .needReply().data(new byte[]{on ? (byte) 1 : 0}).buildBytes();
    }

    /**
     * 开始倒计时/正计时 (0x0611)
     */
    public static byte[] timerStart(int day, int hour, int minute, int second) {
        byte[] data = new byte[]{
                (byte) day, (byte) hour, (byte) minute, (byte) second
        };
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_TIMER_START)
                .needReply().data(data).buildBytes();
    }

    /**
     * 停止倒计时/正计时 (0x0612)
     */
    public static byte[] timerStop() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_TIMER_STOP)
                .needReply().buildBytes();
    }

    /**
     * 暂停倒计时/正计时 (0x0613)
     */
    public static byte[] timerPause() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_TIMER_PAUSE)
                .needReply().buildBytes();
    }

    /**
     * 继续倒计时/正计时 (0x0614)
     */
    public static byte[] timerResume() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_TIMER_RESUME)
                .needReply().buildBytes();
    }

    public static byte[] readVolume() {
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_VOLUME_READ)
                .needReply().buildBytes();
    }

    /**
     * 调节系统音量 (0x061B), level 0-100
     */
    public static byte[] volumeSet(int level) {
        byte[] data = new byte[]{(byte) Math.min(100, Math.max(0, level))};
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_VOLUME_SET)
                .needReply().data(data).buildBytes();
    }

    public static byte[] priorityPlay(Partition partition, FileType fileType, String fileName) {
        byte[] data = new byte[14];
        data[0] = partition.getCode();
        data[1] = fileType.getCode();
        if (fileName != null) {
            byte[] name = fileName.getBytes(ProtocolConstant.GB18030);
            System.arraycopy(name, 0, data, 2, Math.min(name.length, 12));
        }
        return PacketBuilder.create(MainCmd.DISPLAY, SubCmd.DISP_PRIORITY)
                .needReply().data(data).buildBytes();
    }
}
