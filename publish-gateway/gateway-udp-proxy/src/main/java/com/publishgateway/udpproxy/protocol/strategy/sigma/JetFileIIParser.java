package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * JetFileII协议解析（第一种通信格式）
 *
 * 协议格式:
 * <SOH(0x01)><Z><地址2字节><STX(0x02)><命令码><File Label 1-5字节><Data Field N字节><EOT(0x04)>
 *
 * 支持的命令码:
 *   'A' = TEXT FILE   — 文本文件(含Head头 + 显示控制符)
 *   'G' = STRING FILE — 字符串文件(纯文本, 多条用0x02分隔)
 *   'I' = PICTURE FILE
 *   'K' = ARRAY PICTURE FILE
 */
@Slf4j
public class JetFileIIParser {

    // 协议常量
    private static final byte SOH = 0x01;  // 消息开始
    private static final byte STX = 0x02;  // 数据开始
    private static final byte EOT = 0x04;  // 消息结束
    private static final byte REV = 'Z';   // 保留字节

    // 命令码常量
    private static final byte CMD_TEXT_FILE = 'A';
    private static final byte CMD_STRING_FILE = 'G';
    private static final byte CMD_PICTURE_FILE = 'I';
    private static final byte CMD_ARRAY_PICTURE_FILE = 'K';

    /** GBK字符集，LED屏中文编码 */
    private static final Charset GBK = Charset.forName("GBK");

    /**
     * 解析JetFileII数据包
     * 
     * @param data 原始UDP数据
     * @return 解析结果,如果不是有效的JetFileII数据包则返回null
     */
    public static JetFileIIMessage parse(byte[] data) {
        if (data == null || data.length < 7) {
            return null;
        }

        try {
            // 【调试】打印前20个字节的十六进制
            int debugLen = Math.min(20, data.length);
            StringBuilder hexDebug = new StringBuilder();
            for (int i = 0; i < debugLen; i++) {
                hexDebug.append(String.format("%02X ", data[i]));
            }
            log.info("【协议调试】收到数据包前{}字节: {} (总长度:{})", debugLen, hexDebug.toString(), data.length);
            
            // 1. 验证消息开始标识
            if (data[0] != SOH) {
                log.debug("非JetFileII协议: 缺少SOH标识,首字节=0x{}", String.format("%02X", data[0]));
                return null;
            }

            // 2. 验证保留字节
            if (data[1] != REV) {
                log.debug("非JetFileII协议: 保留字节不是'Z'");
                return null;
            }

            // 3. 解析地址 (2字节ASCII)
            String address = new String(data, 2, 2, StandardCharsets.US_ASCII);

            // 4. 验证STX标识
            if (data[4] != STX) {
                log.debug("非JetFileII协议: 缺少STX标识");
                return null;
            }

            // 5. 解析命令码
            byte commandCode = data[5];
            String commandType = parseCommandType(commandCode);
            if (commandType == null) {
                log.debug("非JetFileII协议: 未知命令码 0x{}", Integer.toHexString(commandCode & 0xFF));
                return null;
            }

            // 6. 查找EOT标识
            int eotIndex = findEOT(data);
            if (eotIndex == -1) {
                log.warn("JetFileII协议: 未找到EOT结束标识");
                return null;
            }

            // 7. 解析File Label（文件名）
            // 协议: File Label 1-5字节, 位于命令码之后(偏移6)
            FileLabelResult labelResult = parseFileLabel(data, 6, eotIndex);
            String fileName = labelResult.fileName;
            int dataStartIndex = labelResult.dataStartIndex;

            // 8. 提取实际数据内容
            byte[] contentData;
            int dataLength = eotIndex - dataStartIndex;
            if (dataLength > 0) {
                contentData = new byte[dataLength];
                System.arraycopy(data, dataStartIndex, contentData, 0, dataLength);
            } else {
                contentData = new byte[0];
            }

            // 9. 构建解析结果
            JetFileIIMessage message = new JetFileIIMessage();
            message.setAddress(address);
            message.setCommandCode(commandCode);
            message.setCommandType(commandType);
            message.setFileName(fileName);
            message.setContentData(contentData);
            message.setRawData(data);

            // 10. 根据命令类型解析内容
            parseContent(message);

            log.info("【协议解析】JetFileII数据包解析成功: 地址={}, 命令={}, 文件名={}, 内容长度={}",
                    address, commandType, fileName, contentData.length);

            return message;

        } catch (Exception e) {
            log.error("【协议解析】JetFileII解析失败", e);
            return null;
        }
    }

    /**
     * 解析命令类型
     */
    private static String parseCommandType(byte commandCode) {
        switch (commandCode) {
            case CMD_TEXT_FILE:
                return "TEXT_FILE";
            case CMD_STRING_FILE:
                return "STRING_FILE";
            case CMD_PICTURE_FILE:
                return "PICTURE_FILE";
            case CMD_ARRAY_PICTURE_FILE:
                return "ARRAY_PICTURE_FILE";
            default:
                return null;
        }
    }

    /**
     * 查找EOT位置（从数据末尾往前搜索，更准确）
     * 注意: 0x04 也可能出现在 Text File 内部作为 EOF，
     * 但协议层 EOT 一定是最后一个 0x04
     */
    private static int findEOT(byte[] data) {
        for (int i = data.length - 1; i >= 6; i--) {
            if (data[i] == EOT || data[i] == 0x03) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析File Label（文件名/路径）
     *
     * 协议规定:
     * - 第一字节非0x0F: 1字节文件名（写到默认盘）
     * - 第一字节为0x0F: 路径格式 0x0F + 盘符(1) + 目录(1) + 文件名(2) = 共5字节
     */
    private static FileLabelResult parseFileLabel(byte[] data, int start, int eotIndex) {
        if (start >= eotIndex) {
            return new FileLabelResult(null, start);
        }

        if (data[start] == 0x0F) {
            // 特殊路径格式: 0x0F<盘符><目录><文件名2字节>
            if (start + 5 <= eotIndex) {
                char disk = (char) data[start + 1];
                char dir = (char) data[start + 2];
                String name = new String(data, start + 3, 2, StandardCharsets.US_ASCII).trim();
                String fullPath = disk + ":\\" + dir + "\\" + name;
                return new FileLabelResult(fullPath, start + 5);
            }
            return new FileLabelResult(null, start + 1);
        } else {
            // 普通格式: 1字节文件名
            String fileName = String.valueOf((char) data[start]);
            return new FileLabelResult(fileName, start + 1);
        }
    }

    /**
     * 文件名解析结果
     */
    @AllArgsConstructor
    private static class FileLabelResult {
        final String fileName;
        final int dataStartIndex;
    }

    /**
     * 根据命令类型解析内容
     */
    private static void parseContent(JetFileIIMessage message) {
        try {
            byte[] contentData = message.getContentData();
            if (contentData == null || contentData.length == 0) {
                return;
            }

            switch (message.getCommandType()) {
                case "TEXT_FILE":
                    // TEXT FILE: 剥离Head(7字节) + 过滤显示控制符 + GBK解码
                    String textContent = parseTextFileData(contentData, 0, contentData.length);
                    message.setTextContent(textContent);
                    log.info("【TEXT FILE解析】解码文本: '{}'", textContent);
                    break;

                case "STRING_FILE":
                    // STRING FILE: 处理0x02分隔符 + GBK解码
                    String stringContent = parseStringFileData(contentData, 0, contentData.length);
                    message.setTextContent(stringContent);
                    log.info("【STRING FILE解析】解码文本: '{}'", stringContent);
                    break;

                case "PICTURE_FILE":
                case "ARRAY_PICTURE_FILE":
                    // 图片数据,保持二进制
                    message.setBinaryContent(contentData);
                    break;

                default:
                    // 未知类型尝试GBK文本解码
                    message.setTextContent(new String(contentData, GBK));
            }
        } catch (Exception e) {
            log.warn("【协议解析】内容解析失败: {}", e.getMessage());
            // 降级为二进制内容
            message.setBinaryContent(message.getContentData());
        }
    }

    // ======================== TEXT FILE 解析 ========================

    /**
     * 解析 Text File 数据
     *
     * Text File 格式 (Table4.1.1):
     *   [Head 7字节] [Data N字节] [EOF 0x04]
     *   Head = <0x51><Z><0x53><地址2字节>AX (固定7字节, 以 0x51 0x5A 0x53 开头)
     *   Data = 显示内容 + 控制符
     */
    private static String parseTextFileData(byte[] data, int start, int end) {
        int pos = start;

        // 跳过 Head (7字节): 检测 0x51 0x5A 0x53 魔数
        if (pos + 7 <= end
                && data[pos] == 0x51
                && data[pos + 1] == 0x5A
                && data[pos + 2] == 0x53) {
            log.debug("【TEXT FILE】检测到文件头, 跳过7字节Head");
            pos += 7;
        }

        // 过滤控制符，收集纯文本字节，用GBK解码
        return extractPureText(data, pos, end);
    }

    // ======================== STRING FILE 解析 ========================

    /**
     * 解析 String File 数据
     *
     * String File 无文件头，直接是文本内容
     * 多条字符串之间用 0x02 分隔
     */
    private static String parseStringFileData(byte[] data, int start, int end) {
        ByteArrayOutputStream textBytes = new ByteArrayOutputStream();
        for (int i = start; i < end; i++) {
            byte b = data[i];
            if (b == 0x04) break;               // EOT/EOF 结束
            if (b == 0x02) {                     // 多条分隔符 → 换行
                textBytes.write('\n');
                continue;
            }
            // 跳过其他不可见控制字符(0x01, 0x03, 0x05等)，保留0x0A(LF)和0x0D(CR)
            if (b >= 0x01 && b < 0x20 && b != 0x0A && b != 0x0D) {
                continue;
            }
            textBytes.write(b & 0xFF);
        }
        return new String(textBytes.toByteArray(), GBK);
    }

    // ======================== 显示控制符过滤 ========================

    /**
     * 从Text File的Data区域提取纯文本字节，过滤掉所有显示控制符和Sigma配置噪声
     *
     * 控制符定义 (Table4.1.2):
     *   0x01         — 1字节, 文件开始符
     *   0x04         — 1字节, 文件结束符(EOF)
     *   0x06 XX      — 2字节, 协议选择控制符
     *   0x07 XX      — 2字节, 闪烁控制符
     *   0x08 XX      — 2字节, 行间距控制符
     *   0x09 XX      — 2字节, 对齐控制符
     *   0x0A XX XX   — 3字节, 花样控制符
     *   0x0B XX ...  — 2字节, 特殊字符(日期/时间等)
     *
     * Sigma配置噪声过滤策略:
     *   Sigma .Nmg 文件中，标准控制符之前会有一段配置区(显示位置/字体/颜色等)，
     *   其中夹杂的可打印ASCII字节(主要是数字和符号)会被误当成文本。
     *   通过分析"文本段"(连续可打印字节的run)来区分:
     *   - 含字母/中文(GBK高字节)的段 → 真实文本
     *   - 纯数字/符号且较短的段 → 配置噪声
     */
    private static String extractPureText(byte[] data, int start, int end) {
        // 第一步: 按控制符切分,收集"文本段"(run)
        ArrayList<byte[]> runs = new ArrayList<>();
        ByteArrayOutputStream currentRun = new ByteArrayOutputStream();
        int i = start;

        while (i < end) {
            int b = data[i] & 0xFF;

            // EOF / EOT
            if (b == 0x04) break;

            // --- 控制符处理: 遇到控制符保存当前段,跳过控制符 ---
            int skip = getControlCharLength(b);
            if (skip > 0) {
                flushRun(currentRun, runs);
                i += skip;
                continue;
            }

            // 其他低位控制字符(0x02, 0x03, 0x05, 0x0C-0x1F): 跳过
            if (b < 0x20 && b != 0x0D) {
                flushRun(currentRun, runs);
                i++;
                continue;
            }

            // 0x0D(CR): 作为行分隔标记
            if (b == 0x0D) {
                flushRun(currentRun, runs);
                runs.add(new byte[]{0x0D}); // CR标记
                i++;
                continue;
            }

            // 普通可打印字节(包括GBK高位字节 0x80-0xFF)
            currentRun.write(b);
            i++;
        }
        flushRun(currentRun, runs);

        // 第二步: 过滤噪声段,保留有意义的文本段
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        boolean afterCR = false;

        for (byte[] run : runs) {
            // CR标记: 在有文本的前提下输出换行
            if (run.length == 1 && run[0] == 0x0D) {
                if (result.size() > 0) {
                    result.write('\n');
                }
                afterCR = true;
                continue;
            }

            // 判断该段是否为有意义文本(还是Sigma配置噪声)
            if (isTextRun(run) || afterCR) {
                result.write(run, 0, run.length);
            }
            afterCR = false;
        }

        byte[] bytes = result.toByteArray();
        if (bytes.length == 0) {
            return "";
        }
        return new String(bytes, GBK);
    }

    /**
     * 获取标准控制符的长度(含控制符字节本身)
     * @return 控制符总长度, 0表示不是已知控制符
     */
    private static int getControlCharLength(int b) {
        if (b == 0x01) return 1;                   // 文件开始符
        if (b >= 0x06 && b <= 0x09) return 2;      // 2字节控制符
        if (b == 0x0A) return 3;                    // 3字节花样控制符
        if (b == 0x0B) return 2;                    // 2字节特殊字符
        return 0;
    }

    /** 将当前run保存到列表并重置 */
    private static void flushRun(ByteArrayOutputStream currentRun, ArrayList<byte[]> runs) {
        if (currentRun.size() > 0) {
            runs.add(currentRun.toByteArray());
            currentRun.reset();
        }
    }

    /**
     * 判断一个文本段(run)是否为有意义的显示文本
     *
     * Sigma配置区的噪声特征: 1-5字节的纯数字/符号串夹在控制字符之间。
     * 实际显示文本特征: 含字母(a-z/A-Z)或GBK中文高字节(0x81-0xFE), 或长度≥6。
     *
     * 规则:
     *   - 单字节段: 一定是噪声
     *   - 长度≥6: 即使全数字也保留(可能是电话号码等)
     *   - 长度2-5: 必须有超过一半的字节是字母或GBK高字节
     */
    private static boolean isTextRun(byte[] run) {
        if (run.length < 2) return false;
        if (run.length >= 6) return true;

        int textCharCount = 0;
        for (byte b : run) {
            int unsigned = b & 0xFF;
            // 英文字母
            if ((unsigned >= 0x41 && unsigned <= 0x5A) || (unsigned >= 0x61 && unsigned <= 0x7A)) {
                textCharCount++;
            }
            // GBK双字节字符的首字节
            if (unsigned >= 0x81 && unsigned <= 0xFE) {
                textCharCount++;
            }
        }
        return textCharCount > run.length / 2;
    }

    /**
     * JetFileII消息实体
     */
    @Data
    public static class JetFileIIMessage {
        /** 原始数据 */
        private byte[] rawData;

        /** 屏体地址 (00-99) */
        private String address;

        /** 命令码 */
        private byte commandCode;

        /** 命令类型 */
        private String commandType;

        /** 文件名 (可选) */
        private String fileName;

        /** 内容数据 */
        private byte[] contentData;

        /** 文本内容 (TEXT/STRING类型) */
        private String textContent;

        /** 二进制内容 (PICTURE类型) */
        private byte[] binaryContent;

        /**
         * 判断是否包含文本内容
         */
        public boolean hasTextContent() {
            return textContent != null && !textContent.isEmpty();
        }

        /**
         * 判断是否包含二进制内容
         */
        public boolean hasBinaryContent() {
            return binaryContent != null && binaryContent.length > 0;
        }

        /**
         * 获取可读的内容摘要
         */
        public String getContentSummary() {
            if (hasTextContent()) {
                // 文本内容,最多显示100个字符
                return textContent.length() > 100 
                    ? textContent.substring(0, 100) + "..." 
                    : textContent;
            } else if (hasBinaryContent()) {
                return String.format("[二进制数据: %d字节]", binaryContent.length);
            } else {
                return "[空内容]";
            }
        }
    }
}
