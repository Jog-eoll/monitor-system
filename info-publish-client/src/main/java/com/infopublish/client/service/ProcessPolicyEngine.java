package com.infopublish.client.service;

import java.util.Map;

/**
 * Shadow-mode process policy evaluator.
 */
public interface ProcessPolicyEngine {

    PolicyDecision decide(ProcessInfoResolver.ProcessInfo processInfo);

    Map<String, Object> getStatus();

    class PolicyDecision {
        private final String decision;
        private final String reasonCode;
        private final boolean whitelistMatched;

        private PolicyDecision(String decision, String reasonCode, boolean whitelistMatched) {
            this.decision = decision;
            this.reasonCode = reasonCode;
            this.whitelistMatched = whitelistMatched;
        }

        public static PolicyDecision allow(String reasonCode, boolean whitelistMatched) {
            return new PolicyDecision("ALLOW_SHADOW", reasonCode, whitelistMatched);
        }

        public static PolicyDecision deny(String reasonCode) {
            return new PolicyDecision("DENY_SHADOW", reasonCode, false);
        }

        public static PolicyDecision unknown(String reasonCode) {
            return new PolicyDecision("UNKNOWN_SHADOW", reasonCode, false);
        }

        public String getDecision() { return decision; }
        public String getReasonCode() { return reasonCode; }
        public boolean isWhitelistMatched() { return whitelistMatched; }
    }
}
