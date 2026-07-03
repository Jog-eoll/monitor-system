package com.publishgateway.udpproxy.service;

import lombok.Data;

/**
 * Client-side file signature metadata received from Windows client.
 */
@Data
public class ClientFileSignatureRecord {

    private String clientId;
    private String clientCertId;
    private String fileId;
    private String fileName;
    private String filePath;
    private Integer fileSize;
    private Integer totalPackets;
    private String fileHash;
    private String manifestJson;
    private String signedEnvelopeBase64;
    private String algorithm;
    private String hashAlgorithm;
    private String sourceIp;
    private Integer sourcePort;
    private String targetIp;
    private Integer targetPort;
    private Long completedAt;
    private Boolean verified;
    private String verifyError;
    private Long receivedAt;
}
