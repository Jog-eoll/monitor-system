package com.publishgateway.udpproxy.secure;

import lombok.Data;

@Data
public class SecurePublishVerifyResult {

    private boolean allowed;
    private String reason;
    private String packageName;
    private String payloadName;
    private byte[] payloadBytes;
    private String fileHash;
    private Long fileSize;
    private String keyId;
    private String scanResult;
    private String contentDecision;
    private String auditReason;
    private long verifyTime;

    public static SecurePublishVerifyResult allowed(SecurePublishManifest manifest, byte[] payloadBytes, String keyId) {
        SecurePublishVerifyResult result = new SecurePublishVerifyResult();
        result.setAllowed(true);
        result.setReason("SIGNATURE_AND_POLICY_VERIFIED");
        result.setPayloadName(manifest.getFileName());
        result.setPayloadBytes(payloadBytes);
        result.setFileHash(manifest.getSha256());
        result.setFileSize(manifest.getFileSize());
        result.setKeyId(keyId);
        result.setScanResult(manifest.getScanResult());
        result.setContentDecision(manifest.getContentDecision());
        result.setAuditReason(manifest.getAuditReason());
        result.setVerifyTime(System.currentTimeMillis());
        return result;
    }

    public static SecurePublishVerifyResult rejected(String reason) {
        SecurePublishVerifyResult result = new SecurePublishVerifyResult();
        result.setAllowed(false);
        result.setReason(reason == null ? "VERIFY_FAILED" : reason);
        result.setVerifyTime(System.currentTimeMillis());
        return result;
    }
}
