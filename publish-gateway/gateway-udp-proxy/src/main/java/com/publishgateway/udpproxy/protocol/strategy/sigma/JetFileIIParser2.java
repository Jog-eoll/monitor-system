package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * JetFileII协议解析器 - 第二/三种消息格式
 * 
 * 用于点级数据传输(无线通信场景)
 * 
 * 协议格式:
 * <SYN Code(2)><CheckSum(2)><Data Len(2)><Source Addr(2)><Dest Addr(2)>
 * <Packet Serial(2)><Main CMD(1)><Sub CMD(1)><Arg Len(1)><Flag(1)><Arg(N*4)><Data(N)>
 * 
 * 同步码:
 * - 第二种: 0x55 0xa7 (普通和校验) / 0x55 0xa3 (CRC校验)
 * - 第三种: 0x55 0xa8 (普通和校验) / 0x55 0xa4 (CRC校验)
 */
@Slf4j
public class JetFileIIParser2 {

    // 同步码常量
    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xa7;     // 第二种-和校验
    private static final byte SYN_TYPE2_CRC = (byte) 0xa3;     // 第二种-CRC校验
    private static final byte SYN_TYPE3_SUM = (byte) 0xa8;     // 第三种-和校验
    private static final byte SYN_TYPE3_CRC = (byte) 0xa4;     // 第三种-CRC校验

    /**
     * 解析第二/三种消息格式
     * 
     * @param data 原始UDP数据
     * @return 解析结果,如果不是有效格式则返回null
     */
    public static JetFileIIMessage2 parse(byte[] data) {
        if (data == null || data.length < 16) {
            return null;
        }

        try {
            // 【调试】打印前32个字节的十六进制
            int debugLen = Math.min(32, data.length);
            StringBuilder hexDebug = new StringBuilder();
            for (int i = 0; i < debugLen; i++) {
                hexDebug.append(String.format("%02X ", data[i]));
            }
            log.info("【协议调试2】收到数据包前{}字节: {} (总长度:{})", debugLen, hexDebug.toString(), data.length);

            // 1. 验证同步码
            if (data[0] != SYN_BYTE1) {
                log.debug("非JetFileII第二/三种格式: 同步码首字节不是0x55, 实际=0x{}", 
                        String.format("%02X", data[0]));
                return null;
            }

            byte synByte2 = data[1];
            MessageType messageType;
            ChecksumType checksumType;

            if (synByte2 == SYN_TYPE2_SUM || synByte2 == SYN_TYPE2_CRC) {
                messageType = MessageType.TYPE2;
                checksumType = (synByte2 == SYN_TYPE2_SUM) ? ChecksumType.SUM : ChecksumType.CRC;
            } else if (synByte2 == SYN_TYPE3_SUM || synByte2 == SYN_TYPE3_CRC) {
                messageType = MessageType.TYPE3;
                checksumType = (synByte2 == SYN_TYPE3_SUM) ? ChecksumType.SUM : ChecksumType.CRC;
            } else {
                log.debug("非JetFileII第二/三种格式: 同步码第二字节不匹配, 实际=0x{}", 
                        String.format("%02X", synByte2));
                return null;
            }

            log.info("【协议识别】JetFileII {} - {} 校验", messageType, checksumType);

            // 2. 解析校验和 (2字节, 小端序)
            int checksum = ByteBuffer.wrap(data, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

            // 3. 解析数据长度 (2字节, 小端序)
            int dataLen = ByteBuffer.wrap(data, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

            // 4. 解析源地址 (2字节, 小端序)
            int sourceAddr = ByteBuffer.wrap(data, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

            // 5. 解析目的地址 (2字节, 小端序) - GG(Group) UU(Unit)
            byte groupAddr = data[8];
            byte unitAddr = data[9];

            // 6. 解析包序号 (2字节, 小端序)
            int packetSerial = ByteBuffer.wrap(data, 10, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

            // 7. 解析主命令
            byte mainCmd = data[12];

            // 8. 解析子命令
            byte subCmd = data[13];

            // 9. 解析参数长度 (单位: 4字节)
            int argLen = data[14] & 0xFF;
            int argBytes = argLen * 4;

            // 10. 解析标志位
            byte flag = data[15];
            boolean needResponse = (flag == 0);

            // 11. 解析参数 (如果有)
            byte[] arguments = null;
            int dataStartIndex = 16 + argBytes;
            if (argLen > 0 && data.length >= dataStartIndex) {
                arguments = new byte[argBytes];
                System.arraycopy(data, 16, arguments, 0, argBytes);
            }

            // 12. 解析数据部分
            byte[] payload = null;
            if (dataLen > 0 && data.length >= dataStartIndex) {
                int actualDataLen = Math.min(dataLen, data.length - dataStartIndex);
                if (actualDataLen > 0) {
                    payload = new byte[actualDataLen];
                    System.arraycopy(data, dataStartIndex, payload, 0, actualDataLen);
                }
            }

            // 13. 校验和验证 (可选,暂时跳过)
            // TODO: 实现和校验和CRC校验

            // 14. 构建解析结果
            JetFileIIMessage2 message = new JetFileIIMessage2();
            message.setRawData(data);
            message.setMessageType(messageType);
            message.setChecksumType(checksumType);
            message.setChecksum(checksum);
            message.setDataLen(dataLen);
            message.setSourceAddr(sourceAddr);
            message.setGroupAddr(groupAddr & 0xFF);
            message.setUnitAddr(unitAddr & 0xFF);
            message.setPacketSerial(packetSerial);
            message.setMainCmd(mainCmd & 0xFF);
            message.setSubCmd(subCmd & 0xFF);
            message.setArgLen(argLen);
            message.setNeedResponse(needResponse);
            message.setArguments(arguments);
            message.setPayload(payload);

            // 15. 尝试解析payload内容
            parsePayload(message);

            log.info("【协议解析】JetFileII{}解析成功: 源={}, 目的={}:{}, 主命令=0x{}, 子命令=0x{}, 数据长度={}",
                    messageType,
                    String.format("0x%04X", sourceAddr),
                    groupAddr & 0xFF,
                    unitAddr & 0xFF,
                    String.format("%02X", mainCmd),
                    String.format("%02X", subCmd),
                    payload != null ? payload.length : 0);

            return message;

        } catch (Exception e) {
            log.error("【协议解析】JetFileII第二/三种格式解析失败", e);
            return null;
        }
    }

    /**
     * 解析payload内容
     */
    private static void parsePayload(JetFileIIMessage2 message) {
        if (message.getPayload() == null || message.getPayload().length == 0) {
            return;
        }

        try {
            byte[] payload = message.getPayload();

            // 尝试判断内容类型
            // 1. 检查是否为图片(JPEG, PNG, GIF等)
            if (isImage(payload)) {
                message.setContentType("image");
                message.setImageFormat(detectImageFormat(payload));
                log.info("【内容识别】检测到图片格式: {}", message.getImageFormat());
            }
            // 2. 尝试作为文本解析
            else {
                String textContent = new String(payload, StandardCharsets.UTF_8);
                // 检查是否为可打印字符
                if (isPrintableText(textContent)) {
                    message.setContentType("text");
                    message.setTextContent(textContent);
                    log.info("【内容识别】检测到文本内容: {}", 
                            textContent.length() > 50 ? textContent.substring(0, 50) + "..." : textContent);
                } else {
                    message.setContentType("binary");
                    log.info("【内容识别】二进制数据: {} 字节", payload.length);
                }
            }
        } catch (Exception e) {
            log.warn("【内容识别】payload解析失败: {}", e.getMessage());
            message.setContentType("binary");
        }
    }

    /**
     * 检查是否为图片数据
     */
    private static boolean isImage(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }

        // JPEG: FF D8 FF
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return true;
        }

        // GIF: 47 49 46
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) {
            return true;
        }

        // BMP: 42 4D
        if (data[0] == 0x42 && data[1] == 0x4D) {
            return true;
        }

        return false;
    }

    /**
     * 检测图片格式
     */
    private static String detectImageFormat(byte[] data) {
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            return "JPEG";
        }
        if (data[0] == (byte) 0x89 && data[1] == 0x50) {
            return "PNG";
        }
        if (data[0] == 0x47 && data[1] == 0x49) {
            return "GIF";
        }
        if (data[0] == 0x42 && data[1] == 0x4D) {
            return "BMP";
        }
        return "UNKNOWN";
    }

    /**
     * 检查是否为可打印文本
     */
    private static boolean isPrintableText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 简单判断: 至少80%的字符是可打印字符
        int printableCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x20 && c <= 0x7E || c >= 0x4E00 && c <= 0x9FA5) {
                printableCount++;
            }
        }
        return (printableCount * 1.0 / text.length()) > 0.8;
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        TYPE2("第二种格式"),
        TYPE3("第三种格式");

        private final String description;

        MessageType(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * 校验类型枚举
     */
    public enum ChecksumType {
        SUM("和校验"),
        CRC("CRC校验");

        private final String description;

        ChecksumType(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * JetFileII第二/三种消息实体
     */
    @Data
    public static class JetFileIIMessage2 {
        /** 原始数据 */
        private byte[] rawData;

        /** 消息类型 */
        private MessageType messageType;

        /** 校验类型 */
        private ChecksumType checksumType;

        /** 校验和 */
        private int checksum;

        /** 数据长度 */
        private int dataLen;

        /** 源地址 */
        private int sourceAddr;

        /** 目的组地址 */
        private int groupAddr;

        /** 目的单元地址 */
        private int unitAddr;

        /** 包序号 */
        private int packetSerial;

        /** 主命令 */
        private int mainCmd;

        /** 子命令 */
        private int subCmd;

        /** 参数长度(N*4字节) */
        private int argLen;

        /** 是否需要响应 */
        private boolean needResponse;

        /** 参数数据 */
        private byte[] arguments;

        /** 载荷数据 */
        private byte[] payload;

        /** 内容类型: text/image/binary */
        private String contentType;

        /** 文本内容(如果是文本) */
        private String textContent;

        /** 图片格式(如果是图片) */
        private String imageFormat;

        /**
         * 获取目的地址字符串
         */
        public String getDestinationAddress() {
            return String.format("%d:%d", groupAddr, unitAddr);
        }

        /**
         * 获取主命令十六进制
         */
        public String getMainCmdHex() {
            return String.format("0x%02X", mainCmd);
        }

        /**
         * 获取子命令十六进制
         */
        public String getSubCmdHex() {
            return String.format("0x%02X", subCmd);
        }

        /**
         * 获取内容摘要
         */
        public String getContentSummary() {
            if ("text".equals(contentType) && textContent != null) {
                return textContent.length() > 100 
                    ? textContent.substring(0, 100) + "..." 
                    : textContent;
            } else if ("image".equals(contentType)) {
                return String.format("[%s图片: %d字节]", imageFormat, payload != null ? payload.length : 0);
            } else {
                return String.format("[二进制数据: %d字节]", payload != null ? payload.length : 0);
            }
        }
    }
}
