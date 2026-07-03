package com.publishgateway.udpproxy.service;

import lombok.Data;

@Data
public class FileSignatureCheckResult {

    private boolean enabled;
    private boolean signed;
    private boolean verified;
    private boolean allowed;
    private boolean auditOnly;
    private String mode;
    private String algorithm;
    private String hashAlgorithm;
    private String fileHash;
    private String filePath;
    private String errorMessage;
    private String signatureSource;
    private String clientId;
    private String clientCertId;
    private String fileId;
    private boolean clientRecordMatched;
    private long completedAt;
    private int signedEnvelopeLength;
}
