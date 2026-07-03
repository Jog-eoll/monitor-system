package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.protocol.strategy.context.ReportPayload;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Caches parsed full-image payloads for analysis before MinIO upload.
 */
@Service
public class ImagePayloadStore {

    private static final int MAX_SIZE = 100;

    private final Deque<ImagePayloadRecord> store = new ConcurrentLinkedDeque<>();

    public void save(ReportPayload payload) {
        if (payload == null || !"image".equals(payload.getContentType())
                || isBlank(payload.getImageAnalysisBase64())) {
            return;
        }
        while (store.size() >= MAX_SIZE) {
            store.pollFirst();
        }

        ImagePayloadRecord record = new ImagePayloadRecord();
        record.setBusinessId(payload.getBusinessId());
        record.setGatewayId(payload.getGatewayId());
        record.setDeviceId(payload.getDeviceId());
        record.setDeviceName(payload.getDeviceName());
        record.setChainId(payload.getChainId());
        record.setSourceIp(payload.getSourceIp());
        record.setCaptureTime(payload.getCaptureTime());
        record.setTimestamp(payload.getTimestamp());
        record.setBoardIp(payload.getBoardIp());
        record.setBoardPort(payload.getBoardPort());
        record.setRawPacket(payload.getRawPacket());
        record.setProtocol(payload.getProtocol());
        record.setContentType(payload.getContentType());
        record.setMinioPath(payload.getMinioPath());
        record.setScreenshotBase64(payload.getImageAnalysisBase64());
        record.setImageFormat(payload.getImageFormat());
        record.setFileName(payload.getFileName());
        record.setFilePath(payload.getFilePath());
        record.setDescription(payload.getDescription());
        record.setTotalPackets(payload.getTotalPackets());
        record.setTotalSize(payload.getTotalSize());
        record.setImageSize(estimateBase64DecodedSize(payload.getImageAnalysisBase64()));
        store.addLast(record);
    }

    public List<ImagePayloadRecord> getLatest(int limit) {
        List<ImagePayloadRecord> all = new ArrayList<>(store);
        List<ImagePayloadRecord> reversed = new ArrayList<>();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int estimateBase64DecodedSize(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return 0;
        }
        int padding = 0;
        int length = base64.length();
        if (length > 0 && base64.charAt(length - 1) == '=') {
            padding++;
        }
        if (length > 1 && base64.charAt(length - 2) == '=') {
            padding++;
        }
        return (length * 3 / 4) - padding;
    }

    @Data
    public static class ImagePayloadRecord {
        private String businessId;
        private String gatewayId;
        private String deviceId;
        private String deviceName;
        private Long chainId;
        private String sourceIp;
        private String captureTime;
        private Long timestamp;
        private String boardIp;
        private Integer boardPort;
        private String rawPacket;
        private String protocol;
        private String contentType;
        private String minioPath;
        private String screenshotBase64;
        private String imageFormat;
        private String fileName;
        private String filePath;
        private String description;
        private Integer totalPackets;
        private Integer totalSize;
        private int imageSize;
    }
}
