package com.publishgateway.udpproxy.ack;

import lombok.Data;

/**
 * Pending request summary for ACK proxy diagnostics.
 */
@Data
public class AckProxyPendingRequest {

    private String sampleId;

    private String ingressType;

    private String ruleId;

    private Long chainId;

    private String sourceIp;

    private Integer sourcePort;

    private String targetIp;

    private Integer targetPort;

    private Long requestTime;

    private Long ageMs;

    private Integer requestLength;

    private String requestFrameType;

    private Integer packetSerial;

    private String mainCmd;

    private String subCmd;

    private Boolean proxyAckSent;

    private String requestHex;

    private String parseError;

    private String correlationKey;
}
