package com.infopublish.client.entity.dto;

import lombok.Data;

/**
 * One outbound UDP event reported by a local ETW collector agent.
 */
@Data
public class EtwTrafficEventDTO {

    private String sourceIp;
    private Integer sourcePort;
    private String targetIp;
    private Integer targetPort;
    private Long processId;
    private Long packetCount;
    private Long byteCount;
    private Long timestampMillis;
    private String protocol;
    private String direction;
}
