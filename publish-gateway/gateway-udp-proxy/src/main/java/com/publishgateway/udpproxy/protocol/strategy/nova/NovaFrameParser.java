package com.publishgateway.udpproxy.protocol.strategy.nova;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 诺瓦交通协议帧解析器
 *
 * 帧格式: 0xAA + [设备地址(2B) + 指令码(1B) + 数据域(nB)] + 0xCC + CRC16(2B)
 *
 * 转义规则（帧内容中）:
 * - 0xAA → 0xEE 0x0A
 * - 0xCC → 0xEE 0x0C
 * - 0xEE → 0xEE 0x0E
 *
 * 字节序: 小端序（低位在前，高位在后）
 * CRC-16: 对转义后的完整帧（含起始符和结束符）计算，低位在前高位在后存储
 *
 * @Author: zyh
 * @Date: 2026/4/8
 */
@Slf4j
public class NovaFrameParser {

    /** 帧起始符 */
    private static final byte FRAME_START = (byte) 0xAA;
    /** 帧结束符 */
    private static final byte FRAME_END = (byte) 0xCC;
    /** 转义前缀 */
    private static final byte ESCAPE_PREFIX = (byte) 0xEE;

    /**
     * 从原始数据中解析所有 Nova 协议帧
     *
     * @param data 原始网络数据（可能包含一个或多个帧）
     * @return 解析出的帧列表，找不到有效帧则返回空列表
     */
    public static List<NovaFrame> parseFrames(byte[] data) {
        List<NovaFrame> frames = new ArrayList<>();
        if (data == null || data.length < 7) {
            // 最小帧: AA(1) + addr(2) + cmd(1) + CC(1) + CRC(2) = 7
            return frames;
        }

        int pos = 0;
        while (pos < data.length) {
            // 查找起始符 0xAA
            if (data[pos] != FRAME_START) {
                pos++;
                continue;
            }

            // 从 pos+1 开始扫描，查找真正的结束符 0xCC（排除转义）
            int contentStart = pos + 1;
            int endPos = findFrameEnd(data, contentStart);
            if (endPos < 0) {
                break; // 没找到结束符，数据不完整
            }

            // 结束符后面需要有 2 字节 CRC
            if (endPos + 2 >= data.length) {
                log.debug("【Nova帧解析】CRC数据不完整: endPos={}, dataLen={}", endPos, data.length);
                break;
            }

            // 提取帧内容（起始符和结束符之间的转义数据）
            int escapedLen = endPos - contentStart;
            byte[] escapedContent = new byte[escapedLen];
            System.arraycopy(data, contentStart, escapedContent, 0, escapedLen);

            // 提取 CRC（结束符后2字节，低位在前）
            int crcLow = data[endPos + 1] & 0xFF;
            int crcHigh = data[endPos + 2] & 0xFF;
            int receivedCrc = (crcHigh << 8) | crcLow;

            // 验证 CRC（对含起始符和结束符的转义数据计算）
            int frameLen = endPos - pos + 1; // AA + escaped + CC
            byte[] frameForCrc = new byte[frameLen];
            System.arraycopy(data, pos, frameForCrc, 0, frameLen);
            int calculatedCrc = calculateCrc16(frameForCrc);

            if (calculatedCrc != receivedCrc) {
                log.debug("【Nova帧解析】CRC不匹配: 计算=0x{}, 接收=0x{}, 仍尝试解析",
                        String.format("%04X", calculatedCrc), String.format("%04X", receivedCrc));
            }

            // 反转义
            byte[] content = unescape(escapedContent);

            // 解析帧内容: 设备地址(2B小端) + 指令码(1B) + 数据域(nB)
            if (content.length >= 3) {
                int deviceAddr = (content[0] & 0xFF) | ((content[1] & 0xFF) << 8);
                int commandCode = content[2] & 0xFF;
                byte[] dataField = new byte[content.length - 3];
                if (dataField.length > 0) {
                    System.arraycopy(content, 3, dataField, 0, dataField.length);
                }

                frames.add(new NovaFrame(deviceAddr, commandCode, dataField));
                log.debug("【Nova帧解析】成功: 设备地址=0x{}, 指令码=0x{}, 数据域={}B",
                        String.format("%04X", deviceAddr),
                        String.format("%02X", commandCode),
                        dataField.length);
            }

            // 移动到下一帧: 结束符位置 + CRC(2B) + 1
            pos = endPos + 3;
        }

        return frames;
    }

    /**
     * 查找帧结束符 0xCC 的位置（排除被转义的 0xEE 0x0C）
     * 利用状态机跟踪转义前缀
     */
    private static int findFrameEnd(byte[] data, int startPos) {
        boolean inEscape = false;
        for (int i = startPos; i < data.length; i++) {
            byte b = data[i];
            if (inEscape) {
                inEscape = false;
                continue;
            }
            if (b == ESCAPE_PREFIX) {
                inEscape = true;
                continue;
            }
            if (b == FRAME_END) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 反转义处理
     * 0xEE 0x0A → 0xAA
     * 0xEE 0x0C → 0xCC
     * 0xEE 0x0E → 0xEE
     */
    private static byte[] unescape(byte[] escaped) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(escaped.length);
        for (int i = 0; i < escaped.length; i++) {
            if (escaped[i] == ESCAPE_PREFIX && i + 1 < escaped.length) {
                byte next = escaped[i + 1];
                if (next == 0x0A) {
                    out.write(0xAA);
                    i++;
                } else if (next == 0x0C) {
                    out.write(0xCC);
                    i++;
                } else if (next == 0x0E) {
                    out.write(0xEE);
                    i++;
                } else {
                    out.write(escaped[i] & 0xFF);
                }
            } else {
                out.write(escaped[i] & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /**
     * CRC-16 计算（MODBUS 多项式 0xA001）
     * 诺瓦协议使用 16 位 CRC 校验，按低位在前高位在后存储
     */
    private static int calculateCrc16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Nova 协议帧结构
     */
    @Data
    public static class NovaFrame {
        /** 设备地址 (0-65535, 小端序) */
        private final int deviceAddress;
        /** 指令码 (1-255) */
        private final int commandCode;
        /** 数据域（反转义后的纯数据） */
        private final byte[] dataField;

        public NovaFrame(int deviceAddress, int commandCode, byte[] dataField) {
            this.deviceAddress = deviceAddress;
            this.commandCode = commandCode;
            this.dataField = dataField;
        }

        /** 指令码十六进制字符串 */
        public String getCommandHex() {
            return String.format("0x%02X", commandCode);
        }

        /** 设备地址十六进制字符串 */
        public String getDeviceAddressHex() {
            return String.format("0x%04X", deviceAddress);
        }
    }
}
