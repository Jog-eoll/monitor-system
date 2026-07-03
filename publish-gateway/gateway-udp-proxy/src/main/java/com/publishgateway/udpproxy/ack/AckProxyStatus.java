package com.publishgateway.udpproxy.ack;

import lombok.Data;

/**
 * Runtime counters for ACK learning.
 */
@Data
public class AckProxyStatus {

    private boolean enabled;

    private String mode;

    private boolean learningEnabled;

    private boolean compareEnabled;

    private boolean proxyAckEnabled;

    private boolean isolationActive;

    private long requestsRecorded;

    private long responsesRecorded;

    private long samplesCompleted;

    private long responsesUnmatched;

    private long pendingEvicted;

    private long simulatedCompared;

    private long simulatedMatched;

    private long simulatedMismatched;

    private long simulatedUnsupported;

    private long proxyAcksSent;

    private long realAcksSuppressed;

    private long isolationHeldPackets;

    private long isolationReleasedPackets;

    private long isolationRejectedSessions;

    private int isolationSessions;

    private double simulatedMatchRate;

    private int pendingRequests;

    private int sampleCount;

    private int maxSamples;

    private int maxPendingRequests;

    private long pendingTimeoutMs;
}
