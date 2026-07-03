package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Sigma软件命令解析器
 * 
 * Sigma通过JetFileII协议发送的payload并不是原始图片数据,
 * 而是"文件引用指令" — 告诉终端播放某个文件（或列表）。
 * 
 * Payload结构 (从JetFileII Type2的payload中解析):
 * [0-1]   命令标识: "SQ" (0x53 0x51)
 * [2-7]   命令参数
 * [8-9]   子命令标识: "FS" (0x46 0x53) 或其他
 * [10-13] 子命令参数
 * [14-17] 其他参数
 * [18-N]  文件路径1 (以0x00结尾的字符串)
 * [M-M+N] 文件路径2 (列表时，后续还有更多路径)
 * ...
 * [末尾]  播放参数 (显示方式等)
 * 
 * 已知的Sigma命令类型:
 * - 主命令=0x02, 子命令=0x02: 文件播放指令(PICTURE_FILE / 列表)
 * - 主命令=0x01, 子命令=0x08: 控制命令(心跳/状态)
 * 
 * 重要发现：发送列表时，payload 中包含列表里所有文件的路径，
 * 单发文件时 payload 只包含该文件的路径。
 */
@Slf4j
public class SigmaCommandParser {

    // 命令标识 "SQ"
    private static final byte CMD_S = 0x53;
    private static final byte CMD_Q = 0x51;

    /**
     * 从JetFileII Type2的payload中解析Sigma命令
     * 
     * @param payload JetFileII Type2解析后的payload数据
     * @param mainCmd JetFileII主命令 (用于辅助判断)
     * @param subCmd  JetFileII子命令 (用于辅助判断)
     * @return 解析结果,如果不是Sigma命令则返回null
     */
    public static SigmaCommand parse(byte[] payload, int mainCmd, int subCmd) {
        if (payload == null || payload.length < 20) {
            return null;
        }

        try {
            // 检查是否以 "SQ" 开头
            if (payload[0] != CMD_S || payload[1] != CMD_Q) {
                return null;
            }

            SigmaCommand command = new SigmaCommand();
            command.setMainCmd(mainCmd);
            command.setSubCmd(subCmd);
            command.setRawPayload(payload);

            // 提取 payload 中所有文件路径（列表发送时包含多个路径）
            List<String> allPaths = extractAllFilePaths(payload);
            if (!allPaths.isEmpty()) {
                // 第一个路径作为主路径
                String filePath = allPaths.get(0);
                command.setFilePath(filePath);
                command.setAllFilePaths(allPaths);
                command.setCommandType("FILE_PLAY");

                String fileName = extractFileNameFromPath(filePath);
                command.setFileName(fileName);

                String fileExtension = getFileExtension(fileName);
                command.setFileExtension(fileExtension);
                command.setImageFile(isImageExtension(fileExtension));

                log.info("【Sigma命令】文件播放指令: 路径={}, 文件名={}, 类型={}, 是图片={}, 列表总路径数={}",
                        filePath, fileName, fileExtension, command.isImageFile(), allPaths.size());
                if (allPaths.size() > 1) {
                    log.info("【Sigma命令】列表包含多个文件: {}", allPaths);
                }
            } else {
                // 没有文件路径,可能是其他类型的Sigma命令
                command.setCommandType("CONTROL");
                log.debug("【Sigma命令】控制命令: mainCmd=0x{}, subCmd=0x{}",
                        String.format("%02X", mainCmd), String.format("%02X", subCmd));
            }

            return command;

        } catch (Exception e) {
            log.debug("【Sigma命令】解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** GBK字符集，Windows LED屏软件文件路径默认使用 GBK 编码（支持中文文件名） */
    private static final Charset GBK = Charset.forName("GBK");

    /**
     * 从payload中提取所有文件路径（扫描所有 X:\ 模式）
     * 发送列表时 payload 包含多个路径，单文件时只有一个
     */
    private static List<String> extractAllFilePaths(byte[] payload) {
        List<String> paths = new ArrayList<>();
        int i = 0;
        while (i < payload.length - 3) {
            byte b = payload[i];
            if (b >= 'A' && b <= 'Z' && payload[i + 1] == ':' && payload[i + 2] == '\\') {
                int endIndex = i;
                while (endIndex < payload.length && payload[endIndex] != 0x00) {
                    endIndex++;
                }
                if (endIndex > i + 3) {
                    String path = new String(payload, i, endIndex - i, GBK);
                    paths.add(path);
                    i = endIndex; // 从路径结束处继续扫描
                    continue;
                }
            }
            i++;
        }
        return paths;
    }

    /**
     * 从payload中提取第一个文件路径（保留向后兼容）
     */
    private static String extractFilePath(byte[] payload) {
        List<String> paths = extractAllFilePaths(payload);
        return paths.isEmpty() ? null : paths.get(0);
    }


    /**
     * 从路径中提取文件名
     */
    private static String extractFileNameFromPath(String filePath) {
        if (filePath == null) return null;
        int lastSlash = filePath.lastIndexOf('\\');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        int lastForwardSlash = filePath.lastIndexOf('/');
        if (lastForwardSlash >= 0 && lastForwardSlash < filePath.length() - 1) {
            return filePath.substring(lastForwardSlash + 1);
        }
        return filePath;
    }

    /**
     * 从payload中提取独立的文件名字段
     * 通常在路径之后的某个位置,也是以0x00结尾
     */
    private static String extractStandaloneFileName(byte[] payload, String filePath) {
        if (filePath == null) return null;
        
        // 在路径之后查找文件名
        String fileNameFromPath = extractFileNameFromPath(filePath);
        if (fileNameFromPath == null) return null;
        
        // 搜索文件名的字节模式
        byte[] fileNameBytes = fileNameFromPath.getBytes(StandardCharsets.US_ASCII);
        
        // 从路径结束位置之后搜索
        int searchStart = 0;
        for (int i = 0; i < payload.length - fileNameBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < fileNameBytes.length; j++) {
                if (payload[i + j] != fileNameBytes[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                searchStart = i;
                // 继续搜索,找最后一次出现
            }
        }
        
        if (searchStart > 0) {
            // 读取完整文件名
            int end = searchStart;
            while (end < payload.length && payload[end] != 0x00) {
                end++;
            }
            if (end > searchStart) {
                return new String(payload, searchStart, end - searchStart, StandardCharsets.US_ASCII);
            }
        }
        
        return null;
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 判断是否为图片扩展名
     */
    private static boolean isImageExtension(String ext) {
        if (ext == null) return false;
        switch (ext.toLowerCase()) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "tif":
            case "tiff":
            case "webp":
            case "pmg":
                return true;
            default:
                return false;
        }
    }

    /**
     * Sigma命令实体
     */
    @Data
    public static class SigmaCommand {
        /** 命令类型: FILE_PLAY(文件播放), CONTROL(控制) */
        private String commandType;
        
        /** JetFileII主命令 */
        private int mainCmd;
        
        /** JetFileII子命令 */
        private int subCmd;
        
        /** 文件路径（主文件，如 D:\p\111.jpg 或 D:\T\SJWG.Nmg） */
        private String filePath;

        /**
         * payload 中所有文件路径（列表发送时包含多个）
         * 单发文件时只有一个元素，与 filePath 相同
         */
        private List<String> allFilePaths;
        
        /** 文件名 (如 111.jpg) */
        private String fileName;
        
        /** 文件扩展名 (如 jpg) */
        private String fileExtension;
        
        /** 是否为图片文件 */
        private boolean imageFile;
        
        /** 原始payload */
        private byte[] rawPayload;
    }
}
