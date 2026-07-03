package com.gateway.device.protocol.base.jetfileii.standard.builder;

import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;

/**
 * 时间指令 (MainCMD=0x05) 便捷构建器。
 */
public final class TimeCommands {

    private TimeCommands() {
    }

    public static byte[] readTime() {
        return PacketBuilder.create(MainCmd.TIME, SubCmd.TIME_READ)
                .needReply().buildBytes();
    }

    public static byte[] setTime(byte[] timeData) {
        return PacketBuilder.create(MainCmd.TIME, SubCmd.TIME_WRITE)
                .needReply().data(timeData).buildBytes();
    }

    public static byte[] readTempHumidity() {
        return PacketBuilder.create(MainCmd.TIME, SubCmd.TIME_TEMP_HUMID)
                .needReply().buildBytes();
    }
}
