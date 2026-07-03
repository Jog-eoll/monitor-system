package com.publishgateway.udpproxy.service;

import lombok.Data;

import java.nio.charset.StandardCharsets;

/**
 * Canonical metadata signed as a content release token.
 */
@Data
public class ContentReleaseTokenMetadata {

    private Integer version = 1;
    private String tokenId;
    private String fileId;
    private String fileName;
    private String filePath;
    private String fileHash;
    private Long fileSize;
    private String contentType;
    private String scanResult;
    private String riskLevel;
    private Boolean allowForward;
    private Boolean allowDisplay;
    private String policyVersion;
    private String clientId;
    private String sourceIp;
    private Integer sourcePort;
    private String targetIp;
    private Integer targetPort;
    private String ruleId;
    private Long chainId;
    private Long issuedAt;
    private Long expireAt;
    private String issuer;

    public byte[] toCanonicalBytes() {
        return toCanonicalJson().getBytes(StandardCharsets.UTF_8);
    }

    public String toCanonicalJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        appendNumber(sb, "version", version);
        appendString(sb, "tokenId", tokenId);
        appendString(sb, "fileId", fileId);
        appendString(sb, "fileName", fileName);
        appendString(sb, "filePath", filePath);
        appendString(sb, "fileHash", fileHash);
        appendNumber(sb, "fileSize", fileSize);
        appendString(sb, "contentType", contentType);
        appendString(sb, "scanResult", scanResult);
        appendString(sb, "riskLevel", riskLevel);
        appendBoolean(sb, "allowForward", allowForward);
        appendBoolean(sb, "allowDisplay", allowDisplay);
        appendString(sb, "policyVersion", policyVersion);
        appendString(sb, "clientId", clientId);
        appendString(sb, "sourceIp", sourceIp);
        appendNumber(sb, "sourcePort", sourcePort);
        appendString(sb, "targetIp", targetIp);
        appendNumber(sb, "targetPort", targetPort);
        appendString(sb, "ruleId", ruleId);
        appendNumber(sb, "chainId", chainId);
        appendNumber(sb, "issuedAt", issuedAt);
        appendNumber(sb, "expireAt", expireAt);
        appendString(sb, "issuer", issuer);
        sb.setLength(sb.length() - 1);
        sb.append('}');
        return sb.toString();
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

    private static void appendBoolean(StringBuilder sb, String name, Boolean value) {
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
