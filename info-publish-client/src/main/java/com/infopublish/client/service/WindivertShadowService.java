package com.infopublish.client.service;

import java.util.List;
import java.util.Map;

/**
 * WinDivert Shadow Mode packet observer.
 */
public interface WindivertShadowService {

    Map<String, Object> getStatus();

    List<ShadowPacketEvent> getRecentEvents(int limit);

    void clear();

    class ShadowPacketEvent {
        private final long timestamp;
        private final String sourceIp;
        private final int sourcePort;
        private final String destinationIp;
        private final int destinationPort;
        private final int packetLength;
        private final int payloadLength;
        private final long pid;
        private final String processName;
        private final String processPath;
        private final String decision;
        private final String reasonCode;
        private final boolean pidCacheHit;
        private final boolean outbound;
        private final boolean impostor;

        public ShadowPacketEvent(long timestamp,
                                 String sourceIp,
                                 int sourcePort,
                                 String destinationIp,
                                 int destinationPort,
                                 int packetLength,
                                 int payloadLength,
                                 long pid,
                                 String processName,
                                 String processPath,
                                 String decision,
                                 String reasonCode,
                                 boolean pidCacheHit,
                                 boolean outbound,
                                 boolean impostor) {
            this.timestamp = timestamp;
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.destinationIp = destinationIp;
            this.destinationPort = destinationPort;
            this.packetLength = packetLength;
            this.payloadLength = payloadLength;
            this.pid = pid;
            this.processName = processName;
            this.processPath = processPath;
            this.decision = decision;
            this.reasonCode = reasonCode;
            this.pidCacheHit = pidCacheHit;
            this.outbound = outbound;
            this.impostor = impostor;
        }

        public long getTimestamp() { return timestamp; }
        public String getSourceIp() { return sourceIp; }
        public int getSourcePort() { return sourcePort; }
        public String getDestinationIp() { return destinationIp; }
        public int getDestinationPort() { return destinationPort; }
        public int getPacketLength() { return packetLength; }
        public int getPayloadLength() { return payloadLength; }
        public long getPid() { return pid; }
        public String getProcessName() { return processName; }
        public String getProcessPath() { return processPath; }
        public String getDecision() { return decision; }
        public String getReasonCode() { return reasonCode; }
        public boolean isPidCacheHit() { return pidCacheHit; }
        public boolean isOutbound() { return outbound; }
        public boolean isImpostor() { return impostor; }
    }
}
