package com.infopublish.client.entity.dto;

import lombok.Data;

@Data
public class ContentAuditRelayPacket {

    private String sourceIp;
    private int sourcePort;
    private String targetIp;
    private int targetPort;
    private long pid;
    private String processName;
    private byte[] payload;
    private byte[] relayBytes;
    private long timestamp;
    private String contentTokenId;
    private String contentFileId;

    public ContentAuditRelayPacket copy() {
        ContentAuditRelayPacket copied = new ContentAuditRelayPacket();
        copied.setSourceIp(sourceIp);
        copied.setSourcePort(sourcePort);
        copied.setTargetIp(targetIp);
        copied.setTargetPort(targetPort);
        copied.setPid(pid);
        copied.setProcessName(processName);
        copied.setPayload(payload == null ? null : payload.clone());
        copied.setRelayBytes(relayBytes == null ? null : relayBytes.clone());
        copied.setTimestamp(timestamp);
        copied.setContentTokenId(contentTokenId);
        copied.setContentFileId(contentFileId);
        return copied;
    }
}
