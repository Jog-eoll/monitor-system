package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * JetFileII 控制指令封装类
 *
 * 支持的控制指令:
 * 1. 删除操作 (命令码 'E')
 *    - 'T' = 删除 Text File
 *    - 'S' = 删除 String File
 *    - 'P' = 删除 Picture File
 *    - 'Q' = 删除播放列表
 *    - 'R' = 删除 Run time table
 *    - 'A' = 删除所有
 *
 * 2. 播放列表操作 (命令码 'E', Arg = "SL")
 *    - 发送播放列表到情报板
 *
 * 协议格式 (第一种通信格式):
 * <SOH(0x01)><Z><地址2字节><STX(0x02)><命令码'E'><Arg 1-2字节><EOT(0x04)>
 */
@Slf4j
public class JetFileIIControlCommand {

    // 协议常量
    private static final byte SOH = 0x01;
    private static final byte STX = 0x02;
    private static final byte EOT = 0x04;
    private static final byte REV = 'Z';

    // 命令码
    public static final byte CMD_CONTROL = 'E';

    // 删除操作子命令
    public static final char DELETE_TEXT_FILE = 'T';
    public static final char DELETE_STRING_FILE = 'S';
    public static final char DELETE_PICTURE_FILE = 'P';
    public static final char DELETE_PLAYLIST = 'Q';
    public static final char DELETE_RUNTIME_TABLE = 'R';
    public static final char DELETE_ALL = 'A';

    // 播放列表操作
    public static final String PLAYLIST_COMMAND = "SL";

    /**
     * 控制指令类型
     */
    public enum CommandType {
        DELETE_TEXT_FILE("删除文本文件"),
        DELETE_STRING_FILE("删除字符串文件"),
        DELETE_PICTURE_FILE("删除图片文件"),
        DELETE_PLAYLIST("删除播放列表"),
        DELETE_RUNTIME_TABLE("删除运行时间表"),
        DELETE_ALL("删除所有文件"),
        PLAYLIST_OPERATION("播放列表操作"),
        UNKNOWN("未知控制指令");

        private final String description;

        CommandType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 解析控制指令
     *
     * @param data 原始UDP数据
     * @return 解析结果,如果不是控制指令则返回null
     */
    public static ControlCommand parse(byte[] data) {
        if (data == null || data.length < 7) {
            return null;
        }

        try {
            // 验证协议头
            if (data[0] != SOH || data[1] != REV || data[4] != STX) {
                return null;
            }

            // 检查是否为控制命令码 'E'
            if (data[5] != CMD_CONTROL) {
                return null;
            }

            // 解析地址
            String address = new String(data, 2, 2, StandardCharsets.US_ASCII);

            // 解析参数
            String arg = null;
            int eotIndex = findEOT(data);
            if (eotIndex > 6) {
                int argLen = eotIndex - 6;
                if (argLen > 0 && argLen <= 3) {
                    arg = new String(data, 6, argLen, StandardCharsets.US_ASCII).trim();
                }
            }

            // 确定命令类型
            CommandType commandType = parseCommandType(arg);

            ControlCommand cmd = new ControlCommand();
            cmd.setAddress(address);
            cmd.setRawArg(arg);
            cmd.setCommandType(commandType);
            cmd.setRawData(data);

            // 解析播放列表文件列表
            if (commandType == CommandType.PLAYLIST_OPERATION && eotIndex > 9) {
                List<String> fileList = parseFileList(data, 9, eotIndex);
                cmd.setFileList(fileList);
            }

            log.info("【控制指令解析】{}: 地址={}, 参数='{}'", 
                    commandType.getDescription(), address, arg);

            return cmd;

        } catch (Exception e) {
            log.debug("【控制指令解析】解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建删除指令字节数组
     *
     * @param address 屏体地址 (00-99)
     * @param deleteType 删除类型 (DELETE_TEXT_FILE, DELETE_ALL 等)
     * @return 完整的UDP数据包
     */
    public static byte[] buildDeleteCommand(String address, char deleteType) {
        if (address == null || address.length() != 2) {
            throw new IllegalArgumentException("地址必须是2字节字符串(00-99)");
        }

        // 计算包长度: SOH(1) + Z(1) + 地址(2) + STX(1) + E(1) + Arg(1) + EOT(1) = 8字节
        byte[] packet = new byte[8];

        packet[0] = SOH;
        packet[1] = REV;
        packet[2] = (byte) address.charAt(0);
        packet[3] = (byte) address.charAt(1);
        packet[4] = STX;
        packet[5] = CMD_CONTROL;
        packet[6] = (byte) deleteType;
        packet[7] = EOT;

        log.info("【控制指令构建】删除指令: 地址={}, 类型={}, 数据={}", 
                address, deleteType, bytesToHex(packet));

        return packet;
    }

    /**
     * 构建播放列表指令
     *
     * @param address 屏体地址 (00-99)
     * @param fileList 文件列表 (如 ["0", "1", "2"] 或 ["D:\\T\\AB1"])
     * @return 完整的UDP数据包
     */
    public static byte[] buildPlaylistCommand(String address, List<String> fileList) {
        if (address == null || address.length() != 2) {
            throw new IllegalArgumentException("地址必须是2字节字符串(00-99)");
        }

        // 计算包长度
        int fileListLen = 0;
        if (fileList != null && !fileList.isEmpty()) {
            for (String file : fileList) {
                fileListLen += file.length();
            }
        }

        // SOH(1) + Z(1) + 地址(2) + STX(1) + E(1) + SL(2) + FileList(N) + EOT(1)
        int packetLen = 9 + fileListLen + 1;
        byte[] packet = new byte[packetLen];

        int pos = 0;
        packet[pos++] = SOH;
        packet[pos++] = REV;
        packet[pos++] = (byte) address.charAt(0);
        packet[pos++] = (byte) address.charAt(1);
        packet[pos++] = STX;
        packet[pos++] = CMD_CONTROL;  
        packet[pos++] = 'S';
        packet[pos++] = 'L';

        // 写入文件列表
        if (fileList != null) {
            for (String file : fileList) {
                byte[] fileBytes = file.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(fileBytes, 0, packet, pos, fileBytes.length);
                pos += fileBytes.length;
            }
        }

        packet[pos] = EOT;

        log.info("【控制指令构建】播放列表指令: 地址={}, 文件数={}, 数据={}",
                address, fileList != null ? fileList.size() : 0, bytesToHex(packet));

        return packet;
    }

    /**
     * 判断是否为控制指令
     */
    public static boolean isControlCommand(byte[] data) {
        if (data == null || data.length < 7) {
            return false;
        }
        return data[0] == SOH && data[1] == REV && data[4] == STX && data[5] == CMD_CONTROL;
    }

    // ========== 私有工具方法 ==========

    private static CommandType parseCommandType(String arg) {
        if (arg == null || arg.isEmpty()) {
            return CommandType.DELETE_ALL; // 'E' 后面没有参数时等同于 'A'
        }

        if (arg.length() >= 2 && arg.startsWith(PLAYLIST_COMMAND)) {
            return CommandType.PLAYLIST_OPERATION;
        }

        char firstChar = arg.charAt(0);
        switch (firstChar) {
            case DELETE_TEXT_FILE:
                return CommandType.DELETE_TEXT_FILE;
            case DELETE_STRING_FILE:
                return CommandType.DELETE_STRING_FILE;
            case DELETE_PICTURE_FILE:
                return CommandType.DELETE_PICTURE_FILE;
            case DELETE_PLAYLIST:
                return CommandType.DELETE_PLAYLIST;
            case DELETE_RUNTIME_TABLE:
                return CommandType.DELETE_RUNTIME_TABLE;
            case DELETE_ALL:
                return CommandType.DELETE_ALL;
            default:
                return CommandType.UNKNOWN;
        }
    }

    private static int findEOT(byte[] data) {
        for (int i = data.length - 1; i >= 6; i--) {
            if (data[i] == EOT || data[i] == 0x03) {
                return i;
            }
        }
        return data.length;
    }

    private static List<String> parseFileList(byte[] data, int start, int end) {
        List<String> fileList = new ArrayList<>();
        if (start >= end) {
            return fileList;
        }

        // 解析文件列表 (格式参考协议文档)
        String fileListStr = new String(data, start, end - start, StandardCharsets.US_ASCII);

        // 如果包含路径格式 0x0F，需要特殊解析
        int pos = start;
        while (pos < end) {
            if (data[pos] == 0x0F && pos + 5 <= end) {
                // 路径格式: 0x0F<盘符><目录><文件名2字节>
                char disk = (char) data[pos + 1];
                char dir = (char) data[pos + 2];
                String name = new String(data, pos + 3, 2, StandardCharsets.US_ASCII).trim();
                fileList.add(disk + ":\\" + dir + "\\" + name);
                pos += 5;
            } else {
                // 简单文件名格式 (1字节)
                fileList.add(String.valueOf((char) data[pos]));
                pos++;
            }
        }

        return fileList;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    // ========== 数据实体 ==========

    /**
     * 控制指令实体
     */
    @Data
    public static class ControlCommand {
        /** 屏体地址 (00-99) */
        private String address;

        /** 原始参数字符串 */
        private String rawArg;

        /** 命令类型 */
        private CommandType commandType;

        /** 播放列表文件列表 (仅 PLAYLIST_OPERATION 有效) */
        private List<String> fileList;

        /** 原始数据 */
        private byte[] rawData;

        /**
         * 是否为删除指令
         */
        public boolean isDeleteCommand() {
            return commandType != null && commandType.name().startsWith("DELETE");
        }

        /**
         * 是否为播放列表操作
         */
        public boolean isPlaylistCommand() {
            return commandType == CommandType.PLAYLIST_OPERATION;
        }

        /**
         * 获取命令描述
         */
        public String getDescription() {
            if (commandType == null) {
                return "未知指令";
            }

            StringBuilder desc = new StringBuilder(commandType.getDescription());

            if (isPlaylistCommand() && fileList != null && !fileList.isEmpty()) {
                desc.append(" [文件: ").append(String.join(", ", fileList)).append("]");
            }

            return desc.toString();
        }
    }
}
