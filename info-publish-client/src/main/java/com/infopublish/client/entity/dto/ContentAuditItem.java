package com.infopublish.client.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ContentAuditItem {

    private String auditId;
    private String auditKey;
    private String status;
    private String decision;
    private String reason;
    private String contentType;
    private String fileName;
    private String filePath;
    private String fileHash;
    private long fileSize;
    private int totalPackets;
    private long pid;
    private String processName;
    private String sourceIp;
    private int sourcePort;
    private String targetIp;
    private int targetPort;
    private long createdAt;
    private long updatedAt;
    private Long completedAt;
    private Long handledAt;
    private String handledBy;
    private Integer auditCode;
    private String auditMessage;
    private String remoteDecision;
    private String remoteReason;
    private String contentTokenId;
    private String contentFileId;
    private Long tokenExpireAt;
    private String tokenError;
    private String riskLevel;
    private boolean forwarded;
    private boolean passthroughForwarded;

    @JsonIgnore
    private byte[] fullContentBytes;

    @JsonIgnore
    private final List<ContentAuditRelayPacket> bufferedPackets = new ArrayList<>();
}
