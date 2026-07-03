package com.infopublish.client.service;

import com.infopublish.client.entity.dto.ContentAuditItem;
import com.infopublish.client.entity.dto.ContentAuditRelayPacket;

import java.util.List;
import java.util.Map;

public interface ContentPreAuditService {

    PreAuditDecision inspect(ContentAuditRelayPacket relayPacket);

    void learnResponse(ContentAuditRelayPacket relayPacket);

    Map<String, Object> getStatus();

    Map<String, Object> updateRuntimeSettings(Map<String, Object> settings);

    List<ContentAuditItem> listItems(String status);

    ContentAuditItem getItem(String auditId);

    boolean approve(String auditId, String operator);

    boolean reject(String auditId, String operator);

    void clear();

    class PreAuditDecision {
        public enum Action {
            PASS,
            HOLD,
            REJECT
        }

        private final Action action;
        private final String reason;
        private final byte[] ackPayload;

        private PreAuditDecision(Action action, String reason) {
            this(action, reason, null);
        }

        private PreAuditDecision(Action action, String reason, byte[] ackPayload) {
            this.action = action;
            this.reason = reason;
            this.ackPayload = ackPayload == null ? null : ackPayload.clone();
        }

        public static PreAuditDecision pass(String reason) {
            return new PreAuditDecision(Action.PASS, reason);
        }

        public static PreAuditDecision hold(String reason) {
            return new PreAuditDecision(Action.HOLD, reason);
        }

        public static PreAuditDecision holdWithAck(String reason, byte[] ackPayload) {
            return new PreAuditDecision(Action.HOLD, reason, ackPayload);
        }

        public static PreAuditDecision reject(String reason) {
            return new PreAuditDecision(Action.REJECT, reason);
        }

        public Action getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public byte[] getAckPayload() {
            return ackPayload == null ? null : ackPayload.clone();
        }
    }
}
