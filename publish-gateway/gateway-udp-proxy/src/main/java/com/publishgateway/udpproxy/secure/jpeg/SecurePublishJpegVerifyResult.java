package com.publishgateway.udpproxy.secure.jpeg;

import lombok.Data;

/**
 * Result of SecurePublish JPEG APP11/COM signature verification.
 */
@Data
public class SecurePublishJpegVerifyResult {

    private boolean enabled;

    private String mode;

    private boolean blockPresent;

    private boolean signed;

    private boolean verified;

    private boolean allowed;

    private boolean auditOnly;

    private String reason;

    private String fileName;

    private String fileHash;

    private String payloadHash;

    private String manifestJson;

    private Integer manifestVersion;

    private String contentDecision;

    private String publisher;

    private String keyId;

    private Long signTime;

    private Long expireTime;

    private String signatureAlgorithm;

    private Long verifyTime;

    public static SecurePublishJpegVerifyResult disabled() {
        SecurePublishJpegVerifyResult result = new SecurePublishJpegVerifyResult();
        result.setEnabled(false);
        result.setAllowed(true);
        result.setReason("DISABLED");
        result.setVerifyTime(System.currentTimeMillis());
        return result;
    }
}
