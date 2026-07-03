package com.gateway.standardization.service;

import com.gateway.standardization.dto.MessageType;
import com.gateway.standardization.dto.StandardizedMessage;

/**
 * 标准化消息封装服务
 * <p>
 * 将 publish-gateway 现有的 ReportPayload 字段映射为标准化的 StandardizedMessage，
 * 补齐 sessionId、messageType、action、target 等文档要求的字段。
 * </p>
 * <p>
 * 本服务为独立模块，不修改现有 DataReportService 和 ReportPayload，
 * 由调用方在上报前选择性地使用本服务生成标准化消息。
 * </p>
 */
public interface StandardizationService {

    /**
     * 从现有上报数据构建标准化消息
     *
     * @param gatewayId    网关标识
     * @param chainId      链路 ID
     * @param sourceIp     来源 IP
     * @param boardIp      目标情报板 IP
     * @param boardPort    目标情报板端口
     * @param protocol     原始协议标识
     * @param contentType  内容类型
     * @param commandType  命令类型（如 SEND_FILE, FILE_PLAY, 控制指令等）
     * @param mainCmd      主命令（十六进制字符串，可为 null）
     * @param subCmd       子命令（十六进制字符串，可为 null）
     * @param fileName     文件名
     * @param fileType     文件扩展名
     * @param data         内容数据（文本或 Base64）
     * @param minioPath    MinIO 存储路径
     * @param rawPacket    原始包 Base64
     * @param packetSerial 包序号
     * @return 标准化消息
     */
    StandardizedMessage buildStandardizedMessage(
            String gatewayId, String chainId,
            String sourceIp, String boardIp, Integer boardPort,
            String protocol, String contentType,
            String commandType, String mainCmd, String subCmd,
            String fileName, String fileType, String data, String minioPath,
            String rawPacket, Integer packetSerial);

    /**
     * 根据协议和命令类型推断 messageType
     *
     * @param protocol    协议标识
     * @param commandType 命令类型
     * @param contentType 内容类型
     * @return 消息类型
     */
    MessageType resolveMessageType(String protocol, String commandType, String contentType);
}
