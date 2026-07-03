package com.publishgateway.udpproxy.ack;

import lombok.Data;

/**
 * One learned request/ACK pair captured by publish-gateway.
 */
@Data
public class AckProxySample {

    private String sampleId;

    private String ingressType;

    private String ruleId;

    private Long chainId;

    private String sourceIp;

    private Integer sourcePort;

    private String targetIp;

    private Integer targetPort;

    private String responseSourceIp;

    private Integer responseSourcePort;

    private String requestHex;

    private String responseHex;

    private Integer requestLength;

    private Integer responseLength;

    private Long requestTime;

    private Long responseTime;

    private Long rttMs;

    private String requestFrameType;

    private String responseFrameType;

    private Integer packetSerial;

    private String mainCmd;

    private String subCmd;

    private Integer currentPart;

    private Boolean needResponse;

    private String checksumType;

    private Integer checksum;

    private Integer sourceAddress;

    private String destinationAddress;

    private Boolean matchedBySerial;

    private Boolean simulatedCompared;

    private Boolean simulatedMatched;

    private Boolean proxyAckSent;

    private Boolean realAckSuppressed;

    private String simulatedAckHex;

    private Integer simulatedAckLength;

    private String simulatedAckReason;

    private Integer firstDiffOffset;

    private String realByteAtDiff;

    private String simulatedByteAtDiff;

    private String correlationKey;

    private String parseError;
}
