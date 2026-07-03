package com.monitorplatform.content.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Resource;
import javax.imageio.ImageIO;

/**
 * 阿里云通义千问API调用服务
 * 使用 DashScope SDK 调用公网模型
 * - 图片/视频检测: qwen-vl-max 多模态视觉语言模型
 * - 文本检测: qwen-max 纯文本模型
 */
@Slf4j
@Service
public class QwenApiService {

    @Value("${aliyun.dashscope.api-key:}")
    private String apiKey;

    @Value("${aliyun.dashscope.model:qwen-vl-max}")
    private String model;

    /** 纯文本检测模型（可配置，默认 qwen-max） */
    @Value("${aliyun.dashscope.text-model:qwen-max}")
    private String textModel;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${minio.bucket-name:monitor-content}")
    private String minioBucketName;

    /**
     * 精简版系统提示词（控制在80 Token以内）
     * 注意：模型返回的是带编号的纯文本，parseResponse 用正则提取各字段
     **/
    private static final String SYSTEM_PROMPT =
        "你是内容合规检测专家，需判定图片内容是否违反中国法律法规与公序良俗。" +
        "输出结果包含：1.判定结果（合规/违规）；2.违规类型（无/色情/暴力/敏感信息/其他）；" +
        "3.置信度（0-100）；4.判定依据（不超过50字）。";

    /**
     * 文本内容合规检测的系统提示词
     */
    private static final String TEXT_SYSTEM_PROMPT =
        "你是内容合规检测专家，需判定LED屏幕文本内容是否违反中国法律法规与公序良俗。" +
        "输出结果包含：1.判定结果（合规/违规）；2.违规类型（无/色情/暴力/敏感信息/其他）；" +
        "3.置信度（0-100）；4.判定依据（不超过50字）。";

    /**
     * 用户检测指令
     */
    private static final String USER_INSTRUCTION =
        "请分析该图片中的电子屏播放内容，严格按照要求输出检测结果。";

    /**
     * 调用通义千问模型检测内容合规性
     *
     * @param request 检测请求
     * @return 检测结果
     */
    public QwenDetectionResultDTO detectContent(QwenDetectionRequestDTO request) {
        try {
            log.info("开始调用通义千问API，businessId: {}, deviceId: {}",
                request.getBusinessId(), request.getDeviceId());

            // 视频类型：先从 MinIO 抽帧，转换为 Base64 后复用图片检测流程
            if ("video".equals(request.getContentType()) && request.getMinioPath() != null) {
                log.info("【视频检测】开始从 MinIO 抽取视频帧，minioPath: {}, businessId: {}",
                    request.getMinioPath(), request.getBusinessId());
                String frameBase64 = extractFrameFromVideoMinioPath(request.getMinioPath(), request.getBusinessId());
                if (frameBase64 == null) {
                    log.warn("【视频检测】视频帧提取失败，返回 pending，businessId: {}", request.getBusinessId());
                    QwenDetectionResultDTO pending = new QwenDetectionResultDTO();
                    pending.setDetectionResult("pending");
                    pending.setViolationType("other");
                    pending.setConfidence(0);
                    pending.setReason("视频帧提取失败，待人工审核");
                    return pending;
                }
                // 抽帧成功，将请求转换为图片模式继续走图片检测流程
                request.setScreenshotBase64(frameBase64);
                request.setMinioPath(null);   // 清掉 minioPath，走 Base64 分支
                request.setContentType("image");
                log.info("【视频检测】抽帧成功，转为图片检测流程，businessId: {}", request.getBusinessId());
            }

            // 【关键1】强制清空上下文：messages仅包含system + 当前user请求，无任何历史记录
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)           // 传入API Key认证
                .model(model)             // 从配置注入，支持运行时切换模型
                .messages(Arrays.asList(
                    // System角色：预设提示词
                    createSystemMessage(),
                    // User角色：图片 + 检测指令 + 业务标识
                    createUserMessage(request)
                ))
                // 【关键2】固定温度降低随机性（SDK 2.12.0 不支持 ResultFormat 枚举，默认输出格式已满足需求）
                .topP(0.01)
                .build();

            // 调用API（实例调用，非静态调用）
            MultiModalConversationResult result = new MultiModalConversation().call(param);

            // 解析响应
            return parseResponse(result, request.getBusinessId());

        } catch (Exception e) {
            log.error("调用通义千问API失败，businessId: {}, error: {}",
                request.getBusinessId(), e.getMessage(), e);

            // 返回错误结果（兜底，不阻断检测流程）
            QwenDetectionResultDTO errorResult = new QwenDetectionResultDTO();
            errorResult.setDetectionResult("pending");
            errorResult.setViolationType("other");
            errorResult.setConfidence(0);
            errorResult.setReason("API调用失败：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 调用通义千问纯文本模型检测LED屏文本内容合规性
     *
     * @param textContent 待检测的文本内容
     * @param businessId  业务唯一标识
     * @param deviceId    设备ID
     * @return 检测结果
     */
    public QwenDetectionResultDTO detectTextContent(String textContent, String businessId, String deviceId) {
        try {
            log.info("开始调用通义千问文本API，businessId: {}, deviceId: {}, text: {}",
                businessId, deviceId,
                textContent.length() > 100 ? textContent.substring(0, 100) + "..." : textContent);

            String userPrompt = String.format(
                "设备ID：%s，请求ID：%s。\n待检测的LED屏幕显示文本如下：\n%s\n\n请严格按照要求输出检测结果。",
                deviceId, businessId, textContent
            );

            GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(textModel)
                .messages(Arrays.asList(
                    Message.builder().role(Role.SYSTEM.getValue()).content(TEXT_SYSTEM_PROMPT).build(),
                    Message.builder().role(Role.USER.getValue()).content(userPrompt).build()
                ))
                .topP(0.01)
                .build();

            GenerationResult result = new Generation().call(param);

            // 解析响应
            String content = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("文本检测API响应，businessId: {}, content: {}", businessId, content);
            return parseTextResponse(content, businessId);

        } catch (Exception e) {
            log.error("调用通义千问文本API失败，businessId: {}, error: {}",
                businessId, e.getMessage(), e);
            QwenDetectionResultDTO errorResult = new QwenDetectionResultDTO();
            errorResult.setDetectionResult("pending");
            errorResult.setViolationType("other");
            errorResult.setConfidence(0);
            errorResult.setReason("文本API调用失败：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 解析纯文本模型响应（与 parseResponse 逻辑相同，但入参为 String）
     */
    private QwenDetectionResultDTO parseTextResponse(String content, String businessId) {
        QwenDetectionResultDTO dto = new QwenDetectionResultDTO();
        try {
            String detectionResult = extractField(content, "判定结果");
            String violationType   = extractField(content, "违规类型");
            String confidenceStr   = extractField(content, "置信度");
            String reason          = extractField(content, "判定依据");

            dto.setDetectionResult(mapDetectionResult(detectionResult));
            dto.setViolationType(mapViolationType(violationType));
            dto.setConfidence(parseConfidence(confidenceStr));
            dto.setReason(reason != null ? reason : content);

            log.info("文本检测解析成功，businessId: {}, result: {}, violationType: {}, confidence: {}",
                businessId, dto.getDetectionResult(), dto.getViolationType(), dto.getConfidence());
        } catch (Exception e) {
            log.error("文本检测响应解析失败，businessId: {}, error: {}", businessId, e.getMessage(), e);
            dto.setDetectionResult("pending");
            dto.setViolationType("other");
            dto.setConfidence(0);
            dto.setReason("结果解析失败：" + e.getMessage());
        }
        return dto;
    }

    /**
     * 从 MinIO 视频路径抽取帧，返回 JPEG Base64（不含 data: 前缀）
     * 使用 FFmpegFrameGrabber 流式读取，定位到视频 1/4 处避免片头黑屏
     *
     * @param minioPath MinIO 对象路径（如 videos/2026/04/07/uuid.mp4）
     * @param businessId 业务标识（用于日志）
     * @return JPEG Base64 字符串，失败返回 null
     */
    private String extractFrameFromVideoMinioPath(String minioPath, String businessId) {
        String videoUrl = minioEndpoint + "/" + minioBucketName + "/" + minioPath;
        log.info("【视频抽帧】开始，url={}, businessId={}", videoUrl, businessId);

        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(videoUrl);
            grabber.start();

            // 定位到 1/4 处，避免片头黑屏
            long durationMicros = grabber.getLengthInTime();
            if (durationMicros > 0) {
                long seekMicros = durationMicros / 4;
                grabber.setTimestamp(seekMicros, true);
                log.info("【视频抽帧】视频时长={}ms，定位到={}ms，businessId={}",
                    durationMicros / 1000, seekMicros / 1000, businessId);
            }

            // 获取一帧图像
            Frame frame = null;
            for (int i = 0; i < 30; i++) {
                Frame f = grabber.grabImage();
                if (f != null && f.image != null) {
                    frame = f;
                    break;
                }
            }

            if (frame == null) {
                log.warn("【视频抽帧】未获取到有效帧，businessId={}", businessId);
                grabber.stop();
                grabber.release();
                grabber = null;
                return null;
            }

            // Frame -> BufferedImage -> JPEG Base64
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage bufferedImage = converter.getBufferedImage(frame);
            converter.close();
            if (bufferedImage == null) {
                log.warn("【视频抽帧】Frame 转换 BufferedImage 失败，businessId={}", businessId);
                return null;
            }

            // 如果尺寸过小则放大（复用图片预处理逻辑）
            int w = bufferedImage.getWidth();
            int h = bufferedImage.getHeight();
            if (w < MIN_IMAGE_SIZE || h < MIN_IMAGE_SIZE) {
                double scale = (double) MIN_IMAGE_SIZE / Math.min(w, h);
                int newW = (int) Math.ceil(w * scale);
                int newH = (int) Math.ceil(h * scale);
                java.awt.Image scaled = bufferedImage.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
                BufferedImage upscaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                upscaled.getGraphics().drawImage(scaled, 0, 0, null);
                upscaled.getGraphics().dispose();
                bufferedImage = upscaled;
                log.info("【视频抽帧】帧尺寸过小，已放大 {}x{} -> {}x{}，businessId={}", w, h, newW, newH, businessId);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "jpeg", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            log.info("【视频抽帧】成功，帧大小={}bytes，businessId={}", baos.size(), businessId);
            return base64;

        } catch (Exception e) {
            log.error("【视频抽帧】异常: url={}, businessId={}, error={}", videoUrl, businessId, e.getMessage(), e);
            return null;
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 创建System消息（精简提示词）
     */
    private MultiModalMessage createSystemMessage() {
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("text", SYSTEM_PROMPT);

        return MultiModalMessage.builder()
            .role(Role.SYSTEM.getValue())
            .content(Arrays.asList(textContent))
            .build();
    }

    /**
     * 创建User消息（图片 + 检测指令 + 业务标识）
     * 自动检测图片格式，BMP 格式自动转换为 JPEG（qwen-vl-max 不支持 BMP）
     *
     * MIME类型优先级：
     * 1. 优先从 Data URL 前缀提取（data:image/webp;base64,...）
     * 2. 无前缀时降级使用魔术字节检测
     * 3. MinIO 路径：下载图片转为 Base64（阿里云 API 无法访问内网 MinIO）
     */
    private MultiModalMessage createUserMessage(QwenDetectionRequestDTO request) {
        String imageDataUrl;
        
        // 优先使用 MinIO 路径（下载后转 Base64）
        if (request.getMinioPath() != null && !request.getMinioPath().isEmpty()) {
            // 从 MinIO 下载图片并转为 Base64
            String minioUrl = minioEndpoint + "/" + minioBucketName + "/" + request.getMinioPath();
            log.info("【MinIO模式】从 MinIO 下载图片: {}, businessId: {}", minioUrl, request.getBusinessId());
            imageDataUrl = downloadImageFromMinio(minioUrl);
            if (imageDataUrl == null) {
                throw new RuntimeException("从 MinIO 下载图片失败: " + minioUrl);
            }
        } else {
            // Base64 模式（原逻辑）
            String raw = request.getScreenshotBase64();
            String pureBase64;
            String mimeType;

            // 第一步：若有 Data URL 前缀，直接从中提取 MIME 类型（最可靠）
            // 格式：data:image/webp;base64,Uk1GR...
            if (raw != null && raw.startsWith("data:")) {
                int semicolonIndex = raw.indexOf(';');
                int commaIndex = raw.indexOf(',');
                if (semicolonIndex > 5 && commaIndex > semicolonIndex) {
                    mimeType = raw.substring(5, semicolonIndex);   // 取 "image/webp"
                    pureBase64 = raw.substring(commaIndex + 1);   // 取纯 Base64 部分
                } else {
                    // 前缀格式异常，降级处理
                    pureBase64 = stripDataUrlPrefix(raw);
                    mimeType = detectImageMimeType(pureBase64);
                }
            } else {
                // 无 Data URL 前缀，使用魔术字节检测
                pureBase64 = raw;
                mimeType = detectImageMimeType(pureBase64);
            }

            // 第二步：BMP 格式不被 DashScope 支持，自动转换为 JPEG
            if ("image/bmp".equals(mimeType)) {
                log.info("检测到 BMP 格式图片，自动转换为 JPEG，businessId: {}", request.getBusinessId());
                pureBase64 = convertBmpToJpegBase64(pureBase64);
                mimeType = "image/jpeg";
            }

            // 第三步：图片尺寸过小时放大，确保大模型能有效识别内容
            pureBase64 = upscaleImageIfTooSmall(pureBase64, mimeType, request.getBusinessId());

            // 第四步：重新拼接 Data URL 发送给 API
            imageDataUrl = "data:" + mimeType + ";base64," + pureBase64;
            log.info("【Base64模式】图片MIME类型: {}, businessId: {}", mimeType, request.getBusinessId());
        }

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("image", imageDataUrl);

        // 文本内容（检测指令 + 业务标识，用于并发结果匹配）
        Map<String, Object> textContent = new HashMap<>();
        String instruction = String.format(
            "设备ID：%s，设备名称：%s，采集时间：%s，请求ID：%s。%s",
            request.getDeviceId(),
            request.getDeviceName(),
            request.getCaptureTime(),
            request.getBusinessId(),
            USER_INSTRUCTION
        );
        textContent.put("text", instruction);

        return MultiModalMessage.builder()
            .role(Role.USER.getValue())
            .content(Arrays.asList(imageContent, textContent))
            .build();
    }

    /**
     * 根据纯Base64头部魔术字节自动检测图片MIME类型（入参须为纯Base64，不含data:前缀）
     * JPEG: /9j/  PNG: iVBORw0KGgo  WebP: UklGR  GIF: R0lGOD  BMP: Qk0
     */
    private String detectImageMimeType(String base64) {
        if (base64 == null || base64.length() < 8) {
            return "image/jpeg";
        }
        // 通过文件头魔术字节判断（调用前已由 stripDataUrlPrefix 去除 data: 前缀）
        String header = base64.substring(0, Math.min(12, base64.length()));
        if (header.startsWith("/9j/")) {
            return "image/jpeg";
        } else if (header.startsWith("iVBORw0KGgo")) {
            return "image/png";
        } else if (header.startsWith("UklGR")) {
            return "image/webp";
        } else if (header.startsWith("R0lGOD")) {
            return "image/gif";
        } else if (header.startsWith("Qk0")) {
            return "image/bmp";
        }
        return "image/jpeg";  // 无法识别时降级为 JPEG
    }

    /**
     * 将 BMP 格式 Base64 转换为 JPEG 格式 Base64
     * 使用 Java 标准库 ImageIO，无需额外依赖
     */
    private String convertBmpToJpegBase64(String bmpBase64) {
        try {
            byte[] bmpBytes = Base64.getDecoder().decode(bmpBase64);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bmpBytes));
            if (image == null) {
                log.error("BMP图片解码失败，返回原始数据");
                return bmpBase64;
            }
            ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", jpegOutput);
            String jpegBase64 = Base64.getEncoder().encodeToString(jpegOutput.toByteArray());
            log.info("BMP转JPEG成功，原始大小: {}字节, 转换后大小: {}字节",
                bmpBytes.length, jpegOutput.size());
            return jpegBase64;
        } catch (Exception e) {
            log.error("BMP转JPEG异常，返回原始数据: {}", e.getMessage(), e);
            return bmpBase64;
        }
    }

    /**
     * 图片尺寸过小时放大到最低可识别尺寸（MIN_IMAGE_SIZE × MIN_IMAGE_SIZE）
     * 原因：大模型对极低分辨率图片（如 32×32）的视觉理解能力极差，
     *       放大后像素点变多，模型可以提取到更多上下文特征。
     * 放大策略：双线性插值（SCALE_SMOOTH），保持宽高比，短边放大到 MIN_IMAGE_SIZE
     * 仅在宽或高小于 MIN_IMAGE_SIZE 时触发，正常尺寸图片原样返回。
     */
    private static final int MIN_IMAGE_SIZE = 200;

    private String upscaleImageIfTooSmall(String pureBase64, String mimeType, String businessId) {
        try {
            byte[] imgBytes = Base64.getDecoder().decode(pureBase64);
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imgBytes));
            if (original == null) {
                log.warn("【放大预处理】图片解码失败，跳过放大，businessId: {}", businessId);
                return pureBase64;
            }

            int w = original.getWidth();
            int h = original.getHeight();

            // 宽和高都不小于最低尺寸，无需处理
            if (w >= MIN_IMAGE_SIZE && h >= MIN_IMAGE_SIZE) {
                return pureBase64;
            }

            // 计算放大比例（保持宽高比，短边放大到 MIN_IMAGE_SIZE）
            double scale = (double) MIN_IMAGE_SIZE / Math.min(w, h);
            int newW = (int) Math.ceil(w * scale);
            int newH = (int) Math.ceil(h * scale);

            log.info("【放大预处理】原始尺寸 {}×{}，放大至 {}×{}，businessId: {}", w, h, newW, newH, businessId);

            // 双线性插值放大
            java.awt.Image scaled = original.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
            BufferedImage upscaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            upscaled.getGraphics().drawImage(scaled, 0, 0, null);
            upscaled.getGraphics().dispose();

            // 输出格式：JPEG（与 convertBmpToJpegBase64 保持一致）
            String outputFormat = "image/png".equals(mimeType) ? "png" : "jpeg";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(upscaled, outputFormat, out);
            String result = Base64.getEncoder().encodeToString(out.toByteArray());

            log.info("【放大预处理】完成，原始大小: {}字节，放大后: {}字节，businessId: {}",
                imgBytes.length, out.size(), businessId);
            return result;

        } catch (Exception e) {
            log.error("【放大预处理】异常，跳过放大，businessId: {}, error: {}", businessId, e.getMessage(), e);
            return pureBase64;
        }
    }

    /**
     * 剥离 Data URL 前缀，返回纯 Base64 数据
     * 输入：data:image/bmp;base64,Qk04... → 输出：Qk04...
     * 输入：Qk04...（已是纯Base64）→ 输出：Qk04...（不变）
     */
    private String stripDataUrlPrefix(String base64) {
        if (base64 != null && base64.startsWith("data:")) {
            int commaIndex = base64.indexOf(',');
            if (commaIndex >= 0 && commaIndex < base64.length() - 1) {
                return base64.substring(commaIndex + 1);
            }
        }
        return base64;
    }

    /**
     * 解析API响应
     */
    private QwenDetectionResultDTO parseResponse(MultiModalConversationResult result, String businessId) {
        QwenDetectionResultDTO dto = new QwenDetectionResultDTO();

        try {
            // 检查响应状态
            if (result == null || result.getOutput() == null) {
                log.warn("API响应为空，businessId: {}", businessId);
                dto.setDetectionResult("待审核");
                dto.setViolationType("其他");
                dto.setConfidence(0);
                dto.setReason("API响应为空");
                return dto;
            }

            String content = result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
            log.info("API响应内容，businessId: {}, content: {}", businessId, content);

         
            String detectionResult = extractField(content, "判定结果");
            String violationType   = extractField(content, "违规类型");
            String confidenceStr   = extractField(content, "置信度");
            String reason          = extractField(content, "判定依据");

            dto.setDetectionResult(mapDetectionResult(detectionResult));
            dto.setViolationType(mapViolationType(violationType));
            dto.setConfidence(parseConfidence(confidenceStr));
            dto.setReason(reason != null ? reason : content);  // 兜底：直接存原始文本

            // 获取请求ID
            dto.setRequestId(result.getRequestId());

            // Token消耗统计
            QwenDetectionResultDTO.TokenUsage tokenUsage = new QwenDetectionResultDTO.TokenUsage();
            tokenUsage.setInputTokens(result.getUsage().getInputTokens());
            tokenUsage.setOutputTokens(result.getUsage().getOutputTokens());
            tokenUsage.setTotalTokens(tokenUsage.getInputTokens() + tokenUsage.getOutputTokens());
            dto.setTokenUsage(tokenUsage);

            log.info("解析成功，businessId: {}, result: {}, violationType: {}, confidence: {}, tokens: {}",
                businessId, dto.getDetectionResult(), dto.getViolationType(),
                dto.getConfidence(), tokenUsage.getTotalTokens());

        } catch (Exception e) {
            log.error("解析API响应失败，businessId: {}, error: {}", businessId, e.getMessage(), e);
            dto.setDetectionResult("待审核");
            dto.setViolationType("其他");
            dto.setConfidence(0);
            dto.setReason("结果解析失败：" + e.getMessage());
        }

        return dto;
    }

    /**
     * 从带编号的纯文本中提取指定字段值
     * 匹配格式：1.判定结果：xxx 或 1.判定结果: xxx（冒号全半角兼容）
     */
    private String extractField(String content, String fieldName) {
        // 匹配 "字段名：内容" 直到行尾或下一个编号
        Pattern pattern = Pattern.compile(fieldName + "[：:][\\s]*([^\\n]+?)(?=\\n\\d|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 解析置信度字符串为整数，兼容 "95" / "95%" 等格式
     */
    private int parseConfidence(String confidenceStr) {
        if (confidenceStr == null) return 0;
        try {
            return Integer.parseInt(confidenceStr.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 映射判定结果（中文转英文）
     */
    private String mapDetectionResult(String chineseResult) {
        if (chineseResult == null) return "pending";

        switch (chineseResult) {
            case "合规":
                return "compliant";
            case "违规":
                return "violation";
            default:
                return "pending";
        }
    }

    /**
     * 映射违规类型（中文转英文）
     */
    private String mapViolationType(String chineseType) {
        if (chineseType == null) return "none";

        switch (chineseType) {
            case "无":
                return "none";
            case "色情":
                return "pornography";
            case "暴力":
                return "violence";
            case "敏感信息":
                return "sensitive";
            default:
                return "other";
        }
    }

    /**
     * 从 MinIO 下载图片并转为 Base64 Data URL
     * 阿里云 DashScope API 无法访问内网 MinIO，需要本地下载后上传
     *
     * @param minioUrl MinIO 图片 URL
     * @return Base64 Data URL (data:image/jpeg;base64,...)
     */
    private String downloadImageFromMinio(String minioUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(minioUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);  // 10秒连接超时
            conn.setReadTimeout(30000);     // 30秒读取超时
            conn.setDoInput(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.error("【MinIO下载】HTTP错误: {}, URL: {}", responseCode, minioUrl);
                return null;
            }

            // 读取图片数据
            byte[] imageBytes;
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                imageBytes = baos.toByteArray();
            }

            // 检测图片格式
            String mimeType = detectImageMimeTypeFromBytes(imageBytes);
            if (mimeType == null) {
                mimeType = "image/jpeg";  // 默认
            }

            // BMP 转 JPEG
            if ("image/bmp".equals(mimeType)) {
                log.info("【MinIO下载】BMP格式，转换为JPEG");
                imageBytes = convertBmpBytesToJpeg(imageBytes);
                mimeType = "image/jpeg";
            }

            // 转为 Base64
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            
            log.info("【MinIO下载】成功: {} -> {} bytes, mimeType={}", 
                    minioUrl, imageBytes.length, mimeType);
            return dataUrl;

        } catch (Exception e) {
            log.error("【MinIO下载】失败: {}, error: {}", minioUrl, e.getMessage(), e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 从字节数组检测图片 MIME 类型
     */
    private String detectImageMimeTypeFromBytes(byte[] data) {
        if (data == null || data.length < 4) return null;
        // JPEG: FF D8 FF
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
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
        return null;
    }

    /**
     * BMP 字节数组转为 JPEG 字节数组
     */
    private byte[] convertBmpBytesToJpeg(byte[] bmpBytes) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bmpBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                throw new RuntimeException("无法读取 BMP 图片");
            }
            ImageIO.write(image, "jpeg", baos);
            return baos.toByteArray();
        }
    }
}
