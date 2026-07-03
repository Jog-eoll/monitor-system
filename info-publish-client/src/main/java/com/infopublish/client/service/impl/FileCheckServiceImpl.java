package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.precheck.FailedFile;
import com.infopublish.client.entity.dto.precheck.PassedFile;
import com.infopublish.client.entity.dto.sigma.InnerFile;
import com.infopublish.client.enums.CheckFailCode;
import com.infopublish.client.enums.CheckStage;
import com.infopublish.client.service.FileCheckService;
import com.infopublish.client.service.SigmaApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 文件校验服务实现
 * <p>
 * GENERIC_FILE_CHECK_V1 最小实现：
 * <ul>
 *     <li>文件字节非空校验</li>
 *     <li>文件大小与元信息一致性校验</li>
 *     <li>MIME 类型与实际内容魔数匹配校验</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class FileCheckServiceImpl implements FileCheckService {

    @Resource
    private SigmaApiClient sigmaApiClient;

    @Override
    public FileCheckOutcome checkFile(String sigmaBaseUrl, String playlistId, InnerFile innerFile) {
        String innerFileId = innerFile.getInnerFileId();
        String fileName = innerFile.getFileName();
        log.info("[文件校验] 开始校验: innerFileId={}, fileName={}, size={}, mimeType={}",
                innerFileId, fileName, innerFile.getSize(), innerFile.getMimeType());

        // ========== 1. 下载文件字节 ==========
        byte[] fileBytes;
        try {
            fileBytes = sigmaApiClient.downloadFileContent(sigmaBaseUrl, playlistId, innerFileId);
        } catch (Exception e) {
            log.error("[文件校验] 下载异常: innerFileId={}, error={}", innerFileId, e.getMessage(), e);
            return FileCheckOutcome.fail(new FailedFile(
                    innerFileId, fileName,
                    CheckStage.DOWNLOAD.name(),
                    CheckFailCode.FILE_CHECK_FAILED.name(),
                    "文件下载异常: " + e.getMessage()));
        }

        if (fileBytes == null || fileBytes.length == 0) {
            log.warn("[文件校验] 下载内容为空: innerFileId={}", innerFileId);
            return FileCheckOutcome.fail(new FailedFile(
                    innerFileId, fileName,
                    CheckStage.DOWNLOAD.name(),
                    CheckFailCode.FILE_CHECK_FAILED.name(),
                    "文件下载内容为空"));
        }

        // ========== 2. 文件大小一致性校验 ==========
        long expectedSize = innerFile.getSize();
        long actualSize = fileBytes.length;
        if (expectedSize > 0 && expectedSize != actualSize) {
            log.warn("[文件校验] 文件大小不一致: innerFileId={}, expected={}, actual={}",
                    innerFileId, expectedSize, actualSize);
            return FileCheckOutcome.fail(new FailedFile(
                    innerFileId, fileName,
                    CheckStage.FILE_CHECK.name(),
                    CheckFailCode.FILE_CHECK_FAILED.name(),
                    "文件大小不一致: 期望 " + expectedSize + " 字节, 实际 " + actualSize + " 字节"));
        }

        // ========== 3. MIME 类型魔数校验 ==========
        String declaredMime = innerFile.getMimeType();
        if (declaredMime != null && !declaredMime.isEmpty()) {
            String detectedMime = detectMimeType(fileBytes);
            if (detectedMime != null && !isMimeCompatible(declaredMime, detectedMime)) {
                log.warn("[文件校验] MIME类型不匹配: innerFileId={}, declared={}, detected={}",
                        innerFileId, declaredMime, detectedMime);
                return FileCheckOutcome.fail(new FailedFile(
                        innerFileId, fileName,
                        CheckStage.FILE_CHECK.name(),
                        CheckFailCode.FILE_CHECK_FAILED.name(),
                        "MIME 类型不匹配: 声明 " + declaredMime + ", 实际 " + detectedMime));
            }
        }

        // ========== 校验通过 ==========
        log.info("[文件校验] 校验通过: innerFileId={}, fileName={}", innerFileId, fileName);
        return FileCheckOutcome.pass(new PassedFile(innerFileId, fileName));
    }

    // ========== 内部方法 ==========

    /**
     * 根据文件魔数检测 MIME 类型（最小实现）
     */
    private String detectMimeType(byte[] data) {
        if (data.length < 4) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }
        // GIF: 47 49 46 38
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38) {
            return "image/gif";
        }
        // BMP: 42 4D
        if (data[0] == 0x42 && data[1] == 0x4D) {
            return "image/bmp";
        }
        // MP4: 00 00 00 xx 66 74 79 70 (ftyp)
        if (data.length >= 8 && data[4] == 0x66 && data[5] == 0x74 && data[6] == 0x79 && data[7] == 0x70) {
            return "video/mp4";
        }
        // AVI: 52 49 46 46
        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46) {
            return "video/avi";
        }
        // XML: 3C 3F 78 6D (<?xml)
        if (data[0] == 0x3C && data[1] == 0x3F && data[2] == 0x78 && data[3] == 0x6D) {
            return "application/xml";
        }
        return null;
    }

    /**
     * 判断两个 MIME 类型是否兼容
     * <p>
     * 宽松比较：主类型相同即视为兼容（如 image/jpeg 和 image/jpg）。
     * </p>
     */
    private boolean isMimeCompatible(String declared, String detected) {
        if (declared == null || detected == null) {
            return true;
        }
        // 完全匹配
        if (declared.equalsIgnoreCase(detected)) {
            return true;
        }
        // 主类型匹配
        String declaredMain = declared.split("/")[0].toLowerCase();
        String detectedMain = detected.split("/")[0].toLowerCase();
        return declaredMain.equals(detectedMain);
    }
}
