package com.gateway.udpproxy.entity.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 消息重组器
 *
 * 职责：
 *   1. 解密后调用 process()，解析 Header，提取 Body
 *   2. 透传消息：直接返回 Body 给情报板，不做任何解码
 *   3. 分片消息：缓存各片，全部到齐后重组完整 Body 返回
 *   4. 转码消息：重组完成后按 encoding 字段解码，返回解码后的原始数据给情报板
 *   5. 超时清理：分片超过 10 秒未收齐，丢弃并释放内存
 *
 * 约束：
 *   - 情报板最终收到的是经过解码还原的"干净数据"，与原始数据语义等价
 *   - 本类是唯一解析 Header 的地方，发布网关侧不包含此类
 */
@Slf4j
@Component
public class MessageAssembler {

    /** 分片缓冲区超时时间（毫秒），超时未收齐则丢弃 */
    private static final long FRAGMENT_TIMEOUT_MS = 10_000;

    /**
     * 分片缓冲区：Key = messageId，Value = 该消息的所有分片上下文
     */
    private final Map<Integer, FragmentBuffer> fragmentBuffers = new ConcurrentHashMap<>();

    /** 超时清理定时器 */
    private final ScheduledExecutorService cleaner =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "msg-assembler-cleaner");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    public void startCleaner() {
        // 每 5 秒扫描一次，清理超时未收齐的分片缓冲区
        cleaner.scheduleAtFixedRate(this::evictExpired, 5, 5, TimeUnit.SECONDS);
        log.info("【MessageAssembler】分片超时清理任务已启动，超时阈值={}ms", FRAGMENT_TIMEOUT_MS);
    }

    /**
     * 处理一个解密后的原始字节数组，返回可直接转发给情报板的数据
     *
     * 调用时机：终端网关 UdpProxyServer/TcpProxyServer 解密完成后立即调用
     *
     * 返回值含义：
     *   - 非 null：可以转发给情报板的完整数据
     *   - null   ：分片尚未收齐，等待后续分片到达，本次不转发
     *
     * @param decryptedBytes 解密后的字节数组（= Header + Body）
     * @return 可转发给情报板的完整数据；分片未齐时返回 null
     */
    public byte[] process(byte[] decryptedBytes) {
        // ── Step 1: 解析 Message（含 Header 解析） ────────────────
        Message message = Message.fromBytes(decryptedBytes);
        if (message == null) {
            log.warn("【MessageAssembler】报文解析失败（魔数不匹配或长度不足），丢弃该包，长度={}",
                    decryptedBytes != null ? decryptedBytes.length : 0);
            return null;
        }

        MessageHeader header = message.getHeader();
        log.debug("【MessageAssembler】收到报文 {}", message);

        // ── Step 2: 判断是否需要重组 ──────────────────────────────
        byte[] completeBody;

        if (!message.isFragmented()) {
            // 单包（不分片），直接取 Body
            completeBody = message.getBody();
            log.debug("【MessageAssembler】单包消息，messageId={}, bodyLen={}",
                    header.getMessageId(), completeBody.length);
        } else {
            // 分片消息，加入缓冲区等待重组
            completeBody = assembleFragment(message);
            if (completeBody == null) {
                // 分片未齐，等待
                return null;
            }
        }

        // ── Step 3: 透传模式直接返回，不做任何解码 ────────────────
        if (message.isPassthrough()) {
            log.debug("【MessageAssembler】透传模式，直接转发给情报板，bodyLen={}", completeBody.length);
            return completeBody;
        }

        // ── Step 4: 转码模式，按 encoding 解码，还原情报板所需数据 ─
        return decode(completeBody, header.getEncoding(), header.getMessageId());
    }

    // ── 私有：分片重组 ────────────────────────────────────────────

    /**
     * 将分片加入缓冲区，收齐后按序号重组并返回完整 Body
     *
     * @return 重组完成的完整 Body；未收齐返回 null
     */
    private byte[] assembleFragment(Message message) {
        MessageHeader header = message.getHeader();
        int   messageId     = header.getMessageId();
        short fragmentIndex = header.getFragmentIndex();
        short fragmentTotal = header.getFragmentTotal();

        FragmentBuffer buffer = fragmentBuffers.computeIfAbsent(
                messageId, id -> new FragmentBuffer(fragmentTotal));

        buffer.put(fragmentIndex, message.getBody());

        log.debug("【MessageAssembler】收到分片 messageId={}, [{}/{}]",
                messageId, fragmentIndex + 1, fragmentTotal);

        if (!buffer.isComplete()) {
            return null;
        }

        // 所有分片到齐，按序号拼合
        fragmentBuffers.remove(messageId);
        byte[] assembled = buffer.assemble();
        log.info("【MessageAssembler】分片重组完成 messageId={}, totalBytes={}", messageId, assembled.length);
        return assembled;
    }

    // ── 私有：解码还原 ────────────────────────────────────────────

    /**
     * 按 encoding 字段解码 Body，返回情报板能直接识别的原始数据
     *
     * 转码模式下，发布网关对数据做了编码变换，终端网关在此做逆变换，
     * 保证情报板收到的数据与 Sigma 原始意图等价。
     *
     * TODO: 当转码SDK集成后，在对应 case 中实现真正的解码逻辑
     */
    private byte[] decode(byte[] body, byte encoding, int messageId) {
        switch (encoding) {
            case MessageHeader.ENCODING_NONE:
                // 无编码，直接透传（转码模式但未指定编码，原样返回）
                return body;

            case MessageHeader.ENCODING_UTF8:
                // TODO: UTF-8 文本解码 → 情报板所需编码（如 GBK）
                // 示例：return new String(body, StandardCharsets.UTF_8).getBytes("GBK");
                log.debug("【MessageAssembler】UTF-8文本解码（当前透传，待SDK集成） messageId={}", messageId);
                return body;

            case MessageHeader.ENCODING_JPEG:
                // TODO: JPEG 图片解码 → 情报板所需格式
                log.debug("【MessageAssembler】JPEG图片解码（当前透传，待SDK集成） messageId={}", messageId);
                return body;

            case MessageHeader.ENCODING_PNG:
                // TODO: PNG 图片解码 → 情报板所需格式
                log.debug("【MessageAssembler】PNG图片解码（当前透传，待SDK集成） messageId={}", messageId);
                return body;

            default:
                log.warn("【MessageAssembler】未知编码方式 0x{}，原样透传 messageId={}",
                        Integer.toHexString(encoding & 0xFF), messageId);
                return body;
        }
    }

    // ── 私有：超时清理 ────────────────────────────────────────────

    private void evictExpired() {
        long now = System.currentTimeMillis();
        fragmentBuffers.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().getCreateTimeMs()) > FRAGMENT_TIMEOUT_MS;
            if (expired) {
                log.warn("【MessageAssembler】分片缓冲区超时，丢弃 messageId={}, 已收={}/{}",
                        entry.getKey(),
                        entry.getValue().receivedCount(),
                        entry.getValue().getTotalFragments());
            }
            return expired;
        });
    }

    // ── 内部类：分片缓冲区 ────────────────────────────────────────

    /**
     * 单条消息的分片缓冲区
     */
    private static class FragmentBuffer {

        private final short    totalFragments;
        private final byte[][] fragments;
        private       int      receivedCount;
        private final long     createTimeMs;

        FragmentBuffer(short totalFragments) {
            this.totalFragments = totalFragments;
            this.fragments      = new byte[totalFragments][];
            this.receivedCount  = 0;
            this.createTimeMs   = System.currentTimeMillis();
        }

        void put(short index, byte[] body) {
            if (index >= 0 && index < totalFragments && fragments[index] == null) {
                fragments[index] = body;
                receivedCount++;
            }
        }

        boolean isComplete() {
            return receivedCount == totalFragments;
        }

        /**
         * 按序号拼合所有分片，返回完整 Body
         */
        byte[] assemble() {
            int totalLen = Arrays.stream(fragments)
                    .mapToInt(f -> f != null ? f.length : 0)
                    .sum();
            byte[] result = new byte[totalLen];
            int offset = 0;
            for (byte[] fragment : fragments) {
                if (fragment != null) {
                    System.arraycopy(fragment, 0, result, offset, fragment.length);
                    offset += fragment.length;
                }
            }
            return result;
        }

        int receivedCount()   { return receivedCount; }
        short getTotalFragments() { return totalFragments; }
        long getCreateTimeMs()    { return createTimeMs; }
    }
}
