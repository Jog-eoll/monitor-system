package com.gateway.standardization.service.impl;

import com.gateway.standardization.dto.MessageType;
import com.gateway.standardization.dto.StandardizedMessage;
import com.gateway.standardization.dto.TargetInfo;
import com.gateway.standardization.service.CapabilityMapper;
import com.gateway.standardization.service.StandardizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 标准化消息封装服务实现
 */
@Slf4j
@Service
public class StandardizationServiceImpl implements StandardizationService {

    @Resource
    private CapabilityMapper capabilityMapper;

    @Override
    public StandardizedMessage buildStandardizedMessage(
            String gatewayId, String chainId,
            String sourceIp, String boardIp, Integer boardPort,
            String protocol, String contentType,
            String commandType, String mainCmd, String subCmd,
            String fileName, String fileType, String data, String minioPath,
            String rawPacket, Integer packetSerial) {

        StandardizedMessage msg = new StandardizedMessage();

        // ========== 必填字段 ==========
        msg.setGatewayId(gatewayId);
        msg.setChainId(chainId);
        msg.setSourceIp(sourceIp);
        msg.setProtocol(protocol);
        msg.setContentType(contentType);

        // sessionId: 最小实现使用 chainId 作为会话标识
        msg.setSessionId(chainId);

        // requestId: 使用 gatewayId + 时间戳 + 包序号生成
        msg.setRequestId(gatewayId + "-" + System.currentTimeMillis()
                + (packetSerial != null ? "-" + packetSerial : ""));

        // sequenceNo: 统一填充包序号
        msg.setSequenceNo(packetSerial);

        // messageType: 根据协议和命令类型推断
        MessageType messageType = resolveMessageType(protocol, commandType, contentType);
        msg.setMessageType(messageType.name());

        // target: 结构化对象（deviceId 暂时使用 boardIp，后续从设备注册信息获取）
        msg.setTarget(new TargetInfo(boardIp, boardIp, boardPort));

        // action: 业务动作（对外契约字段，中文动作名称）
        String action = capabilityMapper.mapAction(protocol, contentType, commandType);
        msg.setAction(action);

        // ========== 可选字段 ==========

        // command
        if (mainCmd != null || subCmd != null) {
            StandardizedMessage.CommandInfo cmdInfo = new StandardizedMessage.CommandInfo();
            cmdInfo.setMainCommand(mainCmd);
            cmdInfo.setSubCommand(subCmd);
            cmdInfo.setAckRequired(isAckRequired(commandType));
            msg.setCommand(cmdInfo);
        }

        // content
        if (fileName != null || data != null || minioPath != null) {
            StandardizedMessage.ContentInfo contentInfo = new StandardizedMessage.ContentInfo();
            contentInfo.setFileName(fileName);
            contentInfo.setFileType(fileType);
            contentInfo.setData(data);
            contentInfo.setMinioPath(minioPath);
            msg.setContent(contentInfo);
        }

        // rawPacketBase64: 兜底字段
        msg.setRawPacketBase64(rawPacket);

        return msg;
    }

    @Override
    public MessageType resolveMessageType(String protocol, String commandType, String contentType) {
        if (commandType == null) {
            return MessageType.RAW_PACKET;
        }

        String cmd = commandType.toUpperCase();

        // 播放列表指令
        if (cmd.contains("FILE_PLAY") || cmd.contains("PLAYLIST") || cmd.contains("PLAY_LIST")) {
            return MessageType.PLAYLIST;
        }

        // 控制指令
        if (cmd.contains("BRIGHTNESS") || cmd.contains("POWER") || cmd.contains("SCREEN")
                || cmd.contains("TIME_SYNC") || cmd.contains("COLOR_TEST")
                || cmd.contains("BLACKOUT") || cmd.contains("REBOOT")) {
            return MessageType.COMMAND;
        }

        // 内容传输
        if (cmd.contains("SEND_FILE") || cmd.contains("FILE_TRANSFER")
                || cmd.contains("UPLOAD") || cmd.contains("RESOURCE")) {
            // 分包场景
            if (cmd.contains("FRAGMENT") || cmd.contains("CHUNK") || cmd.contains("PART")) {
                return MessageType.CONTENT_FRAGMENT;
            }
            return MessageType.CONTENT;
        }

        // Sigma/Nova 协议中有内容类型时视为 CONTENT
        if (contentType != null && !contentType.isEmpty()) {
            return MessageType.CONTENT;
        }

        return MessageType.RAW_PACKET;
    }

    // ========== 内部方法 ==========

    /**
     * 判断是否需要 ACK
     */
    private boolean isAckRequired(String commandType) {
        if (commandType == null) {
            return false;
        }
        String cmd = commandType.toUpperCase();
        // 文件传输和播放列表指令通常需要 ACK
        return cmd.contains("SEND_FILE") || cmd.contains("FILE_PLAY")
                || cmd.contains("PLAYLIST") || cmd.contains("UPLOAD");
    }
}
