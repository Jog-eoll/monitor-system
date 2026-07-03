package com.publishgateway.udpproxy.entity.message;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息构建器
 *
 *
 *   1. 将原始数据（透传模式）或转码后数据（转码模式）封装为 Message 列表
 *   2. 数据超过分片阈值时自动切片，每片独立封装 Header
 *   3. 计算 TCP 所需的 totalLength 字段
 *
 * 不涉及任何 Header 解析逻辑，解析只属于终端网关。
 */
@Slf4j
public class MessageBuilder {

    /**
     * UDP 单包 Body 最大字节数（保留 20 字节 Header + IP/UDP 头，MTU 1500 字节下安全值）
     * 超过此阈值时自动分片
     */
    public static final int UDP_MAX_BODY_SIZE = 1400;

    /** 全局消息ID生成器，每次调用 build() 递增，保证同一批分片 messageId 唯一 */
    private static final AtomicInteger MESSAGE_ID_SEQ = new AtomicInteger(0);

    /**
     * 为 UDP 协议构建 Message 列表
     *
     * 透传模式（transcodeEnabled=false）：
     *   Body = 原始 Sigma 数据，msgType=PASSTHROUGH，encoding=NONE
     *   终端网关收到后直接转发 Body 给情报板，不做任何解码
     *
     * 转码模式（transcodeEnabled=true）：
     *   Body = 转码后的数据，msgType/encoding 由调用方指定
     *   终端网关收到后按 encoding 解码，重组后转发给情报板
     *
     * @param data             待封装的字节数据（透传时为原始数据，转码时为转码后数据）
     * @param transcodeEnabled 是否为转码模式
     * @param msgType          消息类型（透传模式固定传 MSG_TYPE_PASSTHROUGH）
     * @param encoding         编码方式（透传模式固定传 ENCODING_NONE）
     * @return 封装好的 Message 列表（UDP 分片时有多个，不分片时只有一个）
     */
    public static List<Message> buildForUdp(byte[] data,
                                            boolean transcodeEnabled,
                                            byte msgType,
                                            byte encoding) {
        if (!transcodeEnabled) {
            // 透传模式：强制使用透传类型和无编码，防止调用方误传
            msgType  = MessageHeader.MSG_TYPE_PASSTHROUGH;
            encoding = MessageHeader.ENCODING_NONE;
        }

        int messageId   = nextMessageId();
        List<Message> messages = new ArrayList<>();

        if (data.length <= UDP_MAX_BODY_SIZE) {
            // 数据量小，不需要分片
            messages.add(buildSingleUdp(data, msgType, encoding, messageId, (short) 0, (short) 1));
            log.debug("【MessageBuilder-UDP】单包封装 messageId={}, bodyLen={}, msgType=0x{}, encoding=0x{}",
                    messageId, data.length,
                    Integer.toHexString(msgType & 0xFF), Integer.toHexString(encoding & 0xFF));
        } else {
            // 数据量大，切片处理
            int totalFragments = (int) Math.ceil((double) data.length / UDP_MAX_BODY_SIZE);
            for (int i = 0; i < totalFragments; i++) {
                int start  = i * UDP_MAX_BODY_SIZE;
                int end    = Math.min(start + UDP_MAX_BODY_SIZE, data.length);
                int fragLen = end - start;

                byte[] fragBody = new byte[fragLen];
                System.arraycopy(data, start, fragBody, 0, fragLen);

                messages.add(buildSingleUdp(fragBody, msgType, encoding,
                        messageId, (short) i, (short) totalFragments));
            }
            log.debug("【MessageBuilder-UDP】分片封装 messageId={}, totalFragments={}, dataLen={}",
                    messageId, totalFragments, data.length);
        }

        return messages;
    }

    /**
     * 为 TCP 协议构建单个 Message
     *
     * TCP 是流式协议，不需要分片（由 TCP 协议栈自己处理大数据），
     * 但必须在 Header 中填写 totalLength 供终端网关拆帧使用。
     *
     * @param data             待封装的字节数据
     * @param transcodeEnabled 是否为转码模式
     * @param msgType          消息类型
     * @param encoding         编码方式
     * @return 封装好的单个 Message
     */
    public static Message buildForTcp(byte[] data,
                                      boolean transcodeEnabled,
                                      byte msgType,
                                      byte encoding) {
        if (!transcodeEnabled) {
            msgType  = MessageHeader.MSG_TYPE_PASSTHROUGH;
            encoding = MessageHeader.ENCODING_NONE;
        }

        int messageId   = nextMessageId();
        int totalLength = MessageHeader.HEADER_LENGTH + data.length;

        MessageHeader header = MessageHeader.builder()
                .magic(MessageHeader.MAGIC)
                .version(MessageHeader.VERSION)
                .msgType(msgType)
                .encoding(encoding)
                .messageId(messageId)
                .fragmentIndex((short) 0)   // TCP 固定为 0
                .fragmentTotal((short) 1)   // TCP 固定为 1
                .bodyLength(data.length)
                .totalLength(totalLength)   // TCP 拆帧的核心依据
                .build();

        log.debug("【MessageBuilder-TCP】封装 messageId={}, bodyLen={}, totalLen={}, msgType=0x{}, encoding=0x{}",
                messageId, data.length, totalLength,
                Integer.toHexString(msgType & 0xFF), Integer.toHexString(encoding & 0xFF));

        return new Message(header, data);
    }

    // ── 私有方法 ─────────────────────────────────────

    /**
     * 构建单个 UDP Message（内部使用）
     */
    private static Message buildSingleUdp(byte[] body, byte msgType, byte encoding,
                                          int messageId, short fragmentIndex, short fragmentTotal) {
        MessageHeader header = MessageHeader.builder()
                .magic(MessageHeader.MAGIC)
                .version(MessageHeader.VERSION)
                .msgType(msgType)
                .encoding(encoding)
                .messageId(messageId)
                .fragmentIndex(fragmentIndex)
                .fragmentTotal(fragmentTotal)
                .bodyLength(body.length)
                .totalLength(0)             // UDP 不需要 totalLength
                .build();

        return new Message(header, body);
    }

    /**
     * 获取下一个消息ID（全局递增，溢出后自动从0重新开始）
     */
    private static int nextMessageId() {
        return MESSAGE_ID_SEQ.getAndIncrement() & Integer.MAX_VALUE;
    }
}
