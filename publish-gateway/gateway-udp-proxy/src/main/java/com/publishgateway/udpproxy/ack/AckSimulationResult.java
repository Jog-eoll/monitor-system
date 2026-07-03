package com.publishgateway.udpproxy.ack;

import lombok.Data;

/**
 * Result of comparing a simulated ACK with a real ACK.
 */
@Data
public class AckSimulationResult {

    private boolean compared;

    private boolean matched;

    private byte[] simulatedAck;

    private String reason;

    private Integer firstDiffOffset;

    private String realByteAtDiff;

    private String simulatedByteAtDiff;

    public static AckSimulationResult unsupported(String reason) {
        AckSimulationResult result = new AckSimulationResult();
        result.setCompared(false);
        result.setMatched(false);
        result.setReason(reason);
        return result;
    }
}
