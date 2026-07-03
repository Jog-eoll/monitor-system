package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.message.MessageHeader;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Caches recent plaintext/ciphertext pairs for encryption-gateway analysis.
 */
@Service
public class CryptoPacketStore {

    private static final int MAX_SIZE = 3000;

    private final Deque<CryptoPacketRecord> store = new ConcurrentLinkedDeque<>();

    public void saveMessage(String ruleId, Long chainId, String protocol,
                            String sourceIp, String targetIp, Integer targetPort,
                            MessageHeader header, byte[] plainBytes, byte[] cipherBytes) {
        save(ruleId, chainId, protocol, sourceIp, targetIp, targetPort,
                "MESSAGE_BYTES", header, plainBytes, cipherBytes);
    }

    public void saveRawTcp(String ruleId, Long chainId, String sourceIp,
                           String targetIp, Integer targetPort,
                           byte[] plainBytes, byte[] cipherBytes) {
        save(ruleId, chainId, "CATCH_ALL_TCP", sourceIp, targetIp, targetPort,
                "RAW_TCP_BYTES", null, plainBytes, cipherBytes);
    }

    public void saveRawUdp(String ruleId, Long chainId, String sourceIp,
                           String targetIp, Integer targetPort,
                           byte[] plainBytes, byte[] cipherBytes) {
        save(ruleId, chainId, "UDP", sourceIp, targetIp, targetPort,
                "RAW_UDP_BYTES", null, plainBytes, cipherBytes);
    }

    private void save(String ruleId, Long chainId, String protocol,
                      String sourceIp, String targetIp, Integer targetPort,
                      String plainDataType, MessageHeader header,
                      byte[] plainBytes, byte[] cipherBytes) {
        if (plainBytes == null || plainBytes.length == 0 || cipherBytes == null || cipherBytes.length == 0) {
            return;
        }
        while (store.size() >= MAX_SIZE) {
            store.pollFirst();
        }

        CryptoPacketRecord record = new CryptoPacketRecord();
        record.setRuleId(ruleId);
        record.setChainId(chainId);
        record.setProtocol(protocol);
        record.setSourceIp(sourceIp);
        record.setTargetIp(targetIp);
        record.setTargetPort(targetPort);
        record.setTimestamp(System.currentTimeMillis());
        record.setPlainDataType(plainDataType);
        record.setCipherDataType("SDK_CIPHER_BYTES");
        record.setPlainSize(plainBytes.length);
        record.setCipherSize(cipherBytes.length);
        record.setPlainBase64(Base64.getEncoder().encodeToString(plainBytes));
        record.setCipherBase64(Base64.getEncoder().encodeToString(cipherBytes));

        if (header != null) {
            record.setMessageId(header.getMessageId());
            record.setFragmentIndex((int) header.getFragmentIndex());
            record.setFragmentTotal((int) header.getFragmentTotal());
            record.setBodyLength(header.getBodyLength());
            record.setTotalLength(header.getTotalLength());
            record.setMsgType(String.format("0x%02X", header.getMsgType()));
            record.setEncoding(String.format("0x%02X", header.getEncoding()));
        }

        store.addLast(record);
    }

    public List<CryptoPacketRecord> getLatest(int limit) {
        List<CryptoPacketRecord> all = new ArrayList<>(store);
        List<CryptoPacketRecord> reversed = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            reversed.add(all.get(i));
            if (limit > 0 && reversed.size() >= limit) {
                break;
            }
        }
        return reversed;
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    @Data
    public static class CryptoPacketRecord {
        private String ruleId;
        private Long chainId;
        private String protocol;
        private String sourceIp;
        private String targetIp;
        private Integer targetPort;
        private long timestamp;

        private Integer messageId;
        private Integer fragmentIndex;
        private Integer fragmentTotal;
        private Integer bodyLength;
        private Integer totalLength;
        private String msgType;
        private String encoding;

        private String plainDataType;
        private String cipherDataType;
        private int plainSize;
        private int cipherSize;
        private String plainBase64;
        private String cipherBase64;
    }
}
