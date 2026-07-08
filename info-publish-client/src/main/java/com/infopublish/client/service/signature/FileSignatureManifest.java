package com.infopublish.client.service.signature;

import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Canonical file-level signature manifest.
 */
@Data
public class FileSignatureManifest {

    private Integer version = 1;
    private String algorithm = "SM2-SIGN";
    private String hashAlgorithm = "SHA-256";
    private String ruleId;
    private Long chainId;
    private String sourceIp;
    private String targetIp;
    private Integer targetPort;
    private String fileName;
    private String filePath;
    private Integer fileSize;
    private Integer totalPackets;
    private String fileHash;
    private Long completedAt;

    public byte[] toCanonicalBytes() {
        return toCanonicalJson().getBytes(StandardCharsets.UTF_8);
    }

    public String toCanonicalJson() {
        StringBuilder sb = new StringBuilder(384);
        sb.append('{');
        appendNumber(sb, "version", version);
        appendString(sb, "algorithm", algorithm);
        appendString(sb, "hashAlgorithm", hashAlgorithm);
        appendString(sb, "ruleId", ruleId);
        appendNumber(sb, "chainId", chainId);
        appendString(sb, "sourceIp", sourceIp);
        appendString(sb, "targetIp", targetIp);
        appendNumber(sb, "targetPort", targetPort);
        appendString(sb, "fileName", fileName);
        appendString(sb, "filePath", filePath);
        appendNumber(sb, "fileSize", fileSize);
        appendNumber(sb, "totalPackets", totalPackets);
        appendString(sb, "fileHash", fileHash);
        appendNumber(sb, "completedAt", completedAt);
        sb.setLength(sb.length() - 1);
        sb.append('}');
        return sb.toString();
    }

    public static String sha256Hex(byte[] data) {
        if (data == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        char[] out = new char[data.length * 2];
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return new String(out);
    }

    private static void appendString(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":\"")
                .append(escape(value == null ? "" : value))
                .append("\",");
    }

    private static void appendNumber(StringBuilder sb, String name, Number value) {
        sb.append('"').append(name).append("\":");
        sb.append(value == null ? "null" : value.toString());
        sb.append(',');
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
