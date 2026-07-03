package com.publishgateway.udpproxy.ack;

import lombok.Data;

/**
 * Decision for strong isolation after a request is received.
 */
@Data
public class AckIsolationDecision {

    private boolean hold;

    private boolean proxyAckSent;

    private byte[] proxyAck;

    private String reason;

    public static AckIsolationDecision pass(String reason) {
        AckIsolationDecision decision = new AckIsolationDecision();
        decision.setHold(false);
        decision.setReason(reason);
        return decision;
    }

    public static AckIsolationDecision hold(byte[] proxyAck, boolean proxyAckSent, String reason) {
        AckIsolationDecision decision = new AckIsolationDecision();
        decision.setHold(true);
        decision.setProxyAck(proxyAck);
        decision.setProxyAckSent(proxyAckSent);
        decision.setReason(reason);
        return decision;
    }
}
