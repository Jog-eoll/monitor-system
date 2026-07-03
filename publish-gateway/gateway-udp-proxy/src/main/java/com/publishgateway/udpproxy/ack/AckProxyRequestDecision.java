package com.publishgateway.udpproxy.ack;

import lombok.Data;

/**
 * Decision made when a Sigma request enters ACK proxy processing.
 */
@Data
public class AckProxyRequestDecision {

    private boolean proxyAckSent;

    private byte[] proxyAck;

    private String reason;

    public static AckProxyRequestDecision none(String reason) {
        AckProxyRequestDecision decision = new AckProxyRequestDecision();
        decision.setProxyAckSent(false);
        decision.setReason(reason);
        return decision;
    }

    public static AckProxyRequestDecision sent(byte[] proxyAck) {
        AckProxyRequestDecision decision = new AckProxyRequestDecision();
        decision.setProxyAckSent(true);
        decision.setProxyAck(proxyAck);
        decision.setReason("PROXY_ACK_SENT");
        return decision;
    }
}
