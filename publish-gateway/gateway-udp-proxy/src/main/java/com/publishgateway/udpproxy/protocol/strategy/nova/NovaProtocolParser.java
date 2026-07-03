package com.publishgateway.udpproxy.protocol.strategy.nova;

import com.publishgateway.udpproxy.enums.ManufacturerEnum;
import com.publishgateway.udpproxy.protocol.strategy.ProtocolParserStrategy;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseContext;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseResult;
import com.publishgateway.udpproxy.protocol.strategy.context.ReportPayload;
import com.publishgateway.udpproxy.protocol.strategy.sigma.ImageExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Nova（诺瓦）交通协议解析策略
 *
 * 解析诺瓦交通协议标准版的帧格式 (0xAA...0xCC)
 * 帧结构: 起始符(0xAA) + 设备地址(2B) + 指令码(1B) + 数据域(nB) + 结束符(0xCC) + CRC16(2B)
 *
 * 解析级联优先级:
 * 1. Nova帧解析 → 指令码路由
 *    - 0x11/0x13/0xF9: 文件传输分包重组 → 图片/视频/文本上报
 *    - 0x81: 截图数据分块重组 → JPG图片上报
 *    - 0x2E: 当前播放内容 → 文本上报
 *    - 0x3B: 播放列表全部内容 → 文本上报
 *    - 0x88: 屏体内容局部更新 → 图片/文本上报
 *    - 0x49: FTP文件下发 → 文件引用上报
 *    - 0x05/0x06: 开关屏控制 → 日志记录，跳过
 *    - 0x00: 心跳 → 跳过
 *    - 0x01/0x02: 设备状态 → 日志记录，跳过
 * 2. 智能图片提取 (扫描JPEG/PNG魔数)
 * 3. 原始数据上报 (兜底)
 *
 * @Author: zyh
 * @Date: 2026/4/13
 */
@Slf4j
@Component
public class NovaProtocolParser implements ProtocolParserStrategy {

    @Override
    public String supportedManufacturer() {
        return ManufacturerEnum.NOVA.getCode();
    }

    @Override
    public ParseResult parse(byte[] data, ParseContext context) {
        ParseResult rawStreamResult = parseRawPublishedContent(data, context);
        if (rawStreamResult != null && rawStreamResult.isSuccess()) {
            return rawStreamResult;
        }

        // ===== 阶段1: Nova帧解析 =====
        List<NovaFrameParser.NovaFrame> frames = NovaFrameParser.parseFrames(data);

        if (!frames.isEmpty()) {
            for (NovaFrameParser.NovaFrame frame : frames) {
                ParseResult result = processFrame(frame, data.length, context);
                if (result != null && result.isSuccess()) {
                    return result;
                }
            }
            // 所有帧都是心跳/ACK等控制指令，无需上报
            return ParseResult.skip();
        }

        // ===== 阶段2: 智能图片提取（可能是裸JPG/PNG数据流） =====
        ImageExtractor.ImageResult imageInRaw = ImageExtractor.extract(data);
        if (imageInRaw != null) {
            return ParseResult.success(
                    buildImageReport(imageInRaw, "Nova-ImageExtract", context), data.length);
        }

        // ===== 阶段3: 兜底 - 原始数据上报 =====
        if (data.length > 100) {
            return ParseResult.success(buildRawDataReport(data), data.length);
        }

        log.debug("【Nova过滤】忽略无法识别的小数据包: {}字节", data.length);
        return ParseResult.skip();
    }

    // ==================== 帧指令路由 ====================

    /**
     * 根据指令码路由到对应的处理逻辑
     * 返回 null 表示本帧无需上报，外层继续处理下一帧
     */
    private ParseResult parseRawPublishedContent(byte[] data, ParseContext context) {
        if (data == null || data.length < 32) {
            return null;
        }

        ImageExtractor.ImageResult image = ImageExtractor.extract(data);
        if (image != null) {
            return ParseResult.success(buildImageReport(image, "Nova-RawStream", context), data.length);
        }

        String videoExt = detectVideoExtension(data);
        if (videoExt != null) {
            String fileName = "nova-stream." + videoExt;
            String minioPath = context.getMinioUploadService().uploadVideo(data, fileName);
            if (minioPath == null) {
                log.warn("[Nova-RawStream] video upload failed, skip report: size={}MB, ext={}",
                        data.length / (1024 * 1024), videoExt);
                return ParseResult.skip();
            }
            ReportPayload payload = new ReportPayload();
            payload.setProtocol("Nova-RawStream");
            payload.setContentType("video");
            payload.setFileName(fileName);
            payload.setFileExtension(videoExt);
            payload.setMinioPath(minioPath);
            payload.setTotalSize(data.length);
            payload.setDescription(String.format("Nova dynamic-port video stream [%dMB]",
                    data.length / (1024 * 1024)));
            return ParseResult.success(payload, data.length);
        }

        String text = tryExtractText(data);
        if (text != null && text.length() >= 4) {
            ReportPayload payload = ReportPayload.ofText("Nova-RawStream", text,
                    text.length() > 80 ? text.substring(0, 80) + "..." : text);
            payload.setTotalSize(data.length);
            return ParseResult.success(payload, data.length);
        }

        return null;
    }

    private ParseResult processFrame(NovaFrameParser.NovaFrame frame, int totalSize, ParseContext context) {
        int cmd = frame.getCommandCode();
        byte[] cmdData = frame.getDataField();
        int deviceAddr = frame.getDeviceAddress();
        String sourceIp = context.getSourceIp();

        switch (cmd) {
            // ===== 文件传输指令（核心，拦截下发内容） =====
            case 0x11: // 文件名发送（平台→板）
                return handleFileNameSend(frame, context);
            case 0x13: // 文件内容发送（平台→板）
                return handleFileContentSend(frame, context);
            case 0xF9: // 文件发送完毕（板→平台回复）
                return handleFileTransferComplete(frame, context);

            // ===== 截图指令 =====
            case 0x80: // 获取播放截图请求（平台→板）
                NovaFileAssembler.handleScreenshotRequest(deviceAddr, cmdData, sourceIp);
                return ParseResult.skip();
            case 0x81: // 截图数据回复（板→平台，分块JPG）
                return handleScreenshotData(frame, context);

            // ===== 播放内容查询回复 =====
            case 0x2E: // 当前播放内容回复
                return handlePlaybackContent(frame, context);
            case 0x3B: // 播放列表全部内容回复
                return handlePlaylistContent(frame, context);

            // ===== 屏体内容实时更新 =====
            case 0x88: // 屏体内容局部更新（平台→板）
                return handleScreenUpdate(frame, context);

            // ===== FTP 下发 =====
            case 0x49: // FTP 文件传输
                return handleFtpDownload(frame, context);

            // ===== 开关屏控制（记录日志，不上报） =====
            case 0x05:
                logScreenControl(frame);
                return ParseResult.skip();
            case 0x06: // 开关屏回复
                logScreenControlReply(frame);
                return ParseResult.skip();

            // ===== 心跳 & 设备状态（跳过或仅记录） =====
            case 0x00:
                log.debug("【Nova心跳】device=0x{}", frame.getDeviceAddressHex());
                return ParseResult.skip();
            case 0x01: // 查询设备状态（请求，无数据域）
                return ParseResult.skip();
            case 0x02: // 设备状态回复
                logDeviceStatus(frame);
                return ParseResult.skip();

            // ===== ACK类回复指令（跳过） =====
            case 0x12: // 文件名ACK
            case 0x14: // 文件内容ACK
            case 0x1C: // 指定播放列表ACK
            case 0x7D: // 文件清理ACK
            case 0x50: // FTP下载ACK
            case 0x89: // 屏体更新ACK
            case 0x0A: // 设置日期时间ACK
            case 0x0E: // 设备复位ACK
            case 0x08: // 亮度控制回复
            case 0x18: // 自动亮度ACK
            case 0x1A: // 设置屏体参数ACK
            case 0x1E: // 设置输入源ACK
                return ParseResult.skip();

            // ===== 其他指令：尝试提取有效内容 =====
            default:
                return handleUnknownCommand(frame, totalSize, context);
        }
    }

    // ==================== 文件传输处理 ====================

    private ParseResult handleFileNameSend(NovaFrameParser.NovaFrame frame, ParseContext context) {
        NovaFileAssembler.FileTransferResult result = NovaFileAssembler.handleFileNameSend(
                frame.getDeviceAddress(), frame.getDataField(), context.getSourceIp());
        if (result == null || result.getAction() == NovaFileAssembler.ParseAction.SKIP) {
            return ParseResult.skip();
        }
        return ParseResult.success(buildFileTransferReport(result, context), result.getTotalSize());
    }

    private ParseResult handleFileContentSend(NovaFrameParser.NovaFrame frame, ParseContext context) {
        NovaFileAssembler.FileTransferResult result = NovaFileAssembler.handleFileContentSend(
                frame.getDeviceAddress(), frame.getDataField(), context.getSourceIp());
        if (result == null || result.getAction() == NovaFileAssembler.ParseAction.SKIP) {
            return ParseResult.skip();
        }
        return ParseResult.success(buildFileTransferReport(result, context), result.getTotalSize());
    }

    private ParseResult handleFileTransferComplete(NovaFrameParser.NovaFrame frame, ParseContext context) {
        NovaFileAssembler.FileTransferResult result = NovaFileAssembler.handleFileTransferComplete(
                frame.getDeviceAddress(), frame.getDataField(), context.getSourceIp());
        if (result == null || result.getAction() == NovaFileAssembler.ParseAction.SKIP) {
            return ParseResult.skip();
        }
        ReportPayload payload = buildFileTransferReport(result, context);
        // 视频文件若 MinIO 上传失败，跳过上报（避免大文件塞入 JSON）
        if ("video".equals(payload.getContentType()) && payload.getMinioPath() == null) {
            log.warn("【Nova视频】MinIO不可用，跳过上报: file={}", result.getFileName());
            return ParseResult.skip();
        }
        return ParseResult.success(payload, result.getTotalSize());
    }

    // ==================== 截图处理 ====================

    private ParseResult handleScreenshotData(NovaFrameParser.NovaFrame frame, ParseContext context) {
        NovaFileAssembler.ScreenshotResult result = NovaFileAssembler.handleScreenshotData(
                frame.getDeviceAddress(), frame.getDataField(), context.getSourceIp());
        if (result == null) {
            return ParseResult.skip(); // 截图尚未完成
        }

        byte[] jpgData = result.getJpgData();
        if (jpgData == null || jpgData.length < 100) {
            log.warn("【Nova截图】数据太小: size={}B", jpgData != null ? jpgData.length : 0);
            return ParseResult.skip();
        }

        String minioPath = context.getMinioUploadService().uploadImage(jpgData, "screenshot.jpg", "JPEG");

        ReportPayload p = new ReportPayload();
        p.setProtocol("Nova-Screenshot");
        p.setContentType("image");
        p.setImageFormat("JPEG");
        p.captureImageForAnalysis(jpgData);
        p.setSourceAddr(String.format("0x%04X", result.getDeviceAddress()));
        p.setTotalPackets(result.getTotalBlocks());
        p.setTotalSize(result.getTotalSize());
        if (minioPath != null) {
            p.setMinioPath(minioPath);
        } else {
            p.setScreenshotBase64(Base64.getEncoder().encodeToString(jpgData));
        }
        p.setDescription(String.format("Nova播放截图 [%d块, %dKB]", result.getTotalBlocks(), jpgData.length / 1024));

        log.info("【Nova截图上报】device=0x{}, blocks={}, size={}KB, minioPath={}",
                String.format("%04X", result.getDeviceAddress()), result.getTotalBlocks(),
                jpgData.length / 1024, minioPath);
        return ParseResult.success(p, result.getTotalSize());
    }

    // ==================== 播放内容处理 ====================

    /**
     * 处理当前播放内容回复 (0x2E)
     * 数据域: 开关屏标志(1B) + 播放类型标志(1B) + 播放列表号(1B) + 内容头(8B) + 当前播放内容(nB)
     */
    private ParseResult handlePlaybackContent(NovaFrameParser.NovaFrame frame, ParseContext context) {
        byte[] data = frame.getDataField();
        if (data == null || data.length < 3) {
            return ParseResult.skip();
        }

        int screenState = data[0] & 0xFF; // 1=开屏, 2=关屏
        int playlistNum = data[2] & 0xFF;

        if (screenState == 2) {
            log.info("【Nova播放】设备关屏状态，跳过内容上报: device=0x{}", frame.getDeviceAddressHex());
            return ParseResult.skip();
        }

        // 内容头(8B) + 播放内容(nB)，偏移 3+8=11
        String content = "";
        if (data.length > 11) {
            content = new String(data, 11, data.length - 11, StandardCharsets.UTF_8);
        }

        if (content.isEmpty()) {
            log.debug("【Nova播放】播放内容为空（可能在切换中）: device=0x{}", frame.getDeviceAddressHex());
            return ParseResult.skip();
        }

        ReportPayload p = ReportPayload.ofText("Nova-PlayContent", content,
                String.format("Nova当前播放内容 [列表=%d]: %s", playlistNum,
                        content.length() > 60 ? content.substring(0, 60) + "..." : content));
        p.setSourceAddr(frame.getDeviceAddressHex());

        log.info("【Nova播放内容】device=0x{}, 列表={}, 内容长度={}",
                frame.getDeviceAddressHex(), playlistNum, content.length());
        return ParseResult.success(p, data.length);
    }

    /**
     * 处理播放列表全部内容回复 (0x3B)
     * 数据域: 列表编号(1B) + 所有内容(nB, UTF8)
     */
    private ParseResult handlePlaylistContent(NovaFrameParser.NovaFrame frame, ParseContext context) {
        byte[] data = frame.getDataField();
        if (data == null || data.length < 2) {
            return ParseResult.skip();
        }

        int listNum = data[0] & 0xFF;
        String content = new String(data, 1, data.length - 1, StandardCharsets.UTF_8);

        if (content.isEmpty()) {
            return ParseResult.skip();
        }

        ReportPayload p = ReportPayload.ofText("Nova-PlaylistContent", content,
                String.format("Nova播放列表内容 [列表=%d, %d字节]", listNum, content.length()));
        p.setSourceAddr(frame.getDeviceAddressHex());

        log.info("【Nova播放列表】device=0x{}, 列表={}, 内容长度={}",
                frame.getDeviceAddressHex(), listNum, content.length());
        return ParseResult.success(p, data.length);
    }

    // ==================== 屏体更新处理 ====================

    /**
     * 处理屏体内容局部更新 (0x88)
     * 数据域: 实时更新操作(1B, 0=移除/1=更新) + 更新区域索引(1B, 0-9) + 显示内容(nB)
     */
    private ParseResult handleScreenUpdate(NovaFrameParser.NovaFrame frame, ParseContext context) {
        byte[] data = frame.getDataField();
        if (data == null || data.length < 3) {
            return ParseResult.skip();
        }

        int operation = data[0] & 0xFF;
        int regionIndex = data[1] & 0xFF;

        if (operation == 0) {
            log.info("【Nova屏体更新】移除区域: device=0x{}, 区域={}", frame.getDeviceAddressHex(), regionIndex);
            return ParseResult.skip();
        }

        byte[] contentData = new byte[data.length - 2];
        System.arraycopy(data, 2, contentData, 0, contentData.length);

        // 尝试提取图片
        ImageExtractor.ImageResult img = ImageExtractor.extract(contentData);
        if (img != null) {
            ReportPayload p = buildImageReport(img, "Nova-ScreenUpdate", context);
            p.setSourceAddr(frame.getDeviceAddressHex());
            p.setDescription(String.format("Nova屏体局部更新-%s [区域=%d, %dKB]",
                    img.getFormat(), regionIndex, img.getImageData().length / 1024));
            return ParseResult.success(p, data.length);
        }

        // 尝试作为文本
        String textContent = tryExtractText(contentData);
        if (textContent != null && !textContent.isEmpty()) {
            ReportPayload p = ReportPayload.ofText("Nova-ScreenUpdate", textContent,
                    String.format("Nova屏体局部更新 [区域=%d]: %s", regionIndex,
                            textContent.length() > 60 ? textContent.substring(0, 60) + "..." : textContent));
            p.setSourceAddr(frame.getDeviceAddressHex());
            return ParseResult.success(p, data.length);
        }

        // 无法识别的二进制内容
        if (contentData.length > 50) {
            ReportPayload p = ReportPayload.ofBinary("Nova-ScreenUpdate",
                    Base64.getEncoder().encodeToString(contentData),
                    String.format("Nova屏体局部更新-二进制 [区域=%d, %dB]", regionIndex, contentData.length));
            p.setSourceAddr(frame.getDeviceAddressHex());
            return ParseResult.success(p, data.length);
        }

        return ParseResult.skip();
    }

    // ==================== FTP 下发处理 ====================

    /**
     * 处理 FTP 文件传输指令 (0x49)
     * 数据域: 单文件 FTP URL (UTF8)，如 ftp://user:password@192.168.0.108:21/play001.lst
     */
    private ParseResult handleFtpDownload(NovaFrameParser.NovaFrame frame, ParseContext context) {
        byte[] data = frame.getDataField();
        if (data == null || data.length < 10) {
            return ParseResult.skip();
        }

        String ftpUrl = new String(data, StandardCharsets.UTF_8).trim();
        String fileName = ftpUrl;
        int lastSlash = ftpUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < ftpUrl.length() - 1) {
            fileName = ftpUrl.substring(lastSlash + 1);
        }

        ReportPayload p = new ReportPayload();
        p.setProtocol("Nova-FtpTransfer");
        p.setContentType("file_reference");
        p.setData(fileName);
        p.setFileName(fileName);
        p.setFilePath(ftpUrl);
        p.setSourceAddr(frame.getDeviceAddressHex());
        p.setDescription(String.format("Nova FTP文件下发: %s", ftpUrl));

        log.info("【Nova FTP下发】device=0x{}, url={}", frame.getDeviceAddressHex(), ftpUrl);
        return ParseResult.success(p, data.length);
    }

    // ==================== 未知指令处理 ====================

    private ParseResult handleUnknownCommand(NovaFrameParser.NovaFrame frame, int totalSize, ParseContext context) {
        byte[] cmdData = frame.getDataField();
        if (cmdData != null && cmdData.length > 50) {
            // 尝试从数据域提取图片
            ImageExtractor.ImageResult img = ImageExtractor.extract(cmdData);
            if (img != null) {
                return ParseResult.success(
                        buildImageReport(img, "Nova-Cmd" + frame.getCommandHex(), context), totalSize);
            }
            // 大数据包上报为二进制
            if (cmdData.length > 100) {
                ReportPayload p = new ReportPayload();
                p.setProtocol("Nova-Cmd" + frame.getCommandHex());
                p.setContentType("binary");
                p.setMainCmd(frame.getCommandHex());
                p.setSourceAddr(frame.getDeviceAddressHex());
                p.setData(Base64.getEncoder().encodeToString(cmdData));
                p.setDescription(String.format("Nova未知指令 [cmd=%s, %dB]", frame.getCommandHex(), cmdData.length));
                return ParseResult.success(p, totalSize);
            }
        }
        log.debug("【Nova过滤】跳过指令: cmd={}, dataLen={}B",
                frame.getCommandHex(), cmdData != null ? cmdData.length : 0);
        return null;
    }

    // ==================== 构建上报数据 ====================

    /**
     * 构建文件传输上报（与 SigmaProtocolParser.buildFileTransferReport 逻辑对齐）
     */
    private ReportPayload buildFileTransferReport(NovaFileAssembler.FileTransferResult transfer, ParseContext context) {
        ReportPayload p = new ReportPayload();
        p.setProtocol("Nova-FileTransfer");
        p.setFileName(transfer.getFileName());
        p.setFileExtension(transfer.getFileExtension());
        p.setSourceAddr(String.format("0x%04X", transfer.getDeviceAddress()));
        p.setTotalPackets(transfer.getTotalBlocks());
        p.setTotalSize(transfer.getTotalSize());

        byte[] fileData = transfer.getFileData();

        if (fileData != null && fileData.length > 0) {
            if (transfer.isImageFile()) {
                buildImageTransfer(p, fileData, transfer, context);
            } else if (transfer.isVideoFile()) {
                buildVideoTransfer(p, fileData, transfer, context);
            } else if (transfer.isTextFile()) {
                buildTextTransfer(p, fileData, transfer);
            } else {
                buildGenericTransfer(p, fileData, transfer, context);
            }
        } else {
            p.setContentType(transfer.isImageFile() ? "image_reference" : "file_reference");
            p.setData(transfer.getFileName());
            p.setDescription(String.format("Nova文件传输引用: %s", transfer.getFileName()));
        }

        log.info("【Nova文件传输上报】file={}, ext={}, contentType={}, size={}KB, blocks={}",
                transfer.getFileName(), transfer.getFileExtension(), p.getContentType(),
                transfer.getTotalSize() / 1024, transfer.getTotalBlocks());
        return p;
    }

    private void buildImageTransfer(ReportPayload p, byte[] fileData,
                                    NovaFileAssembler.FileTransferResult transfer, ParseContext context) {
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
        p.setDescription(String.format("Nova图片文件传输: %s [%d块, %dKB]",
                transfer.getFileName(), transfer.getTotalBlocks(), fileData.length / 1024));
    }

    private void buildVideoTransfer(ReportPayload p, byte[] fileData,
                                    NovaFileAssembler.FileTransferResult transfer, ParseContext context) {
        String minioPath = context.getMinioUploadService().uploadVideo(fileData, transfer.getFileName());
        p.setContentType("video");
        p.setMinioPath(minioPath);
        p.setDescription(String.format("Nova视频文件传输: %s [%d块, %dMB]",
                transfer.getFileName(), transfer.getTotalBlocks(), fileData.length / (1024 * 1024)));
        log.info("【Nova视频文件】重组完成: file={}, size={}MB, minioPath={}",
                transfer.getFileName(), fileData.length / (1024 * 1024), minioPath);
    }

    private void buildTextTransfer(ReportPayload p, byte[] fileData,
                                   NovaFileAssembler.FileTransferResult transfer) {
        p.setContentType("text");
        String textContent = decodeText(fileData);
        p.setData(textContent);
        p.setDescription(String.format("Nova文本文件传输: %s [%d块, %dKB]",
                transfer.getFileName(), transfer.getTotalBlocks(), fileData.length / 1024));
        log.info("【Nova文本文件】重组成功: file={}, size={}字节, 内容预览={}",
                transfer.getFileName(), fileData.length,
                textContent.length() > 100 ? textContent.substring(0, 100) + "..." : textContent);
    }

    private void buildGenericTransfer(ReportPayload p, byte[] fileData,
                                      NovaFileAssembler.FileTransferResult transfer, ParseContext context) {
        // 未知扩展名：尝试图片魔数提取
        ImageExtractor.ImageResult img = ImageExtractor.extract(fileData);
        if (img != null) {
            String minioPath = context.getMinioUploadService().uploadImage(
                    img.getImageData(), transfer.getFileName(), img.getFormat());
            p.setContentType("image");
            p.captureImageForAnalysis(img.getImageData());
            if (minioPath != null) {
                p.setMinioPath(minioPath);
            } else {
                p.setScreenshotBase64(Base64.getEncoder().encodeToString(img.getImageData()));
            }
            p.setImageFormat(img.getFormat());
            p.setDescription(String.format("Nova文件传输(含%s图片): %s [%d块]",
                    img.getFormat(), transfer.getFileName(), transfer.getTotalBlocks()));
        } else {
            p.setContentType("binary");
            p.setData(Base64.getEncoder().encodeToString(fileData));
            p.setDescription(String.format("Nova文件传输: %s [%d块, %dKB]",
                    transfer.getFileName(), transfer.getTotalBlocks(), fileData.length / 1024));
        }
    }

    private ReportPayload buildImageReport(ImageExtractor.ImageResult image, String protocol, ParseContext context) {
        byte[] imageData = image.getImageData();
        String minioPath = context.getMinioUploadService().uploadImage(imageData, null, image.getFormat());
        String description = String.format("%s-%s图片 [%d字节, 偏移=%d]",
                protocol, image.getFormat(), imageData.length, image.getOffset());

        log.info("【Nova图片提取】成功: 格式={}, 大小={}字节, 偏移={}, 协议={}",
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

    private ReportPayload buildRawDataReport(byte[] data) {
        int debugLen = Math.min(100, data.length);
        StringBuilder hexDebug = new StringBuilder();
        for (int i = 0; i < debugLen; i++) {
            hexDebug.append(String.format("%02X ", data[i]));
            if ((i + 1) % 16 == 0) hexDebug.append("\n                ");
        }
        log.warn("【Nova解析失败】无法识别的数据包（前{}字节十六进制）:\n                {}",
                debugLen, hexDebug);

        String base64Data = Base64.getEncoder().encodeToString(data);
        return ReportPayload.ofBinary("Nova-Unknown", base64Data,
                String.format("Nova无法识别的数据 [%d字节]", data.length));
    }

    // ==================== 日志辅助 ====================

    private void logScreenControl(NovaFrameParser.NovaFrame frame) {
        byte[] data = frame.getDataField();
        if (data != null && data.length >= 1) {
            String action = data[0] == 1 ? "开屏(正常显示)" : data[0] == 2 ? "关屏(黑屏)" : "未知(" + data[0] + ")";
            log.info("【Nova控制】开关屏: device=0x{}, 动作={}", frame.getDeviceAddressHex(), action);
        }
    }

    private void logScreenControlReply(NovaFrameParser.NovaFrame frame) {
        byte[] data = frame.getDataField();
        if (data != null && data.length >= 1) {
            log.info("【Nova控制】开关屏回复: device=0x{}, 结果={}",
                    frame.getDeviceAddressHex(), data[0] == 1 ? "成功" : "失败");
        }
    }

    private void logDeviceStatus(NovaFrameParser.NovaFrame frame) {
        byte[] data = frame.getDataField();
        if (data != null && data.length >= 9) {
            int doorState = data[7] & 0xFF;
            int screenPower = data[8] & 0xFF;
            log.info("【Nova状态】device=0x{}, 门状态={}, 屏体电源={}",
                    frame.getDeviceAddressHex(),
                    doorState == 1 ? "打开" : "关闭",
                    screenPower == 1 ? "供电" : "断电");
        }
    }

    // ==================== 工具方法 ====================

    private String detectImageFormat(byte[] data) {
        if (data == null || data.length < 4) return null;
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) return "JPEG";
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return "PNG";
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) return "GIF";
        if (data[0] == 0x42 && data[1] == 0x4D) return "BMP";
        return null;
    }

    /**
     * 尝试将二进制数据解码为可读文本
     * 要求可读字符占比 > 50% 才视为文本
     */
    private String detectVideoExtension(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if (data[4] == 0x66 && data[5] == 0x74 && data[6] == 0x79 && data[7] == 0x70) {
            return "mp4";
        }
        if (data[0] == 0x00 && data[1] == 0x00 && data[2] == 0x00 && data[3] >= 0x18
                && data[4] == 0x66 && data[5] == 0x74 && data[6] == 0x79 && data[7] == 0x70) {
            return "mp4";
        }
        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data[8] == 0x41 && data[9] == 0x56 && data[10] == 0x49 && data[11] == 0x20) {
            return "avi";
        }
        if ((data[0] & 0xFF) == 0x1A && data[1] == 0x45 && (data[2] & 0xFF) == 0xDF
                && (data[3] & 0xFF) == 0xA3) {
            return "mkv";
        }
        if (data[0] == 0x46 && data[1] == 0x4C && data[2] == 0x56) {
            return "flv";
        }
        if ((data[0] & 0xFF) == 0x47 && data.length > 188 && (data[188] & 0xFF) == 0x47) {
            return "ts";
        }
        return null;
    }

    private String tryExtractText(byte[] data) {
        if (data == null || data.length < 2) return null;
        try {
            String text = new String(data, StandardCharsets.UTF_8);
            int readableCount = 0;
            for (char c : text.toCharArray()) {
                if ((c >= 0x20 && c <= 0x7E) || (c >= 0x4E00 && c <= 0x9FFF)
                        || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF)) {
                    readableCount++;
                }
            }
            if (readableCount > text.length() * 0.5) {
                return text.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 文本解码：UTF-8 优先，检测到乱码回退 GBK
     */
    private String decodeText(byte[] data) {
        try {
            String text = new String(data, StandardCharsets.UTF_8);
            if (!text.contains("\uFFFD")) {
                return text;
            }
            text = new String(data, "GBK");
            log.info("【Nova文本编码】使用 GBK 解码");
            return text;
        } catch (Exception e) {
            return Base64.getEncoder().encodeToString(data);
        }
    }
}
