package com.publishgateway.udpproxy.secure;

import com.alibaba.fastjson2.JSON;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SecurePublishManifest {

    private Integer version;
    private String fileName;
    private Long fileSize;
    private String mediaType;
    private String sha256;
    private String publisher;
    private String scanResult;
    private String contentDecision;
    private String riskLevel;
    private String auditMessage;
    private String auditReason;
    private String auditRecordId;
    private String policyVersion;
    private String keyId;
    private Long signTime;
    private Long expireTime;
    private String algorithm;

    public byte[] toCanonicalBytes() {
        return toCanonicalJson().getBytes(StandardCharsets.UTF_8);
    }

    public String toCanonicalJson() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        if (isLegacyV2Shape()) {
            ordered.put("version", version);
            ordered.put("fileName", fileName);
            ordered.put("fileSize", fileSize);
            ordered.put("mediaType", mediaType);
            ordered.put("sha256", sha256);
            ordered.put("publisher", publisher);
            ordered.put("scanResult", scanResult);
            ordered.put("riskLevel", riskLevel);
            ordered.put("auditMessage", auditMessage);
            ordered.put("auditReason", auditReason);
            ordered.put("auditRecordId", auditRecordId);
            ordered.put("policyVersion", policyVersion);
            ordered.put("signTime", signTime);
            ordered.put("expireTime", expireTime);
            ordered.put("algorithm", algorithm);
            return JSON.toJSONString(ordered);
        }

        ordered.put("version", version);
        ordered.put("fileName", fileName);
        ordered.put("fileSize", fileSize);
        ordered.put("mediaType", mediaType);
        ordered.put("sha256", sha256);
        ordered.put("scanResult", scanResult);
        ordered.put("contentDecision", contentDecision);
        ordered.put("policyVersion", policyVersion);
        ordered.put("keyId", keyId);
        ordered.put("signTime", signTime);
        ordered.put("expireTime", expireTime);
        ordered.put("algorithm", algorithm);
        ordered.put("publisher", publisher);
        ordered.put("riskLevel", riskLevel);
        ordered.put("auditMessage", auditMessage);
        ordered.put("auditReason", auditReason);
        ordered.put("auditRecordId", auditRecordId);
        return JSON.toJSONString(ordered);
    }

    public static SecurePublishManifest fromJson(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), SecurePublishManifest.class);
    }

    private boolean isLegacyV2Shape() {
        return keyId == null && contentDecision == null
                && (version == null || Integer.valueOf(2).equals(version));
    }
}
