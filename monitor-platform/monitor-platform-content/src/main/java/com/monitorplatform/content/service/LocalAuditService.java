package com.monitorplatform.content.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.monitorplatform.content.entity.dto.LocalAuditResultDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

/**
 * 本地视觉审核模型调用服务
 * 
 * 替代原通义千问公网API,实现全流程内网化
 * 模型地址: http://127.0.0.1:21580/api/audit/image/quick
 * 
 * 优势:
 * 1. 数据不出内网,安全性更高
 * 2. 无需API Key,无调用费用
 * 3. 结构化JSON输出,无需正则解析
 * 4. 自带OCR+目标检测+场景分析
 * 
 * 注意: 模型存在CPU并发冲突问题(RuntimeError: could not execute a primitive)
 * 已通过重试机制(最多3次,间隔2秒)进行容错处理
 */
@Slf4j
@Service
public class LocalAuditService {

    /** 本地模型审核接口地址 */
    @Value("${local-audit.image-url:http://127.0.0.1:21580/api/audit/image/quick}")
    private String imageUrl;

    /** 本地模型文本审核接口地址(如有) */
    @Value("${local-audit.text-url:http://127.0.0.1:21580/api/audit/text}")
    private String textUrl;

    /** 本地模型视频审核接口地址 */
    @Value("${local-audit.video-url:http://127.0.0.1:21580/api/audit/video/quick}")
    private String videoUrl;

    /** 视频抽帧间隔(秒) */
    @Value("${local-audit.video-frame-interval:0.5}")
    private double videoFrameInterval;

    /** 视频最大处理帧数 */
    @Value("${local-audit.video-max-frames:200}")
    private int videoMaxFrames;

    /** 是否保存违规帧截图 */
    @Value("${local-audit.video-save-frames:false}")
    private boolean videoSaveFrames;

    /** 连接超时(毫秒) */
    @Value("${local-audit.connect-timeout:5000}")
    private int connectTimeout;

    /** 读取超时(毫秒) - 模型推理可能较慢 */
    @Value("${local-audit.read-timeout:60000}")
    private int readTimeout;

    /** 失败重试次数(应对模型CPU并发冲突导致的500错误) */
    @Value("${local-audit.max-retry:3}")
    private int maxRetry;

    /** 重试等待间隔(毫秒) */
    @Value("${local-audit.retry-interval:2000}")
    private int retryInterval;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${minio.bucket-name:monitor-content}")
    private String minioBucketName;

    @Value("${local-audit.auth-mode:none}")
    private String authMode;

    @Value("${local-audit.auth-token:}")
    private String authToken;

    @Value("${local-audit.auth-url:}")
    private String authUrl;

    @Value("${local-audit.auth-username:admin}")
    private String authUsername;

    @Value("${local-audit.auth-password:}")
    private String authPassword;

    @Value("${local-audit.auth-cert-content:}")
    private String authCertContent;

    @Value("${local-audit.auth-cert-path:}")
    private String authCertPath;

    private volatile String sessionCookie;

    /**
     * 调用本地模型审核图片
     * 
     * @param request 检测请求(包含MinIO路径或Base64)
     * @return 统一检测结果DTO
     */
    public QwenDetectionResultDTO auditImage(QwenDetectionRequestDTO request) {
        try {
            log.info("【本地模型】开始审核图片, businessId: {}, deviceId: {}", 
                request.getBusinessId(), request.getDeviceId());

            // 1. 获取图片文件路径(优先MinIO,其次Base64转临时文件)
            File imageFile = prepareImageFile(request);
            if (imageFile == null) {
                log.error("【本地模型】图片准备失败, businessId: {}", request.getBusinessId());
                return buildErrorResult("图片准备失败");
            }

            // 2. HTTP POST FormData上传
            LocalAuditResultDTO auditResult = uploadAndAudit(imageFile);
            
            // 3. 清理临时文件
            if (request.getScreenshotBase64() != null) {
                Files.deleteIfExists(imageFile.toPath());
            }

            if (auditResult == null || auditResult.getCode() != 0) {
                log.error("【本地模型】审核失败, businessId: {}, code: {}", 
                    request.getBusinessId(), auditResult != null ? auditResult.getCode() : "null");
                return buildErrorResult("模型审核失败");
            }

            // 4. 转换为统一结果格式
            return convertToLocalResult(auditResult, request.getBusinessId());

        } catch (Exception e) {
            log.error("【本地模型】图片审核异常, businessId: {}, error: {}", 
                request.getBusinessId(), e.getMessage(), e);
            return buildErrorResult("审核异常: " + e.getMessage());
        }
    }

    /**
     * 文本内容审核
     * 
     * 调用本地模型文本审核接口: POST /api/audit/text (FormData, field="file")
     * 将文本写入临时 .txt 文件后上传，模型返回结构化JSON结果
     *
     * @param textContent 待检测文本
     * @param businessId  业务ID
     * @param deviceId    设备ID
     * @return 统一检测结果DTO
     */
    public QwenDetectionResultDTO auditImageFile(MultipartFile file, String businessId, String deviceId) {
        if (file == null || file.isEmpty()) {
            return buildErrorResult("IMAGE_FILE_EMPTY");
        }

        File sourceFile = null;
        File auditFile = null;
        try {
            log.info("[LocalAudit] pre-audit image file, businessId={}, deviceId={}, file={}, size={}",
                    businessId, deviceId, file.getOriginalFilename(), file.getSize());

            sourceFile = copyMultipartToTempFile(file);
            auditFile = convertToJpgIfNeeded(sourceFile);

            LocalAuditResultDTO auditResult = uploadAndAudit(auditFile);
            if (auditResult == null || auditResult.getCode() == null || auditResult.getCode() != 0) {
                log.error("[LocalAudit] pre-audit image failed, businessId={}, code={}",
                        businessId, auditResult != null ? auditResult.getCode() : null);
                return buildErrorResult("MODEL_AUDIT_FAILED");
            }
            return convertToLocalResult(auditResult, businessId);
        } catch (Exception e) {
            log.error("[LocalAudit] pre-audit image exception, businessId={}, error={}",
                    businessId, e.getMessage(), e);
            return buildErrorResult("MODEL_AUDIT_EXCEPTION: " + e.getMessage());
        } finally {
            deleteQuietly(auditFile);
            if (auditFile == null || sourceFile == null || !auditFile.equals(sourceFile)) {
                deleteQuietly(sourceFile);
            }
        }
    }

    public QwenDetectionResultDTO auditVideoFile(MultipartFile file, String businessId, String deviceId) {
        if (file == null || file.isEmpty()) {
            return buildErrorResult("VIDEO_FILE_EMPTY");
        }

        File videoFile = null;
        try {
            log.info("[LocalAudit] pre-audit video file, businessId={}, deviceId={}, file={}, size={}",
                    businessId, deviceId, file.getOriginalFilename(), file.getSize());

            videoFile = copyMultipartToTempFile(file);
            LocalAuditResultDTO auditResult = uploadAndAuditVideo(videoFile);
            if (auditResult == null || auditResult.getCode() == null || auditResult.getCode() != 0) {
                log.error("[LocalAudit] pre-audit video failed, businessId={}, code={}",
                        businessId, auditResult != null ? auditResult.getCode() : null);
                return buildErrorResult("MODEL_AUDIT_FAILED");
            }
            return convertToLocalResult(auditResult, businessId);
        } catch (Exception e) {
            log.error("[LocalAudit] pre-audit video exception, businessId={}, error={}",
                    businessId, e.getMessage(), e);
            return buildErrorResult("MODEL_AUDIT_EXCEPTION: " + e.getMessage());
        } finally {
            deleteQuietly(videoFile);
        }
    }

    public QwenDetectionResultDTO auditText(String textContent, String businessId, String deviceId) {
        try {
            log.info("【本地模型】开始文本审核, businessId={}, text={}",
                businessId,
                textContent != null && textContent.length() > 80
                    ? textContent.substring(0, 80) + "..."
                    : textContent);

            if (textContent == null || textContent.trim().isEmpty()) {
                log.warn("【本地模型】文本内容为空, businessId={}", businessId);
                return buildErrorResult("文本内容为空");
            }

            // 1. 将文本写入临时文件
            File textFile = File.createTempFile("audit_text_", ".txt");
            Files.write(textFile.toPath(), textContent.getBytes("UTF-8"));

            // 2. 上传到本地模型文本审核接口
            LocalAuditResultDTO auditResult = uploadAndAuditText(textFile);

            // 3. 清理临时文件
            Files.deleteIfExists(textFile.toPath());

            if (auditResult == null || auditResult.getCode() != 0) {
                log.error("【本地模型】文本审核失败, businessId={}, code={}",
                    businessId, auditResult != null ? auditResult.getCode() : "null");
                return buildErrorResult("文本模型审核失败");
            }

            // 4. 转换为统一结果格式（模型结果即最终结果）
            return convertToLocalResult(auditResult, businessId);

        } catch (Exception e) {
            log.error("【本地模型】文本审核异常, businessId={}, error={}",
                businessId, e.getMessage(), e);
            return buildErrorResult("文本审核异常: " + e.getMessage());
        }
    }

    /**
     * 上传文本文件到本地模型进行审核
     * 
     * 内置重试机制: 应对模型CPU并发冲突导致的500错误
     * 策略: 最多重试3次,每次间隔2秒等待模型恢复
     */
    private LocalAuditResultDTO uploadAndAuditText(File textFile) {
        int attempt = 0;
        while (attempt < maxRetry) {
            attempt++;
            try {
                log.info("【本地模型】上传文本审核(第{}/{}次): url={}, file={}",
                    attempt, maxRetry, textUrl, textFile.getName());

                HttpResponse response = executeAuditRequest(HttpRequest.post(textUrl)
                    .form("file", textFile)
                    .timeout(connectTimeout)
                    .setReadTimeout(readTimeout));

                if (response.getStatus() == 200) {
                    String responseBody = response.body();
                    log.info("【本地模型】文本审核响应: {}", responseBody);
                    return JSON.parseObject(responseBody, LocalAuditResultDTO.class);
                }

                if (response.getStatus() == 401 && isCertSessionAuth()) {
                    log.warn("【本地模型】文本审核认证失效, 第{}/{}次, 准备刷新会话", attempt, maxRetry);
                    clearSessionCookie();
                    if (attempt < maxRetry) {
                        continue;
                    }
                }

                if (response.getStatus() == 500) {
                    log.warn("【本地模型】文本模型返回500(CPU并发冲突), 第{}/{}次, {}ms后重试, body={}",
                        attempt, maxRetry, retryInterval, response.body());
                    if (attempt < maxRetry) {
                        Thread.sleep(retryInterval);
                        continue;
                    }
                }

                log.error("【本地模型】文本HTTP请求失败: status={}, body={}",
                    response.getStatus(), response.body());
                return null;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("【本地模型】文本重试等待被中断");
                return null;
            } catch (Exception e) {
                log.error("【本地模型】文本上传审核异常(第{}/{}次): {}", attempt, maxRetry, e.getMessage(), e);
                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        log.error("【本地模型】文本审核已重试{}次仍失败, 放弃", maxRetry);
        return null;
    }

    public QwenDetectionResultDTO auditVideo(QwenDetectionRequestDTO request) {
        if (request == null) {
            return buildErrorResult("VIDEO_REQUEST_EMPTY");
        }
        String businessId = request.getBusinessId();
        log.info("【本地模型】开始视频审核, businessId: {}, minioPath: {}", businessId, request.getMinioPath());

        File videoFile = null;
        try {
            videoFile = prepareVideoFile(request);
            if (videoFile == null) {
                log.error("【本地模型】视频准备失败, businessId={}", businessId);
                return buildErrorResult("视频准备失败");
            }

            LocalAuditResultDTO auditResult = uploadAndAuditVideo(videoFile);
            if (auditResult == null || auditResult.getCode() == null || auditResult.getCode() != 0) {
                log.error("【本地模型】视频审核失败, businessId={}, code={}",
                        businessId, auditResult != null ? auditResult.getCode() : null);
                return buildErrorResult("视频模型审核失败");
            }
            return convertToLocalResult(auditResult, businessId);

        } catch (Exception e) {
            log.error("【本地模型】视频审核异常, businessId: {}, error: {}", businessId, e.getMessage(), e);
            return buildErrorResult("视频审核异常: " + e.getMessage());
        } finally {
            deleteQuietly(videoFile);
        }
    }

    /**
     * 从 MinIO 视频中抽取关键帧
     * 
     * 抽帧策略：定位到视频 1/4 处，避免片头/片尾黑屏
     * 帧转换：Frame → BufferedImage → JPEG Base64
     *
     * @param minioPath  MinIO 对象路径
     * @param businessId 业务ID（日志追踪）
     * @return Base64 编码的帧图片，失败返回 null
     */
    private String extractFrameFromVideo(String minioPath, String businessId) {
        String videoUrl = minioEndpoint + "/" + minioBucketName + "/" + minioPath;
        log.info("【视频抽帧】开始, url={}, businessId={}", videoUrl, businessId);

        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(videoUrl);
            grabber.start();

            // 定位到 1/4 处，避免片头黑屏
            long durationMicros = grabber.getLengthInTime();
            if (durationMicros > 0) {
                long seekMicros = durationMicros / 4;
                grabber.setTimestamp(seekMicros, true);
                log.info("【视频抽帧】视频时长={}ms，定位到={}ms, businessId={}",
                    durationMicros / 1000, seekMicros / 1000, businessId);
            }

            // 获取一帧有效图像（最多尝试 30 次）
            Frame frame = null;
            for (int i = 0; i < 30; i++) {
                Frame f = grabber.grabImage();
                if (f != null && f.image != null) {
                    frame = f;
                    break;
                }
            }

            if (frame == null) {
                log.warn("【视频抽帧】未获取到有效帧, businessId={}", businessId);
                return null;
            }

            // Frame → BufferedImage → JPEG Base64
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage bufferedImage = converter.getBufferedImage(frame);
            converter.close();

            if (bufferedImage == null) {
                log.warn("【视频抽帧】Frame 转换 BufferedImage 失败, businessId={}", businessId);
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "jpeg", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            log.info("【视频抽帧】成功, 帧大小={}bytes, 尺寸={}x{}, businessId={}",
                baos.size(), bufferedImage.getWidth(), bufferedImage.getHeight(), businessId);
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
     * 准备图片文件(MinIO下载或Base64转临时文件)
     */
    private File copyMultipartToTempFile(MultipartFile file) throws IOException {
        String suffix = ".bin";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int dotIdx = originalFilename.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < originalFilename.length() - 1) {
                suffix = originalFilename.substring(dotIdx).toLowerCase();
            }
        }
        File tempFile = File.createTempFile("preaudit_", suffix);
        file.transferTo(tempFile);
        return tempFile;
    }

    private File prepareVideoFile(QwenDetectionRequestDTO request) {
        String minioPath = request.getMinioPath();
        if (minioPath == null || minioPath.trim().isEmpty()) {
            log.error("【本地模型】视频 MinIO 路径为空, businessId={}", request.getBusinessId());
            return null;
        }

        String videoUrl = minioEndpoint + "/" + minioBucketName + "/" + minioPath;
        log.info("【本地模型】从 MinIO 下载视频: {}", videoUrl);
        try {
            HttpResponse response = HttpRequest.get(videoUrl)
                    .timeout(readTimeout)
                    .execute();
            if (response.getStatus() != 200) {
                log.error("【本地模型】MinIO 视频下载失败: status={}, path={}", response.getStatus(), minioPath);
                return null;
            }
            byte[] videoBytes = response.bodyBytes();
            if (videoBytes == null || videoBytes.length == 0) {
                log.error("【本地模型】MinIO 视频为空: path={}", minioPath);
                return null;
            }

            File tempFile = File.createTempFile("audit_video_", suffixFromPath(minioPath, ".mp4"));
            Files.write(tempFile.toPath(), videoBytes);
            log.info("【本地模型】MinIO 视频已下载: size={}bytes, path={}", videoBytes.length, minioPath);
            return tempFile;
        } catch (Exception e) {
            log.error("【本地模型】视频准备异常: path={}, error={}", minioPath, e.getMessage(), e);
            return null;
        }
    }

    private String suffixFromPath(String path, String fallback) {
        if (path == null) {
            return fallback;
        }
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx >= path.length() - 1) {
            return fallback;
        }
        String suffix = path.substring(dotIdx).toLowerCase();
        return suffix.length() > 1 ? suffix : fallback;
    }

    private void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            log.debug("[LocalAudit] temp file cleanup skipped: {}", file.getAbsolutePath());
        }
    }

    private File prepareImageFile(QwenDetectionRequestDTO request) {
        try {
            // 方式1: 从MinIO下载
            if (request.getMinioPath() != null && !request.getMinioPath().isEmpty()) {
                String minioUrl = minioEndpoint + "/" + minioBucketName + "/" + request.getMinioPath();
                log.info("【本地模型】从MinIO下载图片: {}", minioUrl);
                
                HttpResponse response = HttpRequest.get(minioUrl)
                    .timeout(readTimeout)
                    .execute();
                
                if (response.getStatus() != 200) {
                    log.error("【本地模型】MinIO下载失败: status={}", response.getStatus());
                    return null;
                }

                // 从 MinIO 路径识别文件后缀，保留原格式创建临时文件
                String minioPath = request.getMinioPath();
                String suffix = ".jpg";
                int dotIdx = minioPath.lastIndexOf('.');
                if (dotIdx >= 0) {
                    suffix = minioPath.substring(dotIdx).toLowerCase();
                }

                File tempFile = File.createTempFile("audit_", suffix);
                Files.write(tempFile.toPath(), response.bodyBytes());
                log.info("【本地模型】MinIO图片已下载: size={}bytes, suffix={}", response.bodyBytes().length, suffix);

                // 如需转换为 JPG（BMP/GIF/TIFF/WEBP 等格式）
                tempFile = convertToJpgIfNeeded(tempFile);
                return tempFile;
            }

            // 方式2: Base64转临时文件
            if (request.getScreenshotBase64() != null) {
                String base64 = request.getScreenshotBase64();
                
                // 去除Data URL前缀
                if (base64.startsWith("data:")) {
                    int commaIndex = base64.indexOf(',');
                    if (commaIndex > 0) {
                        base64 = base64.substring(commaIndex + 1);
                    }
                }

                byte[] imageBytes = Base64.getDecoder().decode(base64);
                File tempFile = File.createTempFile("audit_", ".jpg");
                Files.write(tempFile.toPath(), imageBytes);
                log.info("【本地模型】Base64图片已转临时文件: size={}bytes", imageBytes.length);
                return tempFile;
            }

            log.error("【本地模型】无可用图片数据");
            return null;

        } catch (Exception e) {
            log.error("【本地模型】图片准备异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将非JPG/PNG格式图片转换为JPG（供本地模型使用）
     * 本地模型不支持 BMP，GIF，TIFF，WEBP 等格式，统一转换为JPG
     */
    private File convertToJpgIfNeeded(File imageFile) throws Exception {
        String name = imageFile.getName().toLowerCase();
        // JPG/JPEG/PNG 直接返回，其余全部转换
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
            return imageFile;
        }
        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            log.warn("【本地模型】图片格式无法识别，跳过转换: {}", imageFile.getName());
            return imageFile;
        }
        File jpgFile = File.createTempFile("converted_", ".jpg");
        ImageIO.write(bufferedImage, "JPEG", jpgFile);
        imageFile.delete(); // 删除原临时文件
        log.info("【本地模型】图片已转换为JPG: {} → {}", imageFile.getName(), jpgFile.getName());
        return jpgFile;
    }

    /**
     * 上传图片到本地模型进行审核
     * 
     * 内置重试机制: 应对模型CPU并发冲突(RuntimeError: could not execute a primitive)导致的500错误
     * 策略: 最多重试3次,每次间隔2秒等待模型恢复
     */
    private LocalAuditResultDTO uploadAndAudit(File imageFile) {
        int attempt = 0;
        while (attempt < maxRetry) {
            attempt++;
            try {
                log.info("【本地模型】上传图片审核(第{}/{}次): url={}, file={}",
                    attempt, maxRetry, imageUrl, imageFile.getName());

                // 构建FormData请求
                HttpResponse response = executeAuditRequest(HttpRequest.post(imageUrl)
                    .form("image", imageFile)
                    .timeout(connectTimeout)
                    .setReadTimeout(readTimeout));

                // 200: 成功
                if (response.getStatus() == 200) {
                    String responseBody = response.body();
                    log.info("【本地模型】审核响应: {}", responseBody);
                    return JSON.parseObject(responseBody, LocalAuditResultDTO.class);
                }

                // 500: 模型并发冲突,等待后重试
                if (response.getStatus() == 401 && isCertSessionAuth()) {
                    log.warn("【本地模型】图片审核认证失效, 第{}/{}次, 准备刷新会话", attempt, maxRetry);
                    clearSessionCookie();
                    if (attempt < maxRetry) {
                        continue;
                    }
                }

                if (response.getStatus() == 500) {
                    log.warn("【本地模型】模型返回500(CPU并发冲突), 第{}/{}次, {}ms后重试, body={}",
                        attempt, maxRetry, retryInterval, response.body());
                    if (attempt < maxRetry) {
                        Thread.sleep(retryInterval);
                        continue;
                    }
                }

                // 其他非200状态
                log.error("【本地模型】HTTP请求失败: status={}, body={}",
                    response.getStatus(), response.body());
                return null;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("【本地模型】重试等待被中断");
                return null;
            } catch (Exception e) {
                log.error("【本地模型】上传审核异常(第{}/{}次): {}", attempt, maxRetry, e.getMessage(), e);
                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        log.error("【本地模型】已重试{}次仍失败, 放弃", maxRetry);
        return null;
    }

    private LocalAuditResultDTO uploadAndAuditVideo(File videoFile) {
        int attempt = 0;
        while (attempt < maxRetry) {
            attempt++;
            try {
                log.info("【本地模型】上传视频审核(第{}/{}次): url={}, file={}, frameInterval={}, maxFrames={}",
                        attempt, maxRetry, videoUrl, videoFile.getName(), videoFrameInterval, videoMaxFrames);

                HttpResponse response = executeAuditRequest(HttpRequest.post(videoUrl)
                        .form("video", videoFile)
                        .form("frame_interval", String.valueOf(videoFrameInterval))
                        .form("max_frames", String.valueOf(videoMaxFrames))
                        .form("save_frames", String.valueOf(videoSaveFrames))
                        .timeout(connectTimeout)
                        .setReadTimeout(readTimeout));

                if (response.getStatus() == 200) {
                    String responseBody = response.body();
                    log.info("【本地模型】视频审核响应: {}", responseBody);
                    return JSON.parseObject(responseBody, LocalAuditResultDTO.class);
                }

                if (response.getStatus() == 401 && isCertSessionAuth()) {
                    log.warn("【本地模型】视频审核认证失效, 第{}/{}次, 准备刷新会话", attempt, maxRetry);
                    clearSessionCookie();
                    if (attempt < maxRetry) {
                        continue;
                    }
                }

                if (response.getStatus() == 500) {
                    log.warn("【本地模型】视频模型返回500, 第{}/{}次, {}ms后重试, body={}",
                            attempt, maxRetry, retryInterval, response.body());
                    if (attempt < maxRetry) {
                        Thread.sleep(retryInterval);
                        continue;
                    }
                }

                log.error("【本地模型】视频HTTP请求失败: status={}, body={}",
                        response.getStatus(), response.body());
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("【本地模型】视频重试等待被中断");
                return null;
            } catch (Exception e) {
                log.error("【本地模型】视频上传审核异常(第{}/{}次): {}", attempt, maxRetry, e.getMessage(), e);
                if (attempt < maxRetry) {
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        log.error("【本地模型】视频审核已重试{}次仍失败, 放弃", maxRetry);
        return null;
    }

    /**
     * 承接本地内容审核系统结果，不做违规类型和置信度推断。
     */
    private QwenDetectionResultDTO convertToLocalResult(LocalAuditResultDTO auditResult, String businessId) {
        QwenDetectionResultDTO dto = new QwenDetectionResultDTO();
            
        try {
            LocalAuditResultDTO.AuditData data = auditResult.getData();
            if (data == null) {
                throw new IllegalArgumentException("审核响应数据为空");
            }

            String auditResultStr = normalizeAuditResultLabel(data.getAuditResult());
            dto.setAuditResult(auditResultStr);
            dto.setDetectionResult(toInternalDetectionResult(auditResultStr));
            dto.setViolationLevel(normalizeViolationLevelLabel(data.getViolationLevel()));
            dto.setLocalAuditData(data);

            LocalAuditResultDTO.Violation firstViolation = firstViolation(data);
            if (firstViolation != null) {
                dto.setViolationType(mapViolationTypeFromModel(firstViolation));
                if (firstViolation.getConfidence() != null) {
                    dto.setConfidence((int) Math.round(firstViolation.getConfidence() * 100));
                }
                dto.setReason(firstNonBlank(firstViolation.getViolationReason(),
                        firstNonBlank(firstViolation.getKeyword(),
                                firstNonBlank(firstViolation.getText(), firstViolation.getClassName()))));
            } else {
                dto.setViolationType("通过".equals(auditResultStr) ? "none" : "other");
            }
    
            // OCR 文本记录日志（仅供排查，不参与判定）
            if (data.getFullText() != null && !data.getFullText().isEmpty()) {
                log.info("【本地模型】OCR识别文本: {}", data.getFullText());
            }
    
            // 统计信息
            if (data.getSummary() != null) {
                log.info("【本地模型】统计: 目标{}个, 文本{}条, 违规{}项", 
                    data.getSummary().getTotalObjects(),
                    data.getSummary().getTotalTexts(),
                    data.getSummary().getTotalViolations());
            }
    
            // 请求ID
            dto.setRequestId("local-audit-" + System.currentTimeMillis());
    
            // Token统计(本地模型无Token概念,设为0)
            QwenDetectionResultDTO.TokenUsage tokenUsage = new QwenDetectionResultDTO.TokenUsage();
            tokenUsage.setInputTokens(0);
            tokenUsage.setOutputTokens(0);
            tokenUsage.setTotalTokens(0);
            dto.setTokenUsage(tokenUsage);
    
            log.info("【本地模型】结果承接完成: businessId={}, auditResult={}, violationLevel={}",
                    businessId, dto.getAuditResult(), dto.getViolationLevel());
    
        } catch (Exception e) {
            log.error("【本地模型】结果承接异常: {}", e.getMessage(), e);
            dto.setDetectionResult("pending");
            dto.setAuditResult("复核");
            dto.setViolationLevel("无");
            dto.setReason("结果承接失败: " + e.getMessage());
        }
    
        return dto;
    }

    private String toInternalDetectionResult(String auditResult) {
        String normalized = normalizeAuditResultLabel(auditResult);
        if ("阻断".equals(normalized) || "复核".equals(normalized)) {
            return "violation";
        }
        if ("通过".equals(normalized)) {
            return "compliant";
        }
        return "pending";
    }

    private HttpResponse executeAuditRequest(HttpRequest request) {
        applyAuth(request);
        return request.execute();
    }

    private void applyAuth(HttpRequest request) {
        String mode = normalizeConfig(authMode);
        if ("bearer".equals(mode)) {
            String token = trimToNull(authToken);
            if (token != null) {
                request.header("Authorization", token.toLowerCase(Locale.ROOT).startsWith("bearer ")
                        ? token
                        : "Bearer " + token);
            }
            return;
        }
        if ("cert-session".equals(mode) || "session".equals(mode)) {
            String cookie = ensureSessionCookie();
            if (cookie != null) {
                request.cookie(cookie);
            }
        }
    }

    private boolean isCertSessionAuth() {
        String mode = normalizeConfig(authMode);
        return "cert-session".equals(mode) || "session".equals(mode);
    }

    private void clearSessionCookie() {
        sessionCookie = null;
    }

    private String ensureSessionCookie() {
        String current = trimToNull(sessionCookie);
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = trimToNull(sessionCookie);
            if (current != null) {
                return current;
            }
            sessionCookie = loginAndReadCookie();
            return sessionCookie;
        }
    }

    private String loginAndReadCookie() {
        String loginUrl = resolveAuthUrl();
        if (loginUrl == null) {
            throw new IllegalStateException("local-audit auth-url is empty");
        }
        String certContent = resolveCertContent();
        Map<String, Object> body = new HashMap<>();
        body.put("username", trimToNull(authUsername) != null ? authUsername.trim() : "admin");
        body.put("password", trimToNull(authPassword) != null ? authPassword : "");
        body.put("cert_content", certContent != null ? certContent : "");

        HttpResponse response = HttpRequest.post(loginUrl)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(JSON.toJSONString(body))
                .timeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .execute();
        if (response.getStatus() != 200) {
            throw new IllegalStateException("local audit cert login failed, status=" + response.getStatus());
        }
        LocalAuditResultDTO loginResult = JSON.parseObject(response.body(), LocalAuditResultDTO.class);
        if (loginResult == null || loginResult.getCode() == null || loginResult.getCode() != 0) {
            throw new IllegalStateException("local audit cert login rejected, code="
                    + (loginResult != null ? loginResult.getCode() : null));
        }
        String setCookie = trimToNull(response.header("Set-Cookie"));
        if (setCookie == null) {
            throw new IllegalStateException("local audit cert login missing session cookie");
        }
        String cookie = setCookie.split(";", 2)[0].trim();
        log.info("【本地模型】证书会话登录成功, username={}", trimToNull(authUsername) != null ? authUsername.trim() : "admin");
        return cookie;
    }

    private String resolveAuthUrl() {
        String configured = trimToNull(authUrl);
        if (configured != null) {
            return configured;
        }
        String auditUrl = trimToNull(imageUrl);
        if (auditUrl == null) {
            return null;
        }
        int idx = auditUrl.indexOf("/api/audit/");
        if (idx < 0) {
            return null;
        }
        return auditUrl.substring(0, idx) + "/api/auth/cert-login";
    }

    private String resolveCertContent() {
        String configured = trimToNull(authCertContent);
        if (configured != null) {
            return configured;
        }
        String path = trimToNull(authCertPath);
        if (path == null) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("read local audit cert failed: " + e.getMessage(), e);
        }
    }

    private String normalizeAuditResultLabel(String auditResult) {
        String value = trimToNull(auditResult);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("pass".equals(lower) || "allow".equals(lower) || "approved".equals(lower) || "通过".equals(value)) {
            return "通过";
        }
        if ("review".equals(lower) || "manual".equals(lower) || "复核".equals(value)) {
            return "复核";
        }
        if ("block".equals(lower) || "blocked".equals(lower) || "reject".equals(lower)
                || "rejected".equals(lower) || "阻断".equals(value)) {
            return "阻断";
        }
        return value;
    }

    private String normalizeViolationLevelLabel(String violationLevel) {
        String value = trimToNull(violationLevel);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("none".equals(lower) || "无".equals(value)) {
            return "无";
        }
        if ("low".equals(lower) || "低".equals(value)) {
            return "低";
        }
        if ("medium".equals(lower) || "中".equals(value)) {
            return "中";
        }
        if ("high".equals(lower) || "高".equals(value)) {
            return "高";
        }
        return value;
    }

    private String normalizeConfig(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "none" : normalized.toLowerCase(Locale.ROOT);
    }

    private LocalAuditResultDTO.Violation firstViolation(LocalAuditResultDTO.AuditData data) {
        if (data == null || data.getViolations() == null || data.getViolations().isEmpty()) {
            return null;
        }
        return data.getViolations().get(0);
    }

    /**
     * 将模型违规类型映射为内容模块内部类型，供前端和告警链路复用。
     */
    private String mapViolationTypeFromModel(LocalAuditResultDTO.Violation violation) {
        if (violation == null) {
            return "none";
        }
        String sceneType = trimToNull(violation.getSceneType());
        if (sceneType != null) {
            String mapped = mapViolationTypeText(sceneType);
            if (!"other".equals(mapped)) {
                return mapped;
            }
        }
        return mapViolationTypeText(firstNonBlank(violation.getType(), violation.getKeyword()));
    }

    private String mapViolationTypeText(String rawType) {
        String value = trimToNull(rawType);
        if (value == null || "无".equals(value) || "none".equalsIgnoreCase(value)) {
            return "none";
        }

        switch (value) {
            case "色情":
            case "色情内容":
            case "色情场景":
            case "疑似色情":
            case "porn":
            case "pornography":
                return "pornography";

            case "暴力":
            case "暴力内容":
            case "暴力场景":
            case "暴力行为":
            case "疑似打架":
            case "疑似血腥":
            case "斗殴":
            case "伤害":
            case "物体违规":
            case "violence":
            case "object":
                return "violence";

            case "敏感信息":
            case "敏感场景":
            case "人员密集":
            case "政治旗帜":
            case "文字违规":
            case "text":
            case "keyword":
            case "sensitive":
                return "sensitive";

            case "其他":
            case "其他违规":
            case "场景违规":
            case "行为违规":
            case "人脸违规":
            case "scene":
            case "forbidden_image":
            case "other":
                return "other";

            default:
                return "other";
        }
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 构建错误结果
     */
    private QwenDetectionResultDTO buildErrorResult(String reason) {
        QwenDetectionResultDTO errorResult = new QwenDetectionResultDTO();
        errorResult.setDetectionResult("pending");
        errorResult.setAuditResult("复核");
        errorResult.setViolationLevel("无");
        errorResult.setReason(reason);
        errorResult.setRequestId("error-" + System.currentTimeMillis());
        return errorResult;
    }
}
