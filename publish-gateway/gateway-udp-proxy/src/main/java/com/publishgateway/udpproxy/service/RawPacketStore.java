package com.publishgateway.udpproxy.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 原始UDP包内存缓存服务
 *
 * 保存最近收到的原始UDP包（最多 MAX_SIZE 条），先进先出。
 * 重启后清空，不持久化。用于外部系统实时拉取原始包进行自定义处理。
 */
@Service
public class RawPacketStore {

    /** 最大缓存条数，超出后自动淘汰最旧的一条 */
    private static final int MAX_SIZE = 200;

    private final Deque<RawPacketRecord> store = new ConcurrentLinkedDeque<>();

    /**
     * 保存一条原始包记录
     *
     * @param ruleId   规则ID（标识来自哪条链路）
     * @param chainId  链路ID
     * @param sourceIp 来源IP（Sigma软件地址）
     * @param data     UDP原始字节
     */
    public void save(String ruleId, Long chainId, String sourceIp, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        // 超出上限时移除最旧的一条
        while (store.size() >= MAX_SIZE) {
            store.pollFirst();
        }
        RawPacketRecord record = new RawPacketRecord();
        record.setRuleId(ruleId);
        record.setChainId(chainId);
        record.setSourceIp(sourceIp);
        record.setTimestamp(System.currentTimeMillis());
        record.setPacketSize(data.length);
        record.setRawPacket(Base64.getEncoder().encodeToString(data));
        store.addLast(record);
    }

    /**
     * 取最近 limit 条原始包（从最新到最旧排列）
     *
     * @param limit 最多返回条数，-1 表示全部
     * @return 记录列表（最新在前）
     */
    public List<RawPacketRecord> getLatest(int limit) {
        List<RawPacketRecord> all = new ArrayList<>(store);
        // 倒序：最新的在前
        List<RawPacketRecord> reversed = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            reversed.add(all.get(i));
            if (limit > 0 && reversed.size() >= limit) {
                break;
            }
        }
        return reversed;
    }

    /**
     * 清空缓存
     */
    public void clear() {
        store.clear();
    }

    /**
     * 当前缓存条数
     */
    public int size() {
        return store.size();
    }

    // ========== 记录实体 ==========

    @Data
    public static class RawPacketRecord {
        /** 规则ID */
        private String ruleId;
        /** 链路ID */
        private Long chainId;
        /** 来源IP（Sigma软件地址） */
        private String sourceIp;
        /** 接收时间戳（毫秒） */
        private long timestamp;
        /** 原始包大小（字节） */
        private int packetSize;
        /**
         * 原始UDP包（Base64编码）
         * 解码方式：Base64.getDecoder().decode(rawPacket)
         */
        private String rawPacket;
    }
}
