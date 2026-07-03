package com.infopublish.client.entity.dto;

import lombok.Data;

@Data
public class ContentReleaseTokenMetadata {

    private Integer version;
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
}
