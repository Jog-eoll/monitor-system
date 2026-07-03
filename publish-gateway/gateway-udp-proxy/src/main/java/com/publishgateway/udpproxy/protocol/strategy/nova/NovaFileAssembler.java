package com.publishgateway.udpproxy.protocol.strategy.nova;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诺瓦文件传输/截图分包重组器
 *
 * 处理两种分块数据:
 * 1. 文件传输 (TCP流下发):
 *    - 0x11: 文件名发送 (块大小 2B + 文件名 UTF8)
 *    - 0x13: 文件内容发送 (块号 2B + 数据)
 *    - 0xF9: 文件发送完毕 (执行结果 1B)
 *
 * 2. 截图数据:
 *    - 0x80: 获取播放截图请求 (块大小 2B, ≥512)
 *    - 0x81: 截图数据回复 (块号 2B + JPG数据块, 最后一块 N < 块大小)
 *
 * 设计参考 SigmaPlayParser 的分包缓存+超时清理模式
 *
 * @Author: zyh
 * @Date: 2026/4/13
 */
@Slf4j
public class NovaFileAssembler {

    /** 文件传输会话缓存: key = sourceIp + "_file_" + deviceAddr */
    private static final ConcurrentHashMap<String, FileTransferSession> FILE_SESSIONS = new ConcurrentHashMap<>();

    /** 截图会话缓存: key = sourceIp + "_screenshot_" + deviceAddr */
    private static final ConcurrentHashMap<String, ScreenshotSession> SCREENSHOT_SESSIONS = new ConcurrentHashMap<>();

    /** 文件传输超时: 2分钟 */
    private static final long FILE_TIMEOUT_MS = 2 * 60 * 1000;
    /** 截图超时: 30秒 */
    private static final long SCREENSHOT_TIMEOUT_MS = 30 * 1000;
    /** 普通文件大小上限: 20MB */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    /** 视频文件大小上限: 500MB */
    private static final long MAX_VIDEO_SIZE = 500L * 1024 * 1024;

    static {
        // 守护线程定期清理超时会话
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30_000);
                    cleanupStaleSessions();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "nova-assembler-cleanup");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    // ==================== 文件传输处理 ====================

    /**
     * 处理文件名发送 (0x11)
     * 数据域: 块大小(2B, 小端序) + 文件名(nB, UTF8)
     *
     * @return 如果有进行中的旧会话则返回其结果，否则返回 SKIP
     */
    public static FileTransferResult handleFileNameSend(int deviceAddr, byte[] data, String sourceIp) {
        if (data == null || data.length < 3) {
            return null;
        }

        int blockSize = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        String fileName = new String(data, 2, data.length - 2, StandardCharsets.UTF_8).trim();

        // 带 MD5 校验的文件名格式: filename_MD5校验码.ext → 实际文件名 filename.ext
        // 不带 MD5 校验: 直接是 filename.ext

        String sessionKey = sourceIp + "_file_" + deviceAddr;

        // 如果有进行中的旧会话，先完成它
        FileTransferSession oldSession = FILE_SESSIONS.get(sessionKey);
        FileTransferResult oldResult = null;
        if (oldSession != null && oldSession.hasData()) {
            log.info("【Nova文件传输】新文件到来，完成旧会话: old={}, blocks={}",
                    oldSession.fileName, oldSession.blocks.size());
            oldResult = oldSession.buildResult();
        }

        // 创建新会话
        FileTransferSession session = new FileTransferSession();
        session.fileName = fileName;
        session.blockSize = blockSize;
        session.deviceAddress = deviceAddr;
        session.sourceIp = sourceIp;
        session.startTime = System.currentTimeMillis();
        session.lastActiveTime = System.currentTimeMillis();
        FILE_SESSIONS.put(sessionKey, session);

        log.info("【Nova文件传输】文件名接收: file={}, blockSize={}, device=0x{}",
                fileName, blockSize, String.format("%04X", deviceAddr));

        return oldResult != null ? oldResult : FileTransferResult.skip();
    }

    /**
     * 处理文件内容发送 (0x13)
     * 数据域: 块号(2B, 小端序, 从1开始) + 数据内容
     * 块号按发送顺序连续编号，数据长度等于 0x11 中指定的块大小
     */
    public static FileTransferResult handleFileContentSend(int deviceAddr, byte[] data, String sourceIp) {
        if (data == null || data.length < 3) {
            return FileTransferResult.skip();
        }

        String sessionKey = sourceIp + "_file_" + deviceAddr;
        FileTransferSession session = FILE_SESSIONS.get(sessionKey);
        if (session == null) {
            log.debug("【Nova文件传输】收到数据块但无活跃会话: device=0x{}", String.format("%04X", deviceAddr));
            return FileTransferResult.skip();
        }

        int blockNum = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        byte[] blockData = new byte[data.length - 2];
        System.arraycopy(data, 2, blockData, 0, blockData.length);

        session.blocks.put(blockNum, blockData);
        session.totalSize += blockData.length;
        session.lastActiveTime = System.currentTimeMillis();

        // 文件大小限制检查
        long maxSize = isVideoFile(session.fileName) ? MAX_VIDEO_SIZE : MAX_FILE_SIZE;
        if (session.totalSize > maxSize) {
            log.warn("【Nova文件传输】文件超限: file={}, size={}MB, limit={}MB",
                    session.fileName, session.totalSize / (1024 * 1024), maxSize / (1024 * 1024));
            FILE_SESSIONS.remove(sessionKey);
            return FileTransferResult.skip();
        }

        log.debug("【Nova文件传输】数据块: file={}, block={}, blockLen={}B, totalSize={}B",
                session.fileName, blockNum, blockData.length, session.totalSize);

        // 如果数据块小于块大小且小于65535字节，可能是末尾块
        // 但不立即完成，等待 0xF9 确认或静默超时
        if (session.blockSize > 0 && blockData.length < session.blockSize) {
            log.debug("【Nova文件传输】检测到可能的末尾块: file={}, block={}, blockLen={} < blockSize={}",
                    session.fileName, blockNum, blockData.length, session.blockSize);
        }

        return FileTransferResult.skip();
    }

    /**
     * 处理文件发送完毕 (0xF9)
     * 数据域: 执行结果(1B) 1=发送成功, 0=发送失败
     * 该指令是设备回复，表示文件已全部接收
     */
    public static FileTransferResult handleFileTransferComplete(int deviceAddr, byte[] data, String sourceIp) {
        String sessionKey = sourceIp + "_file_" + deviceAddr;
        FileTransferSession session = FILE_SESSIONS.remove(sessionKey);

        if (session == null) {
            log.debug("【Nova文件传输】收到完成信号但无活跃会话: device=0x{}", String.format("%04X", deviceAddr));
            return FileTransferResult.skip();
        }

        boolean success = data != null && data.length > 0 && data[0] == 1;

        if (success && session.hasData()) {
            log.info("【Nova文件传输】完成: file={}, blocks={}, size={}KB",
                    session.fileName, session.blocks.size(), session.totalSize / 1024);
            return session.buildResult();
        } else {
            log.warn("【Nova文件传输】失败或无数据: file={}, success={}, blocks={}",
                    session.fileName, success, session.blocks.size());
            return FileTransferResult.skip();
        }
    }

    // ==================== 截图数据处理 ====================

    /**
     * 处理截图请求 (0x80)
     * 数据域: 截图上报数据块大小(2B, 小端序, 不小于512字节)
     * 收到请求后初始化截图会话，等待 0x81 数据块
     */
    public static void handleScreenshotRequest(int deviceAddr, byte[] data, String sourceIp) {
        if (data == null || data.length < 2) return;

        int blockSize = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        String sessionKey = sourceIp + "_screenshot_" + deviceAddr;

        ScreenshotSession session = new ScreenshotSession();
        session.blockSize = blockSize;
        session.deviceAddress = deviceAddr;
        session.startTime = System.currentTimeMillis();
        session.lastActiveTime = System.currentTimeMillis();
        SCREENSHOT_SESSIONS.put(sessionKey, session);

        log.info("【Nova截图】截图请求: device=0x{}, blockSize={}",
                String.format("%04X", deviceAddr), blockSize);
    }

    /**
     * 处理截图数据回复 (0x81)
     * 数据域: 块号(2B, 小端序) + JPG数据块(nB)
     * 最后一块: N < 块大小 表示为最后一个数据块
     *
     * @return 截图重组完成时返回 ScreenshotResult，未完成返回 null
     */
    public static ScreenshotResult handleScreenshotData(int deviceAddr, byte[] data, String sourceIp) {
        if (data == null || data.length < 3) {
            return null;
        }

        String sessionKey = sourceIp + "_screenshot_" + deviceAddr;
        ScreenshotSession session = SCREENSHOT_SESSIONS.get(sessionKey);

        if (session == null) {
            // 可能没截获 0x80 请求，创建临时会话
            session = new ScreenshotSession();
            session.blockSize = 0;
            session.deviceAddress = deviceAddr;
            session.startTime = System.currentTimeMillis();
            session.lastActiveTime = System.currentTimeMillis();
            SCREENSHOT_SESSIONS.put(sessionKey, session);
        }

        int blockNum = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        byte[] blockData = new byte[data.length - 2];
        System.arraycopy(data, 2, blockData, 0, blockData.length);

        session.blocks.put(blockNum, blockData);
        session.totalSize += blockData.length;
        session.lastActiveTime = System.currentTimeMillis();

        // 判断是否为最后一块（数据长度 < 指定的块大小）
        boolean isLastBlock = session.blockSize > 0 && blockData.length < session.blockSize;

        log.debug("【Nova截图】数据块: block={}, size={}B, total={}B, isLast={}",
                blockNum, blockData.length, session.totalSize, isLastBlock);

        if (isLastBlock) {
            SCREENSHOT_SESSIONS.remove(sessionKey);
            return session.buildResult();
        }

        return null; // 截图未完成，继续等待后续块
    }

    // ==================== 超时会话清理 ====================

    private static void cleanupStaleSessions() {
        long now = System.currentTimeMillis();

        FILE_SESSIONS.entrySet().removeIf(entry -> {
            FileTransferSession s = entry.getValue();
            if (now - s.lastActiveTime > FILE_TIMEOUT_MS) {
                log.info("【Nova清理】文件传输超时: file={}, blocks={}, size={}KB",
                        s.fileName, s.blocks.size(), s.totalSize / 1024);
                return true;
            }
            return false;
        });

        SCREENSHOT_SESSIONS.entrySet().removeIf(entry -> {
            ScreenshotSession s = entry.getValue();
            if (now - s.lastActiveTime > SCREENSHOT_TIMEOUT_MS) {
                log.info("【Nova清理】截图超时: device=0x{}, blocks={}",
                        String.format("%04X", s.deviceAddress), s.blocks.size());
                return true;
            }
            return false;
        });
    }

    // ==================== 辅助方法 ====================

    private static boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
                || lower.endsWith(".mov") || lower.endsWith(".wmv") || lower.endsWith(".flv");
    }

    static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    static boolean isImageExtension(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext)
                || "gif".equals(ext) || "bmp".equals(ext);
    }

    static boolean isTextExtension(String ext) {
        return "lst".equals(ext) || "txt".equals(ext) || "xml".equals(ext) || "json".equals(ext);
    }

    // ==================== 内部会话类 ====================

    private static class FileTransferSession {
        String fileName;
        int blockSize;
        int deviceAddress;
        String sourceIp;
        long startTime;
        long lastActiveTime;
        int totalSize;
        final TreeMap<Integer, byte[]> blocks = new TreeMap<>();

        boolean hasData() {
            return !blocks.isEmpty() && totalSize > 0;
        }

        FileTransferResult buildResult() {
            byte[] assembled = assembleBlocks();
            String ext = getFileExtension(fileName);

            FileTransferResult r = new FileTransferResult();
            r.action = ParseAction.REPORT;
            r.fileName = fileName;
            r.fileExtension = ext;
            r.fileData = assembled;
            r.blockSize = blockSize;
            r.totalBlocks = blocks.size();
            r.totalSize = assembled != null ? assembled.length : 0;
            r.deviceAddress = deviceAddress;
            r.imageFile = isImageExtension(ext);
            r.videoFile = isVideoFile(fileName);
            r.textFile = isTextExtension(ext);
            return r;
        }

        byte[] assembleBlocks() {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(totalSize);
                for (byte[] block : blocks.values()) {
                    out.write(block);
                }
                return out.toByteArray();
            } catch (Exception e) {
                log.error("【Nova文件重组】拼接失败: file={}, error={}", fileName, e.getMessage());
                return null;
            }
        }
    }

    private static class ScreenshotSession {
        int blockSize;
        int deviceAddress;
        long startTime;
        long lastActiveTime;
        int totalSize;
        final TreeMap<Integer, byte[]> blocks = new TreeMap<>();

        ScreenshotResult buildResult() {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(totalSize);
                for (byte[] block : blocks.values()) {
                    out.write(block);
                }
                byte[] jpgData = out.toByteArray();

                ScreenshotResult r = new ScreenshotResult();
                r.jpgData = jpgData;
                r.totalBlocks = blocks.size();
                r.totalSize = jpgData.length;
                r.deviceAddress = deviceAddress;

                log.info("【Nova截图】重组完成: device=0x{}, blocks={}, size={}KB",
                        String.format("%04X", deviceAddress), blocks.size(), jpgData.length / 1024);
                return r;
            } catch (Exception e) {
                log.error("【Nova截图】重组失败: error={}", e.getMessage());
                return null;
            }
        }
    }

    // ==================== 结果类 ====================

    @Data
    public static class FileTransferResult {
        private ParseAction action;
        private String fileName;
        private String fileExtension;
        private byte[] fileData;
        private int blockSize;
        private int totalBlocks;
        private int totalSize;
        private int deviceAddress;
        private boolean imageFile;
        private boolean videoFile;
        private boolean textFile;

        public static FileTransferResult skip() {
            FileTransferResult r = new FileTransferResult();
            r.action = ParseAction.SKIP;
            return r;
        }
    }

    @Data
    public static class ScreenshotResult {
        private byte[] jpgData;
        private int totalBlocks;
        private int totalSize;
        private int deviceAddress;
    }

    public enum ParseAction {
        REPORT, SKIP
    }
}
