package com.infopublish.client.entity.dto;

import lombok.Data;

@Data
public class SecurePublishItem {

    private String packageName;
    private String payloadName;
    private String status;
    private String reason;
    private String packagePath;
    private String runtimePath;
    private String fileHash;
    private String scanResult;
    private String riskLevel;
    private String auditMessage;
    private String auditReason;
    private long fileSize;
    private long createdAt;
    private long updatedAt;
}
