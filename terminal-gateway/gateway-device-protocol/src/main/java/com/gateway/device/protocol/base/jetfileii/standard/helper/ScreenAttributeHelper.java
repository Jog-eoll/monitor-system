package com.gateway.device.protocol.base.jetfileii.standard.helper;

import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;

import java.util.Arrays;

/**
 * JetFileII MULTI_WIN 屏体属性读写工具 —— 提取自 {@code JetFileIIScreenAttributeHandler}。
 *
 * <p>MULTI_WIN 数据结构为 88 字节固定长度，宽高为 uint16 LE 位于 data 末尾区域。
 * 通过 READ_MULTI_WIN(0x0118) 读取、WRITE_MULTI_WIN(0x020F) 写入。</p>
 */
public final class ScreenAttributeHelper {

    /**
     * MULTI_WIN 数据结构固定长度
     */
    public static final int DATA_SIZE = 88;
    /**
     * 宽度在 data 中的偏移（uint16 LE）
     */
    public static final int WIDTH_OFFSET = 78;
    /**
     * 高度在 data 中的偏移（uint16 LE）
     */
    public static final int HEIGHT_OFFSET = 80;
    /**
     * 默认操作的窗口索引
     */
    public static final int DEFAULT_WINDOW_INDEX = 1;

    private ScreenAttributeHelper() {
    }

    // ════════════════════════════════════════════════════
    // 请求构建
    // ════════════════════════════════════════════════════

    /**
     * 构建 READ_MULTI_WIN 请求（默认窗口索引）
     */
    public static JetFileIIRequest buildReadRequest(int gg, int uu) {
        return buildReadRequest(gg, uu, DEFAULT_WINDOW_INDEX);
    }

    /**
     * 构建 READ_MULTI_WIN 请求
     */
    public static JetFileIIRequest buildReadRequest(int gg, int uu, int windowIndex) {
        return JetFileIIRequest.builder()
                .mainCmd(MainCmd.READ).subCmd(SubCmd.READ_MULTI_WIN)
                .arg(LittleEndianByteBufUtils.intToLE(windowIndex))
                .needReply(true).destGg(gg).destUu(uu)
                .build();
    }

    /**
     * 构建 WRITE_MULTI_WIN 请求
     */
    public static JetFileIIRequest buildWriteRequest(int gg, int uu, byte[] data, int windowIndex) {
        return JetFileIIRequest.builder()
                .mainCmd(MainCmd.WRITE).subCmd(SubCmd.WRITE_MULTI_WIN)
                .arg(LittleEndianByteBufUtils.intToLE(windowIndex))
                .data(data).needReply(true).destGg(gg).destUu(uu)
                .build();
    }

    // ════════════════════════════════════════════════════
    // 数据读写
    // ════════════════════════════════════════════════════

    /**
     * 校验 MULTI_WIN 响应数据有效性
     */
    public static boolean isValidData(byte[] data) {
        return data != null && data.length == DATA_SIZE;
    }

    /**
     * 从 data 读取宽度
     */
    public static int readWidth(byte[] data) {
        return LittleEndianByteBufUtils.readUShortLE(data, WIDTH_OFFSET);
    }

    /**
     * 从 data 读取高度
     */
    public static int readHeight(byte[] data) {
        return LittleEndianByteBufUtils.readUShortLE(data, HEIGHT_OFFSET);
    }

    /**
     * 修改宽高并返回新数组（不修改原数组）
     */
    public static byte[] withSize(byte[] original, int width, int height) {
        byte[] modified = Arrays.copyOf(original, original.length);
        LittleEndianByteBufUtils.writeUShortLE(modified, WIDTH_OFFSET, width);
        LittleEndianByteBufUtils.writeUShortLE(modified, HEIGHT_OFFSET, height);
        return modified;
    }
}
