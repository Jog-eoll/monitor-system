package com.infopublish.client.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表摘要工具
 * <p>
 * 按规范字段生成 playlistDigest（SHA-256 hex），用于 publishPermit 和加密网关校验。
 * </p>
 *
 * <p>摘要输入字段：
 * <pre>
 *   playlistId
 *   + target(deviceId, ip, port)
 *   + items(orderNo, fileName, fileType, fileUrl, durationSeconds, fileHash)
 * </pre>
 * 排序后 JSON 化 → SHA-256 → hex 字符串。
 * </p>
 */
public final class PlaylistDigestUtil {

    private PlaylistDigestUtil() {}

    /**
     * 生成播放列表摘要
     *
     * @param playlistId 播放列表 ID
     * @param deviceId   目标设备 ID（可为 null）
     * @param ip         目标设备 IP（可为 null）
     * @param port       目标设备端口（可为 null）
     * @param items      播放列表项
     * @return SHA-256 hex 字符串（64 字符）
     */
    public static String digest(String playlistId,
                                String deviceId, String ip, Integer port,
                                List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("playlistId=").append(nullSafe(playlistId));
        sb.append("|deviceId=").append(nullSafe(deviceId));
        sb.append("|ip=").append(nullSafe(ip));
        sb.append("|port=").append(port != null ? port : "");
        sb.append("|items=[");

        if (items != null && !items.isEmpty()) {
            // 按 orderNo 排序
            List<Item> sorted = new ArrayList<>(items);
            sorted.sort((a, b) -> {
                int oa = a.orderNo != null ? a.orderNo : 0;
                int ob = b.orderNo != null ? b.orderNo : 0;
                return Integer.compare(oa, ob);
            });

            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) sb.append(",");
                Item it = sorted.get(i);
                sb.append("{");
                sb.append("orderNo=").append(it.orderNo != null ? it.orderNo : "");
                sb.append(",fileName=").append(nullSafe(it.fileName));
                sb.append(",fileType=").append(nullSafe(it.fileType));
                sb.append(",fileUrl=").append(nullSafe(it.fileUrl));
                sb.append(",durationSeconds=").append(it.durationSeconds != null ? it.durationSeconds : "");
                sb.append(",fileHash=").append(nullSafe(it.fileHash));
                sb.append("}");
            }
        }

        sb.append("]");
        return sha256Hex(sb.toString());
    }

    /**
     * 播放列表项
     */
    public static class Item {
        public Integer orderNo;
        public String fileName;
        public String fileType;
        public String fileUrl;
        public Integer durationSeconds;
        public String fileHash;
    }

    // ── 内部方法 ──

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
