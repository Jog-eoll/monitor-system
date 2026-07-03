package com.publishgateway.udpproxy.service;

import lombok.Data;

@Data
public class ContentReleaseTokenIssueRequest {

    private String clientId;
    private String ruleId;
    private Long chainId;
    private String sourceIp;
    private Integer sourcePort;
    private String targetIp;
    private Integer targetPort;
    private String fileName;
    private String filePath;
    private String fileHash;
    private Long fileSize;
    private String contentType;
    private String scanResult;
    private String riskLevel;
    private String policyVersion;
    private Boolean allowForward;
    private Boolean allowDisplay;
    private Long tokenTtlMs;
}
