package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sigma Play文件传输分包重组解析器
 *
 * Sigma发送图片等大文件时，会将文件拆分为多个UDP分包发送：
 * - 每个分包都是完整的JetFileII第二种格式数据包（0x55同步码）
 * - 参数区(Args)包含文件路径等分包信息
 * - 数据区(Payload)包含文件二进制片段
 *
 * 不匹配的数据（文本指令、SQ命令等参数区无文件路径的包）返回null，
 * 交给 JetFileIIParser2 等后续解析器处理。
 */
@Slf4j
public class SigmaPlayParser {

    // JetFileII同步码
    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xA7;
    private static final byte SYN_TYPE2_CRC = (byte) 0xA3;
    private static final byte SYN_TYPE3_SUM = (byte) 0xA8;
    private static final byte SYN_TYPE3_CRC = (byte) 0xA4;

    /** 分包重组缓存: key = 源地址_文件路径 */
    private static final Map<String, FileAssembly> assemblyCache = new ConcurrentHashMap<>();
    /** 重组超时: 2分钟 */
    private static final long ASSEMBLY_TIMEOUT_MS = 120_000;
    /** 最大重组文件大小: 20MB */
    private static final int MAX_ASSEMBLY_SIZE = 20 * 1024 * 1024;

    /** 最大重组文件大小（视频）: 500MB */
    private static final int MAX_VIDEO_ASSEMBLY_SIZE = 500 * 1024 * 1024;


    /** 默认标准payload大小 */
    private static final int DEFAULT_STANDARD_PAYLOAD_SIZE = 768;
    /** 判定旧传输已结束的静默间隔: 30秒 */
    private static final long STALE_GAP_MS = 30_000;

    private static volatile long lastCleanTime = System.currentTimeMillis();



    /**
     * 解析结果的操作类型
     */
    public enum ParseAction {
        /** 重组完成，应上报完整文件 */
        REPORT,
        /** 收集中，跳过上报 */
        SKIP
    }

    /**
     * 尝试识别并重组Sigma文件传输分包
     *
     * @param udpData 原始UDP数据
     * @return null = 不是文件传输包（交给其他解析器处理）;
     *         action=REPORT 重组完成，含完整文件数据;
     *         action=SKIP 收集中，跳过
     */
    public static FileTransferResult parseFileTransfer(byte[] udpData) {
        if (udpData == null || udpData.length < 20) {
            log.debug("【SigmaPlayParser】数据太短或为空: length={}", udpData == null ? 0 : udpData.length);
            return null;
        }

        // 1. 验证JetFileII同步码
        if (udpData[0] != SYN_BYTE1) {
            log.debug("【SigmaPlayParser】同步码1不匹配: 0x{}", String.format("%02X", udpData[0]));
            return null;
        }
        byte syn2 = udpData[1];
        if (syn2 != SYN_TYPE2_SUM && syn2 != SYN_TYPE2_CRC
                && syn2 != SYN_TYPE3_SUM && syn2 != SYN_TYPE3_CRC) {
            log.debug("【SigmaPlayParser】同步码2不匹配: 0x{}", String.format("%02X", syn2));
            return null;
        }

        // 2. 解析关键头字段
        int sourceAddr = ByteBuffer.wrap(udpData, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        int groupAddr = udpData[8] & 0xFF;
        int unitAddr = udpData[9] & 0xFF;
        int argLen = udpData[14] & 0xFF;
        int argBytes = argLen * 4;
        int dataStartIndex = 16 + argBytes;

        log.debug("【SigmaPlayParser】头信息: argLen={}, argBytes={}, dataStartIndex={}, totalLen={}",
                argLen, argBytes, dataStartIndex, udpData.length);

        // 3. 文件传输包必须有参数区
        if (argLen == 0 || udpData.length < dataStartIndex) {
            log.debug("【SigmaPlayParser】参数区无效: argLen={}", argLen);
            return null;
        }

        // 4. 从参数区提取文件路径
        byte[] args = new byte[argBytes];
        System.arraycopy(udpData, 16, args, 0, argBytes);
        
        // 打印参数区内容（用于调试）
        StringBuilder argsHex = new StringBuilder();
        for (int i = 0; i < Math.min(argBytes, 32); i++) {
            argsHex.append(String.format("%02X ", args[i]));
        }
        log.debug("【SigmaPlayParser】参数区前32字节: {}", argsHex.toString().trim());
        
        String filePath = extractFilePathFromArgs(args);
        if (filePath == null) {
            log.debug("【SigmaPlayParser】未找到文件路径（X:\\模式）");
            return null;
        }
        
        log.info("【SigmaPlayParser】识别到文件传输: filePath={}", filePath);

        // 5. 提取payload
        if (udpData.length <= dataStartIndex) {
            return null;
        }
        int payloadLen = udpData.length - dataStartIndex;
        byte[] payload = new byte[payloadLen];
        System.arraycopy(udpData, dataStartIndex, payload, 0, payloadLen);

        // 6. 从参数区解析标准payload大小（args[4-5] little-endian）
        int standardPayloadSize = parseStandardPayloadSize(args);

        // 7. 清理过期缓存
        cleanStaleAssemblies();

        // 8. 重组逻辑
        String cacheKey = sourceAddr + "_" + filePath;
        String destAddress = String.format("%d:%d", groupAddr, unitAddr);
        String fileName = extractFileNameFromPath(filePath);
        String ext = getFileExtension(fileName);
        boolean isImage = isImageExtension(ext);

        FileAssembly assembly = assemblyCache.get(cacheKey);

        // 如果存在旧的assembly但已静默超过30秒，说明上次传输已结束
        // 完成旧传输的重组，然后将当前包作为新传输的首包
        FileTransferResult staleResult = null;
        if (assembly != null && assembly.isStale()) {
            // 传输完成（超时触发）！取出重组数据
            byte[] fullData = assembly.getAssembledData();
            int totalPackets = assembly.getPacketCount();
            assemblyCache.remove(cacheKey);

            log.info("【Sigma文件传输】超时完成重组: file={}, 总包数={}, 总大小={}KB (超时触发)",
                    filePath, totalPackets, fullData.length / 1024);

            // 先保存超时上报结果
            staleResult = buildReportResult(filePath, fileName, ext, isImage,
                    sourceAddr, destAddress, fullData, totalPackets);
            
            // 然后重置 assembly，让当前包作为新传输的首包处理
            assembly = null;
        }

        // 如果有超时上报结果，先返回它（当前包会在下次调用时作为首包处理）
        // 注意：这会导致当前包被跳过，但通常超时后的新包是新传输的首包
        if (staleResult != null) {
            return staleResult;
        }

        if (assembly == null) {
            // === 首包 ===

            // 如果首包payload已经比标准大小小，说明是单包传输，直接上报
            if (payloadLen < standardPayloadSize) {
                log.info("【Sigma文件传输】单包传输: file={}, payload={}B", filePath, payloadLen);
                return buildReportResult(filePath, fileName, ext, isImage,
                        sourceAddr, destAddress, payload, 1);
            }

            // 多包传输，开始缓存
            assembly = new FileAssembly(filePath, standardPayloadSize);
            assembly.appendPayload(payload);
            assemblyCache.put(cacheKey, assembly);

            log.info("【Sigma文件传输】开始接收: file={}, 标准包大小={}B, 首包payload={}B, args长度={}B", 
                    filePath, standardPayloadSize, payloadLen, argBytes);

            FileTransferResult result = new FileTransferResult();
            result.setAction(ParseAction.SKIP);
            result.setFilePath(filePath);
            return result;
        }

        // === 后续包 ===
        assembly.appendPayload(payload);

        // 检查是否为末包（payload小于标准大小 = 文件最后一段不满一包）
        boolean isLastPacket = payloadLen < assembly.getStandardPayloadSize();
        
        // 详细调试日志
        log.debug("【Sigma分包调试】file={}, 包序号={}, payloadLen={}, standardSize={}, isLast={}",
                filePath, assembly.getPacketCount(), payloadLen, assembly.getStandardPayloadSize(), isLastPacket);

        if (isLastPacket) {
            // 传输完成！取出重组数据
            byte[] fullData = assembly.getAssembledData();
            int totalPackets = assembly.getPacketCount();
            assemblyCache.remove(cacheKey);

            log.info("【Sigma文件传输】接收完成: file={}, 总包数={}, 总大小={}KB",
                    filePath, totalPackets, fullData.length / 1024);

            return buildReportResult(filePath, fileName, ext, isImage,
                    sourceAddr, destAddress, fullData, totalPackets);
        }

        // 仍在接收中，进度日志
        if (assembly.getPacketCount() % 500 == 0) {
            log.info("【Sigma文件传输】接收中: file={}, 已收{}包, 已缓存{}KB",
                    filePath, assembly.getPacketCount(), assembly.getCurrentSize() / 1024);
        }

        // 超过大小限制，放弃

        int maxSize = isImage ? MAX_ASSEMBLY_SIZE :
                      isVideoExtension(ext) ? MAX_VIDEO_ASSEMBLY_SIZE : MAX_ASSEMBLY_SIZE;
        if (assembly.getCurrentSize() > maxSize) {
            log.warn("【Sigma文件传输】文件过大({} MB)，放弃重组: file={}",
                    assembly.getCurrentSize() / (1024 * 1024), filePath);
            assemblyCache.remove(cacheKey);
        }

        FileTransferResult result = new FileTransferResult();
        result.setAction(ParseAction.SKIP);
        result.setFilePath(filePath);
        return result;
    }

    // ========== 工具方法 ==========

    private static FileTransferResult buildReportResult(
            String filePath, String fileName, String ext, boolean isImage,
            int sourceAddr, String destAddress, byte[] data, int totalPackets) {
        FileTransferResult result = new FileTransferResult();
        result.setAction(ParseAction.REPORT);
        result.setFilePath(filePath);
        result.setFileName(fileName);
        result.setFileExtension(ext);
        result.setImageFile(isImage);
        result.setSourceAddr(sourceAddr);
        result.setDestAddress(destAddress);
        result.setReassembledData(data);
        result.setTotalPackets(totalPackets);
        result.setTotalSize(data.length);
        return result;
    }

    /**
     * 从参数区解析标准payload大小
     * 参数区格式(推断): args[4-5] 为每包payload大小(little-endian)
     */
    private static int parseStandardPayloadSize(byte[] args) {
        if (args.length >= 6) {
            int size = ByteBuffer.wrap(args, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (size > 0 && size <= 4096) {
                return size;
            }
        }
        return DEFAULT_STANDARD_PAYLOAD_SIZE;
    }


    private static boolean isVideoExtension(String ext){
        if(ext == null)  return false;
        switch (ext.toLowerCase()){
            case "mp4": case "avi": case "mov": case "wmv":
            case "mkv": case "flv": case "rmvb": case "ts":
                return true;
            default:
                return false;
        }

    }

    /**
     * 从参数区(Args)提取文件路径
     * 查找 X:\ 模式（盘符+冒号+反斜杠）
     * 使用 GBK 编码解析，支持中文文件名（Windows LED屏软件默认使用 GBK 编码路径）
     */
    private static final Charset GBK = Charset.forName("GBK");

    private static String extractFilePathFromArgs(byte[] args) {
        for (int i = 0; i < args.length - 3; i++) {
            byte b = args[i];
            if (b >= 'A' && b <= 'Z' && args[i + 1] == ':' && args[i + 2] == '\\') {
                int endIndex = i;
                while (endIndex < args.length && args[endIndex] != 0x00) {
                    endIndex++;
                }
                if (endIndex > i + 3) {
                    return new String(args, i, endIndex - i, GBK);
                }
            }
        }
        return null;
    }

    private static String extractFileNameFromPath(String filePath) {
        if (filePath == null) return null;
        int lastSlash = filePath.lastIndexOf('\\');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        int lastForward = filePath.lastIndexOf('/');
        if (lastForward >= 0 && lastForward < filePath.length() - 1) {
            return filePath.substring(lastForward + 1);
        }
        return filePath;
    }

    private static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private static boolean isImageExtension(String ext) {
        if (ext == null) return false;
        switch (ext.toLowerCase()) {
            case "jpg": case "jpeg": case "png": case "gif":
            case "bmp": case "tif": case "tiff": case "webp":
                return true;
            default:
                return false;
        }
    }

    private static void cleanStaleAssemblies() {
        long now = System.currentTimeMillis();
        if (now - lastCleanTime < 30_000) return;
        lastCleanTime = now;

        assemblyCache.entrySet().removeIf(entry -> {
            FileAssembly a = entry.getValue();
            if (a.isExpired()) {
                log.warn("【Sigma缓存清理】超时丢弃: file={}, 已收{}包, 已缓存{}KB",
                        a.getFilePath(), a.getPacketCount(), a.getCurrentSize() / 1024);
                return true;
            }
            return false;
        });
    }

    // ========== 内部数据结构 ==========

    /**
     * 文件重组缓存（缓存一次文件传输的所有分包payload）
     */
    private static class FileAssembly {
        private final String filePath;
        private final int standardPayloadSize;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final long createTime = System.currentTimeMillis();
        private long lastPacketTime = System.currentTimeMillis();
        private int packetCount = 0;

        FileAssembly(String filePath, int standardPayloadSize) {
            this.filePath = filePath;
            this.standardPayloadSize = standardPayloadSize;
        }

        void appendPayload(byte[] payload) {
            buffer.write(payload, 0, payload.length);
            packetCount++;
            lastPacketTime = System.currentTimeMillis();
        }

        byte[] getAssembledData() {
            return buffer.toByteArray();
        }

        int getCurrentSize() { return buffer.size(); }
        int getPacketCount() { return packetCount; }
        int getStandardPayloadSize() { return standardPayloadSize; }
        String getFilePath() { return filePath; }

        /** 超时判断（2分钟无新包） */
        boolean isExpired() {
            return System.currentTimeMillis() - lastPacketTime > ASSEMBLY_TIMEOUT_MS;
        }

        /** 静默判断（30秒无新包，认为上次传输已结束） */
        boolean isStale() {
            return System.currentTimeMillis() - lastPacketTime > STALE_GAP_MS;
        }
    }

    /**
     * 文件传输解析结果
     */
    @Data
    public static class FileTransferResult {
        /** 操作类型：REPORT/SKIP */
        private ParseAction action;
        /** 文件完整路径（如 D:\P\111.jpg） */
        private String filePath;
        /** 文件名（如 111.jpg） */
        private String fileName;
        /** 扩展名（如 jpg） */
        private String fileExtension;
        /** 是否为图片文件 */
        private boolean imageFile;
        /** JetFileII源地址 */
        private int sourceAddr;
        /** 目的地址（如 1:1） */
        private String destAddress;
        /** 重组后的完整文件数据 */
        private byte[] reassembledData;
        /** 总包数 */
        private int totalPackets;
        /** 重组后总大小(字节) */
        private int totalSize;

        /**
         * 判断是否为文本文件
         * 注意：Nmg 是 Sigma 节目文件，虽然以二进制传输，但本质是文本文件
         */
        public boolean isTextFile() {
            if (fileExtension == null) return false;
            switch (fileExtension.toLowerCase()) {
                case "nmg":   // Sigma节目文件（文本性质，但二进制传输）
                case "pmg":   // PMG节目文件（内含文本，不是图片）
                case "txt":
                case "log":
                case "xml":
                case "json":
                case "ini":
                case "cfg":
                case "conf":
                    return true;
                default:
                    return false;
            }
        }

        /**
         * 判断是否需要解码为文本
         * .Nmg 文件虽然是文本文件，但不需要解码，直接使用 Base64
         */
        public boolean needsTextDecoding() {
            if (fileExtension == null) return false;
            switch (fileExtension.toLowerCase()) {
                case "txt":
                case "log":
                case "xml":
                case "json":
                case "ini":
                case "cfg":
                case "conf":
                    return true;
                case "nmg":   // Nmg 不解码，保持 Base64
                default:
                    return false;
            }
        }


        /**
         * 判断是否为视频文件
         */
        public boolean isVideoFile() {
            if (fileExtension == null) return false;
            switch (fileExtension.toLowerCase()) {
                case "mp4":
                case "avi":
                case "mov":
                case "wmv":
                case "mkv":
                case "flv":
                case "rmvb":
                case "ts":
                    return true;
                default:
                    return false;
            }
        }


    }
}
