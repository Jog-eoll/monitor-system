package com.publishgateway.udpproxy.protocol.strategy.sigma;

import com.publishgateway.udpproxy.enums.ManufacturerEnum;
import com.publishgateway.udpproxy.protocol.strategy.ProtocolParserStrategy;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseContext;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseResult;
import com.publishgateway.udpproxy.protocol.strategy.context.ReportPayload;
import com.publishgateway.udpproxy.service.FileSignatureCheckResult;
import com.publishgateway.udpproxy.service.FileSignatureManifest;
import com.publishgateway.udpproxy.service.RelayFileSignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Sigma 协议解析策略
 * 封装现有的 JetFileII / Sigma 四阶段解析级联逻辑
 *
 * 解析优先级:
 * 1. JetFileII第一种格式 (SOH/Z/STX协议)
 * 2. Sigma文件传输分包重组
 * 3. JetFileII第二/三种格式 (0x55同步码协议)
 * 4. 智能图片提取 (扫描JPEG/PNG魔数)
 * 5. 原始数据上报 (兜底)
 */
@Slf4j
@Component
public class SigmaProtocolParser implements ProtocolParserStrategy {

    @Autowired
    private ContentCache contentCache;

    @Override
    public String supportedManufacturer() {
        return ManufacturerEnum.SIGMA.getCode();
    }

    @Override
    public ParseResult parse(byte[] data, ParseContext context) {
        Long chainId = context.getChainId();

        // ===== 阶段1: JetFileII第一种格式 =====
        JetFileIIParser.JetFileIIMessage parsedMessage1 = JetFileIIParser.parse(data);
        if (parsedMessage1 != null) {
            return ParseResult.success(buildType1Report(parsedMessage1, context), data.length);
        }

        // ===== 分包重组: Sigma文件传输识别 =====
        SigmaPlayParser.FileTransferResult fileTransfer = SigmaPlayParser.parseFileTransfer(data);
        if (fileTransfer != null) {
            if (fileTransfer.getAction() == SigmaPlayParser.ParseAction.SKIP) {
                return ParseResult.skip();
            }
            // 同一文件在 10s 内已经传输并上报过，跳过重复上报
            // （Sigma 在同一次发送中会重复推送文件传输包；10s 内的重复传输视为同一次）
            if (fileTransfer.getFilePath() != null && contentCache.isFresh(fileTransfer.getFilePath())) {
                log.debug("【数据过滤】文件传输在10s内已上报过，跳过重复上报: file={}", fileTransfer.getFilePath());
                return ParseResult.skip();
            }
            ReportPayload filePayload = buildFileTransferReport(fileTransfer, context);
            // 视频文件若 MinIO 上传失败（minioPath 为空），跳过上报，避免将大文件塞入 payload
            if ("video".equals(filePayload.getContentType()) && filePayload.getMinioPath() == null) {
                log.warn("【Sigma视频】MinIO不可用，跳过上报: file={}", fileTransfer.getFileName());
                return ParseResult.skip();
            }
            return ParseResult.success(filePayload, fileTransfer.getTotalSize());
        }

        // ===== 阶段2: JetFileII第二/三种格式 =====
        JetFileIIParser2.JetFileIIMessage2 parsedMessage2 = JetFileIIParser2.parse(data);
        if (parsedMessage2 != null) {
            byte[] payload = parsedMessage2.getPayload();

            if (payload == null || payload.length <= 50) {
                log.debug("【数据过滤】忽略小数据包: chainId={}, payload={}B, 主命令={}, 子命令={}",
                        chainId,
                        payload != null ? payload.length : 0,
                        parsedMessage2.getMainCmdHex(),
                        parsedMessage2.getSubCmdHex());
                return ParseResult.skip();
            }

            if ("image".equals(parsedMessage2.getContentType())) {
                return ParseResult.success(buildType2Report(parsedMessage2, context), data.length);
            }

            ImageExtractor.ImageResult imageInPayload = ImageExtractor.extract(payload);
            if (imageInPayload != null) {
                return ParseResult.success(
                        buildImageReport(imageInPayload, "JetFileII-" + parsedMessage2.getMessageType(), context),
                        data.length);
            }

            SigmaCommandParser.SigmaCommand sigmaCmd = SigmaCommandParser.parse(
                    payload, parsedMessage2.getMainCmd(), parsedMessage2.getSubCmd());
            if (sigmaCmd != null && "FILE_PLAY".equals(sigmaCmd.getCommandType())) {
                return handleFilePLay(sigmaCmd, chainId, data.length, context);
            }

            if (payload.length > 100) {
                return ParseResult.success(buildType2Report(parsedMessage2, context), data.length);
            } else {
                log.debug("【数据过滤】忽略非图片小数据包: chainId={}, payload={}B", chainId, payload.length);
            }
            return ParseResult.skip();
        }

        // ===== 阶段3: 智能图片提取 =====
        ImageExtractor.ImageResult imageInRaw = ImageExtractor.extract(data);
        if (imageInRaw != null) {
            return ParseResult.success(buildImageReport(imageInRaw, "ImageExtract", context), data.length);
        }

        // ===== 阶段4: 兜底 - 原始数据上报 =====
        if (data.length > 100) {
            return ParseResult.success(buildRawDataReport(data), data.length);
        } else {
            log.debug("【数据过滤】忽略无法识别的小数据包: {}字节", data.length);
        }
        return ParseResult.skip();
    }

    // ==================== 构建各类型上报数据 ====================

    /**
     * 处理 FILE_PLAY 指令：遍历指令中所有文件路径，对每个路径查 Redis 缓存并收集上报数据。
     *
     * 核心逻辑：
     * - Sigma 发送列表时，FILE_PLAY payload 中已包含列表内所有文件的路径
     * - 单发文件时只有一个路径
     * - 对每个路径独立进行缓存查找 + 防重判断
     */
    private ParseResult handleFilePLay(SigmaCommandParser.SigmaCommand sigmaCmd,
                                        Long chainId, int dataLength, ParseContext context) {
        List<String> allPaths = sigmaCmd.getAllFilePaths();
        if (allPaths == null || allPaths.isEmpty()) {
            log.debug("【数据过滤】FILE_PLAY 无文件路径，跳过");
            return ParseResult.skip();
        }

        List<ReportPayload> hits = new ArrayList<>();
        for (String filePath : allPaths) {
            // FILE_PLAY 是播放边界，按缓存命中的路径形成精确批次。
            ContentCache.CacheEntry cached = contentCache.get(filePath);
            if (cached == null) {
                log.debug("【数据过滤】FILE_PLAY 路径未命中缓存，跳过: file={}", filePath);
                continue;
            }
            log.info("【缓存命中】FILE_PLAY 命中缓存: file={}, contentType={}", filePath, cached.getContentType());

            ReportPayload p = new ReportPayload();
            p.setProtocol("Sigma-CachedReplay");
            p.setContentType(cached.getContentType());
            p.setFilePath(filePath);
            int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
            p.setFileName(lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath);
            if (cached.getMinioPath() != null) p.setMinioPath(cached.getMinioPath());
            if (cached.getImageFormat() != null) p.setImageFormat(cached.getImageFormat());
            if (cached.getData() != null) p.setData(cached.getData());
            p.setDescription("Sigma播放指令(缓存命中): " + filePath);
            hits.add(p);
        }

        if (hits.isEmpty()) {
            log.debug("【数据过滤】FILE_PLAY 所有路径均未命中缓存或已过滤，跳过: paths={}", allPaths);
            return ParseResult.skip();
        }

        if (hits.size() == 1) {
            return ParseResult.success(hits.get(0), dataLength);
        }

        // 多个命中：第一个作为主结果，其余作为伴随
        log.info("【批次上报】FILE_PLAY 列表命中 {} 个文件: chainId={}", hits.size(), chainId);
        ReportPayload primary = hits.get(0);
        List<ReportPayload> companions = hits.subList(1, hits.size());
        return ParseResult.successBatch(primary, companions, dataLength);
    }

    private ReportPayload buildType1Report(JetFileIIParser.JetFileIIMessage message, ParseContext context) {
        String safeAddress = message.getAddress() == null ? "??"
                : message.getAddress().replaceAll("[\\x00-\\x1F\\x7F]", "?");

        ReportPayload p = new ReportPayload();
        p.setProtocol("JetFileII-Type1");
        p.setAddress(message.getAddress());
        p.setCommandType(message.getCommandType());
        p.setFileName(message.getFileName());

        if (message.hasTextContent()) {
            p.setContentType("text");
            p.setData(message.getTextContent());
            String textPreview = extractReadableTextPreview(message.getTextContent());
            p.setDescription(textPreview.isEmpty()
                    ? String.format("JetFileII-%s [%s] LED二进制格式", message.getCommandType(), safeAddress)
                    : String.format("JetFileII-%s [%s]: %s", message.getCommandType(), safeAddress, textPreview));
        } else if (message.hasBinaryContent()) {
            byte[] imageData = message.getBinaryContent();
            String minioPath = context.getMinioUploadService().uploadImage(imageData, message.getFileName(), "JPEG");
            p.setContentType("image");
            p.captureImageForAnalysis(imageData);
            if (minioPath != null) {
                p.setMinioPath(minioPath);
            } else {
                p.setScreenshotBase64(Base64.getEncoder().encodeToString(imageData));
            }
            p.setDescription(String.format("JetFileII-%s-%s [%d字节]",
                    message.getCommandType(), safeAddress, imageData.length));
        } else {
            p.setContentType("text");
            p.setData("");
            p.setDescription("JetFileII-空内容");
        }

        log.info("【数据上报】JetFileII第一种格式: 地址={}, 命令={}, 文件={}, 内容={}",
                message.getAddress(), message.getCommandType(),
                message.getFileName(), message.getContentSummary());
        return p;
    }

    private ReportPayload buildType2Report(JetFileIIParser2.JetFileIIMessage2 message, ParseContext context) {
        String contentType = message.getContentType();
        byte[] payload = message.getPayload();

        ReportPayload p = new ReportPayload();
        p.setProtocol("JetFileII-" + message.getMessageType());
        p.setSourceAddr(String.format("0x%04X", message.getSourceAddr()));
        p.setDestAddr(message.getDestinationAddress());
        p.setMainCmd(message.getMainCmdHex());
        p.setSubCmd(message.getSubCmdHex());
        p.setPacketSerial(message.getPacketSerial());

        if ("text".equals(contentType)) {
            p.setContentType("text");
            p.setData(message.getTextContent());
            String textPreview = extractReadableTextPreview(message.getTextContent());
            p.setDescription(textPreview.isEmpty()
                    ? String.format("JetFileII-%s LED二进制格式", message.getMessageType().name())
                    : String.format("JetFileII-%s: %s", message.getMessageType().name(), textPreview));
        } else if ("image".equals(contentType)) {
            String minioPath = context.getMinioUploadService().uploadImage(payload, null, message.getImageFormat());
            p.setContentType("image");
            p.captureImageForAnalysis(payload);
            if (minioPath != null) {
                p.setMinioPath(minioPath);
            } else {
                p.setScreenshotBase64(Base64.getEncoder().encodeToString(payload));
            }
            p.setImageFormat(message.getImageFormat());
            p.setDescription(String.format("JetFileII-%s-%s图片 [%d字节]",
                    message.getMessageType(), message.getImageFormat(), payload.length));
        } else {
            p.setContentType("binary");
            p.setData(Base64.getEncoder().encodeToString(payload));
            p.setDescription(String.format("JetFileII-%s-二进制数据 [%d字节]",
                    message.getMessageType(), payload.length));
        }

        log.info("【数据上报】JetFileII{}: 源={}, 目的={}, 主命令={}, 子命令={}, 内容类型={}, 大小={}B",
                message.getMessageType(),
                String.format("0x%04X", message.getSourceAddr()),
                message.getDestinationAddress(),
                message.getMainCmdHex(), message.getSubCmdHex(),
                contentType, payload != null ? payload.length : 0);
        return p;
    }

    private ReportPayload buildImageReport(ImageExtractor.ImageResult image, String protocol, ParseContext context) {
        byte[] imageData = image.getImageData();
        String minioPath = context.getMinioUploadService().uploadImage(imageData, null, image.getFormat());
        String description = String.format("%s-%s图片 [%d字节,偏移=%d]",
                protocol, image.getFormat(), imageData.length, image.getOffset());

        log.info("【智能提取】成功提取{}图片: 大小={}字节, 偏移={}, 协议={}",
                image.getFormat(), imageData.length, image.getOffset(), protocol);

        if (minioPath != null) {
            ReportPayload payload = ReportPayload.ofImage(protocol, minioPath, image.getFormat(), description);
            payload.captureImageForAnalysis(imageData);
            return payload;
        } else {
            return ReportPayload.ofImageBase64(protocol,
                    Base64.getEncoder().encodeToString(imageData), image.getFormat(), description);
        }
    }

    private ReportPayload buildFileTransferReport(SigmaPlayParser.FileTransferResult transfer, ParseContext context) {
        log.info("【文件类型判断】fileName={}, ext={}, isImage={}, isText={}",
                transfer.getFileName(), transfer.getFileExtension(),
                transfer.isImageFile(), transfer.isTextFile());

        ReportPayload p = new ReportPayload();
        p.setProtocol("Sigma-FileTransfer");
        p.setFilePath(transfer.getFilePath());
        p.setFileName(transfer.getFileName());
        p.setFileExtension(transfer.getFileExtension());
        p.setSourceAddr(String.format("0x%04X", transfer.getSourceAddr()));
        p.setDestAddr(transfer.getDestAddress());
        p.setTotalPackets(transfer.getTotalPackets());
        p.setTotalSize(transfer.getTotalSize());

        byte[] fileData = transfer.getReassembledData();

        if (fileData != null && fileData.length > 0) {
            applyRelayFileSignatureAudit(transfer, context, p, fileData);
            if (transfer.isImageFile()) {
                String imageFormat = detectImageFormat(fileData);
                String minioPath = context.getMinioUploadService().uploadImage(fileData, transfer.getFileName(),
                        imageFormat != null ? imageFormat : transfer.getFileExtension());
                p.setContentType("image");
                p.captureImageForAnalysis(fileData);
                if (minioPath != null) {
                    p.setMinioPath(minioPath);
                } else {
                    p.setScreenshotBase64(Base64.getEncoder().encodeToString(fileData));
                }
                if (imageFormat != null) {
                    p.setImageFormat(imageFormat);
                }
                p.setDescription(String.format("Sigma图片文件传输: %s [%d包, %dKB]",
                        transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
            } else if (transfer.isVideoFile()) {
                // 视频文件：上传到 MinIO，无 Base64 降级（文件太大）
                if (fileData != null && fileData.length > 0) {
                    String minioPath = context.getMinioUploadService().uploadVideo(fileData, transfer.getFileName());
                    if (minioPath == null) {
                        log.warn("【Sigma视频文件】MinIO上传失败，跳过上报: file={}", transfer.getFileName());
                        // minioPath 为空时不上报，返回 skip 由调用方处理
                        p.setContentType("video");
                        p.setDescription(String.format("Sigma视频文件传输(MinIO不可用): %s [%d包, %dMB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / (1024 * 1024)));
                        // 调用方（parse方法）需检测 minioPath 是否为空决定是否 skip
                    } else {
                        p.setContentType("video");
                        p.setMinioPath(minioPath);
                        p.setDescription(String.format("Sigma视频文件传输: %s [%d包, %dMB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / (1024 * 1024)));
                    }
                    log.info("【Sigma视频文件】重组完成: file={}, size={}MB, minioPath={}",
                            transfer.getFileName(), fileData.length / (1024 * 1024), p.getMinioPath());
                } else {
                    p.setContentType("file_reference");
                    p.setData(transfer.getFileName());
                    p.setDescription(String.format("Sigma视频文件引用: %s", transfer.getFilePath()));
                }
            } else if (transfer.isTextFile()) {
                p.setContentType("text");
                if (transfer.needsTextDecoding()) {
                    try {
                        String textContent = new String(fileData, StandardCharsets.UTF_8);
                        if (textContent.contains("\uFFFD")) {
                            textContent = new String(fileData, "GBK");
                            log.info("【文本编码】使用 GBK 编码: file={}", transfer.getFileName());
                        }
                        p.setData(textContent);
                        p.setDescription(String.format("Sigma文本文件传输: %s [%d包, %dKB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
                        log.info("【Sigma文本文件】重组成功: file={}, size={}字节, 内容预览={}",
                                transfer.getFileName(), fileData.length,
                                textContent.length() > 100 ? textContent.substring(0, 100) + "..." : textContent);
                    } catch (Exception e) {
                        log.warn("【文本解码失败】回退为 Base64: file={}", transfer.getFileName());
                        p.setData(Base64.getEncoder().encodeToString(fileData));
                        p.setDescription(String.format("Sigma文本文件传输(解码失败): %s [%d包, %dKB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
                    }
                } else if ("pmg".equalsIgnoreCase(transfer.getFileExtension())) {
                    String pmgText = PmgTextExtractor.extractText(fileData);
                    if (pmgText != null && !pmgText.isEmpty()) {
                        p.setData(pmgText);
                        p.setDescription(String.format("PMG文件文本内容: %s [%d包, 文本='%s']",
                                transfer.getFilePath(), transfer.getTotalPackets(), pmgText));
                        log.info("【PMG文件】提取文本成功: file={}, text='{}'", transfer.getFileName(), pmgText);
                    } else {
                        p.setData(Base64.getEncoder().encodeToString(fileData));
                        p.setDescription(String.format("PMG文件(文本提取失败): %s [%d包, %dKB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
                        log.info("【PMG文件】文本提取失败,回退Base64: file={}, size={}字节",
                                transfer.getFileName(), fileData.length);
                    }
                } else {
                    String nmgText = tryExtractNmgText(fileData);
                    if (nmgText != null && !nmgText.isEmpty()) {
                        p.setData(nmgText);
                        p.setDescription(String.format("Sigma文本文件传输: %s [%d包, %dKB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
                        log.info("【Sigma Nmg文件】文本提取成功: file={}, 内容='{}'",
                                transfer.getFileName(), nmgText.length() > 100 ? nmgText.substring(0, 100) + "..." : nmgText);
                    } else {
                        p.setData(Base64.getEncoder().encodeToString(fileData));
                        p.setDescription(String.format("Sigma文本文件传输(二进制格式): %s [%d包, %dKB]",
                                transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
                        log.info("【Sigma Nmg文件】文本提取失败,回退Base64: file={}, size={}字节",
                                transfer.getFileName(), fileData.length);
                    }
                }
            } else {
                p.setContentType("binary");
                p.setData(Base64.getEncoder().encodeToString(fileData));
                p.setDescription(String.format("Sigma文件传输: %s [%d包, %dKB]",
                        transfer.getFilePath(), transfer.getTotalPackets(), fileData.length / 1024));
            }
        } else {
            p.setContentType(transfer.isImageFile() ? "image_reference" : "file_reference");
            p.setData(transfer.getFileName());
            p.setDescription(String.format("Sigma文件传输: %s", transfer.getFilePath()));
        }

        log.info("【Sigma文件传输上报】file={}, type={}, contentType={}, size={}KB, packets={}",
                transfer.getFilePath(), transfer.getFileExtension(), p.getContentType(),
                transfer.getTotalSize() / 1024, transfer.getTotalPackets());

        // 统一写入缓存（图片/视频缓存 minioPath，文字缓存 data）
        if (transfer.getFilePath() != null) {
            contentCache.put(transfer.getFilePath(), p.getContentType(),
                    p.getMinioPath(), p.getImageFormat(), p.getData());
            log.info("【内容缓存】写入: filePath={}, contentType={}",
                    transfer.getFilePath(), p.getContentType());

            // 标记该文件已由文件传输路径上报（60s 有效），FILE_PLAY 展开伴随文件时据此跳过
            contentCache.markTransferred(transfer.getFilePath());
        }

        return p;
    }

    private void applyRelayFileSignatureAudit(SigmaPlayParser.FileTransferResult transfer, ParseContext context,
                                              ReportPayload payload, byte[] fileData) {
        RelayFileSignatureService signatureService = context.getRelayFileSignatureService();
        if (signatureService == null || fileData == null || fileData.length == 0) {
            return;
        }
        try {
            FileSignatureManifest manifest = new FileSignatureManifest();
            manifest.setRuleId(context.getRuleId());
            manifest.setChainId(context.getChainId());
            manifest.setSourceIp(context.getSourceIp());
            manifest.setTargetIp(context.getBoardIp());
            manifest.setTargetPort(context.getBoardPort());
            manifest.setFileName(transfer.getFileName());
            manifest.setFilePath(transfer.getFilePath());
            manifest.setFileSize(fileData.length);
            manifest.setTotalPackets(transfer.getTotalPackets());
            manifest.setFileHash(FileSignatureManifest.sha256Hex(fileData));
            manifest.setCompletedAt(System.currentTimeMillis());

            FileSignatureCheckResult result = signatureService.signAndVerify(manifest);
            payload.setRelaySignatureEnabled(result.isEnabled());
            payload.setRelaySignatureSigned(result.isSigned());
            payload.setRelaySignatureVerified(result.isVerified());
            payload.setRelaySignatureAllowed(result.isAllowed());
            payload.setRelaySignatureAuditOnly(result.isAuditOnly());
            payload.setRelaySignatureMode(result.getMode());
            payload.setRelaySignatureAlgorithm(result.getAlgorithm());
            payload.setRelaySignatureHashAlgorithm(result.getHashAlgorithm());
            payload.setRelaySignatureFileHash(result.getFileHash());
            payload.setRelaySignatureError(result.getErrorMessage());
            payload.setRelaySignatureSource(result.getSignatureSource());
            payload.setRelaySignatureClientId(result.getClientId());
            payload.setRelaySignatureClientCertId(result.getClientCertId());
            payload.setRelaySignatureFileId(result.getFileId());
            payload.setRelaySignatureClientRecordMatched(result.isClientRecordMatched());

            if (!result.isAllowed() && signatureService.isEnforceMode()) {
                log.warn("[RelayFileSignature] enforce mode found failed file audit, report continues in v1: file={}, error={}",
                        transfer.getFilePath(), result.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("[RelayFileSignature] audit failed but report continues: file={}, error={}",
                    transfer.getFilePath(), e.getMessage(), e);
            payload.setRelaySignatureEnabled(true);
            payload.setRelaySignatureAllowed(true);
            payload.setRelaySignatureError("AUDIT_EXCEPTION: " + e.getMessage());
        }
    }

    private ReportPayload buildRawDataReport(byte[] data) {
        int debugLen = Math.min(100, data.length);
        StringBuilder hexDebug = new StringBuilder();
        for (int i = 0; i < debugLen; i++) {
            hexDebug.append(String.format("%02X ", data[i]));
            if ((i + 1) % 16 == 0) {
                hexDebug.append("\n                ");
            }
        }
        log.warn("【协议解析失败】无法识别的数据包（前{}字节十六进制）:\n                {}",
                debugLen, hexDebug.toString());

        String base64Data = Base64.getEncoder().encodeToString(data);
        log.info("【数据上报】原始数据: 前30字节Base64={}",
                base64Data.length() > 30 ? base64Data.substring(0, 30) + "..." : base64Data);
        return ReportPayload.ofBinary("Unknown", base64Data,
                String.format("无法识别的协议数据 [%d字节]", data.length));
    }

    // ==================== 辅助方法 ====================

    private String tryExtractNmgText(byte[] fileData) {
        try {
            JetFileIIParser.JetFileIIMessage msg = JetFileIIParser.parse(fileData);
            if (msg != null && msg.hasTextContent()) {
                return msg.getTextContent();
            }
        } catch (Exception e) {
            log.debug("【Nmg文本提取】JetFileIIParser解析失败: {}", e.getMessage());
        }
        return null;
    }

    private String extractReadableTextPreview(String text) {
        if (text == null || text.isEmpty()) return "";

        final String NMG_MARKER = "NoteNmg file version:";
        int markerIdx = text.indexOf(NMG_MARKER);
        if (markerIdx >= 0) {
            String versionPart = text.substring(markerIdx + NMG_MARKER.length());
            int end = 0;
            while (end < versionPart.length() && end < 20
                    && versionPart.charAt(end) > 0x20) {
                end++;
            }
            String version = end > 0 ? versionPart.substring(0, end) : "unknown";
            return "NoteNmg LED程序 [" + version + "]";
        }

        StringBuilder current = new StringBuilder();
        StringBuilder best = new StringBuilder();
        for (char c : text.toCharArray()) {
            boolean printable = (c >= 0x20 && c <= 0x7E)
                    || (c >= 0x4E00 && c <= 0x9FFF)
                    || (c >= 0x3000 && c <= 0x303F)
                    || (c >= 0xFF00 && c <= 0xFFEF);
            if (printable) {
                current.append(c);
            } else {
                if (current.length() >= 5 && current.length() > best.length()) {
                    best = new StringBuilder(current);
                }
                current.setLength(0);
            }
        }
        if (current.length() >= 5 && current.length() > best.length()) {
            best = current;
        }

        String result = best.toString().trim();
        if (result.length() > 60) {
            result = result.substring(0, 60) + "...";
        }
        return result;
    }

    private String detectImageFormat(byte[] data) {
        if (data == null || data.length < 4) return null;
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) return "JPEG";
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return "PNG";
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) return "GIF";
        if (data[0] == 0x42 && data[1] == 0x4D) return "BMP";
        return null;
    }
}
