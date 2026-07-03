package com.publishgateway.udpproxy.secure;

import lombok.Data;

@Data
public class SecurePublishIngressDecision {

    public enum Action {
        BYPASS,
        HOLD,
        FORWARD_PAYLOAD,
        REJECT
    }

    private Action action;
    private byte[] forwardData;
    private String reason;
    private String packageName;
    private String payloadName;

    public static SecurePublishIngressDecision bypass() {
        SecurePublishIngressDecision decision = new SecurePublishIngressDecision();
        decision.setAction(Action.BYPASS);
        return decision;
    }

    public static SecurePublishIngressDecision hold(String packageName) {
        SecurePublishIngressDecision decision = new SecurePublishIngressDecision();
        decision.setAction(Action.HOLD);
        decision.setPackageName(packageName);
        return decision;
    }

    public static SecurePublishIngressDecision forward(SecurePublishVerifyResult result) {
        SecurePublishIngressDecision decision = new SecurePublishIngressDecision();
        decision.setAction(Action.FORWARD_PAYLOAD);
        decision.setForwardData(result.getPayloadBytes());
        decision.setReason(result.getReason());
        decision.setPackageName(result.getPackageName());
        decision.setPayloadName(result.getPayloadName());
        return decision;
    }

    public static SecurePublishIngressDecision reject(SecurePublishVerifyResult result) {
        SecurePublishIngressDecision decision = new SecurePublishIngressDecision();
        decision.setAction(Action.REJECT);
        decision.setReason(result == null ? "VERIFY_FAILED" : result.getReason());
        decision.setPackageName(result == null ? null : result.getPackageName());
        decision.setPayloadName(result == null ? null : result.getPayloadName());
        return decision;
    }
}
