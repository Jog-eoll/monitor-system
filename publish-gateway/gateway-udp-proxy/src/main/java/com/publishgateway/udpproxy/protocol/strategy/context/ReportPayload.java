package com.publishgateway.udpproxy.protocol.strategy.context;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.Base64;

/**

 * 字段分四组：
 *  - 公共字段：businessId、gatewayId、chainId
 *  - 协议标识：protocol、commandType、mainCmd
 *  - 内容数据：contentType、data、fileName 等
 *  - 媒体资源：minioPath、screenshotBase64、imageFormat
 */
@Data
public class ReportPayload {

    // ===== 公共字段（DataReportService 填充） =====

    /** 业务ID，格式 ruleId-时间戳，幂等键 */
    private String businessId;

    /** 内容ID（文本上报时与 businessId 相同） */
    private String contentId;

    /** 网关ID */
    private String gatewayId;

    /** 设备ID */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 链路ID */
    private Long chainId;

    /** 数据来源IP */
    private String sourceIp;

    /** 采集时间（yyyy-MM-dd HH:mm:ss） */
    private String captureTime;

    /** 时间戳 */
    private Long timestamp;

    /** 情报板IP（转发目标IP） */
    private String boardIp;

    /** 情报板端口（转发目标端口） */
    private Integer boardPort;

    /** 原始包 Base64 编码 */
    private String rawPacket;

    /** Playlist batch ID. */
    private String playBatchId;

    /** Sequence inside the playlist batch, starting from 0. */
    private Integer playBatchSeq;

    /** Total item count of the playlist batch. */
    private Integer playBatchSize;

    /** Original publish request ID for matching diagnostic_event_log.trace_id. */
    private String publishRequestId;

    // ===== 协议标识字段 =====

    /** 协议名称，如 JetFileII-Type1、Sigma-FileTransfer、Nova-FileTransfer */
    private String protocol;

    /** 指令类型，如 SEND_FILE、FILE_PLAY */
    private String commandType;

    /** 主命令（16进制字符串，如 0x12） */
    private String mainCmd;

    /** 子命令（16进制字符串） */
    private String subCmd;

    /** 包序号 */
    private Integer packetSerial;

    // ===== 地址字段 =====

    /** 源地址（设备/控制器地址，如 0x0001） */
    private String sourceAddr;

    /** 目的地址 */
    private String destAddr;

    /** 设备逻辑地址（Sigma JetFileII Type1 使用） */
    private String address;

    // ===== 内容字段 =====

    /**
     * 内容类型：
     *  text           - 文本内容
     *  image          - 图片内容
     *  binary         - 二进制
     *  image_reference - 图片文件名引用
     *  file_reference  - 文件名引用
     */
    private String contentType;

    /** 文本内容 */
    private String data;

    /** 文件名 */
    private String fileName;

    /** 文件完整路径 */
    private String filePath;

    /** 文件扩展名 */
    private String fileExtension;

    /** 可读描述，用于日志和管控平台展示 */
    private String description;

    // ===== 媒体资源字段 =====

    /** MinIO 存储路径，优先使用此字段 */
    private String minioPath;

    /** 图片 Base64 编码 */
    private String screenshotBase64;

    /** 图片格式，如 JPEG、PNG、GIF、BMP */
    private String imageFormat;

    /**
     * Analysis-only full image Base64. It is not sent to the content platform.
     */
    @JSONField(serialize = false)
    private String imageAnalysisBase64;

    // ===== 文件传输统计字段 =====

    /** 文件传输总包数 */
    private Integer totalPackets;

    /** 文件传输总字节数 */
    private Integer totalSize;

    // ===== Relay file signature audit fields =====

    private Boolean relaySignatureEnabled;

    private Boolean relaySignatureSigned;

    private Boolean relaySignatureVerified;

    private Boolean relaySignatureAllowed;

    private Boolean relaySignatureAuditOnly;

    private String relaySignatureMode;

    private String relaySignatureAlgorithm;

    private String relaySignatureHashAlgorithm;

    private String relaySignatureFileHash;

    private String relaySignatureError;

    private String relaySignatureSource;

    private String relaySignatureClientId;

    private String relaySignatureClientCertId;

    private String relaySignatureFileId;

    private Boolean relaySignatureClientRecordMatched;



    /** 构建文本上报 */
    public static ReportPayload ofText(String protocol, String data, String description) {
        ReportPayload p = new ReportPayload();
        p.protocol = protocol;
        p.contentType = "text";
        p.data = data;
        p.description = description;
        return p;
    }

    /** 构建图片上报 */
    public static ReportPayload ofImage(String protocol, String minioPath, String imageFormat, String description) {
        ReportPayload p = new ReportPayload();
        p.protocol = protocol;
        p.contentType = "image";
        p.minioPath = minioPath;
        p.imageFormat = imageFormat;
        p.description = description;
        return p;
    }

    /** 构建图片上报 */
    public static ReportPayload ofImageBase64(String protocol, String base64, String imageFormat, String description) {
        ReportPayload p = new ReportPayload();
        p.protocol = protocol;
        p.contentType = "image";
        p.screenshotBase64 = base64;
        p.imageAnalysisBase64 = base64;
        p.imageFormat = imageFormat;
        p.description = description;
        return p;
    }

    /** 构建二进制上报 */
    public void captureImageForAnalysis(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }
        this.imageAnalysisBase64 = Base64.getEncoder().encodeToString(imageBytes);
    }

    public static ReportPayload ofBinary(String protocol, String base64Data, String description) {
        ReportPayload p = new ReportPayload();
        p.protocol = protocol;
        p.contentType = "binary";
        p.data = base64Data;
        p.description = description;
        return p;
    }

    /**
     * file_reference 上报前调用，统一为 text 类型
     */
    public void normalizeForTextReport() {
        if ("file_reference".equals(contentType)) {
            contentType = "text";
        }
        if (contentId == null) {
            contentId = businessId;
        }
    }

    /** 是否包含图片数据 */
    public boolean hasImageData() {
        return (minioPath != null && !minioPath.isEmpty())
                || (screenshotBase64 != null && !screenshotBase64.isEmpty());
    }
}
