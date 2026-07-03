package com.infopublish.client.service;

/**
 * Encodes transparent UDP relay packets.
 */
public interface RelayPacketCodec {

    byte[] encode(RelayPacket packet);

    RelayPacket decode(byte[] bytes, int length);

    class RelayPacket {
        private final long pid;
        private final String processName;
        private final String originalSrcIp;
        private final int originalSrcPort;
        private final String originalDstIp;
        private final int originalDstPort;
        private final long timestamp;
        private final byte[] payload;
        private final String contentTokenId;
        private final String contentFileId;

        public RelayPacket(long pid,
                           String processName,
                           String originalSrcIp,
                           int originalSrcPort,
                           String originalDstIp,
                           int originalDstPort,
                           long timestamp,
                           byte[] payload) {
            this(pid, processName, originalSrcIp, originalSrcPort, originalDstIp,
                    originalDstPort, timestamp, payload, null, null);
        }

        public RelayPacket(long pid,
                           String processName,
                           String originalSrcIp,
                           int originalSrcPort,
                           String originalDstIp,
                           int originalDstPort,
                           long timestamp,
                           byte[] payload,
                           String contentTokenId,
                           String contentFileId) {
            this.pid = pid;
            this.processName = processName;
            this.originalSrcIp = originalSrcIp;
            this.originalSrcPort = originalSrcPort;
            this.originalDstIp = originalDstIp;
            this.originalDstPort = originalDstPort;
            this.timestamp = timestamp;
            this.payload = payload;
            this.contentTokenId = contentTokenId;
            this.contentFileId = contentFileId;
        }

        public long getPid() { return pid; }
        public String getProcessName() { return processName; }
        public String getOriginalSrcIp() { return originalSrcIp; }
        public int getOriginalSrcPort() { return originalSrcPort; }
        public String getOriginalDstIp() { return originalDstIp; }
        public int getOriginalDstPort() { return originalDstPort; }
        public long getTimestamp() { return timestamp; }
        public byte[] getPayload() { return payload; }
        public String getContentTokenId() { return contentTokenId; }
        public String getContentFileId() { return contentFileId; }
    }
}
