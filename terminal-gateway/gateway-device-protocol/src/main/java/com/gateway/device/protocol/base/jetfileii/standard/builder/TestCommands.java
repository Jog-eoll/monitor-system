package com.gateway.device.protocol.base.jetfileii.standard.builder;

import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

/**
 * 测试指令 (MainCMD=0x03) 便捷构建器。
 */
public final class TestCommands {

    private TestCommands() {
    }

    /**
     * 连接测试 (0x0301)
     */
    public static byte[] connectionTest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_CONNECT)
                .needReply().buildBytes();
    }

    public static byte[] autoTest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_AUTO)
                .noReply().buildBytes();
    }

    public static byte[] allWhite() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_ALL_WHITE)
                .noReply().buildBytes();
    }

    public static byte[] allRed() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_ALL_RED)
                .noReply().buildBytes();
    }

    public static byte[] allGreen() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_ALL_GREEN)
                .noReply().buildBytes();
    }

    public static byte[] allBlue() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_ALL_BLUE)
                .noReply().buildBytes();
    }

    public static byte[] hScan() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_H_SCAN)
                .noReply().buildBytes();
    }

    public static byte[] vScan() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_V_SCAN)
                .noReply().buildBytes();
    }

    public static byte[] endTest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_END)
                .needReply().buildBytes();
    }

    public static byte[] grayTest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_GRAY)
                .noReply().buildBytes();
    }

    public static byte[] colorTest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_COLOR)
                .noReply().buildBytes();
    }

    /**
     * 指定区域测试 (0x030D)
     */
    public static byte[] regionTest(int startX, int startY, int width, int height) {
        byte[] arg = new byte[8];
        LittleEndianByteBufUtils.writeUShortLE(arg, 0, startX);
        LittleEndianByteBufUtils.writeUShortLE(arg, 2, startY);
        LittleEndianByteBufUtils.writeUShortLE(arg, 4, width);
        LittleEndianByteBufUtils.writeUShortLE(arg, 6, height);
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_REGION)
                .needReply().argWithLen(arg, 2).buildBytes();
    }

    public static byte[] selfTest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_SELF_CHECK)
                .needReply().buildBytes();
    }

    public static byte[] showPosition() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_POSITION)
                .noReply().buildBytes();
    }
}
