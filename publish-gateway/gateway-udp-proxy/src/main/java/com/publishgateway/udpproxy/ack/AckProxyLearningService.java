package com.publishgateway.udpproxy.ack;

import com.publishgateway.udpproxy.config.AckProxyProperties;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures real Sigma request/ACK pairs while traffic still passes through.
 */
@Slf4j
@Service
public class AckProxyLearningService {

    private static final String TYPE1_FRAME = "JETFILE_TYPE1";

    @Resource
    private AckProxyProperties properties;

    @Resource
    private AckProxySimulator ackProxySimulator;

    private final Map<String, Deque<PendingRequest>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, IsolationSession> isolationSessions = new ConcurrentHashMap<>();
    private final Deque<AckProxySample> samples = new ArrayDeque<>();
    private final AtomicLong requestsRecorded = new AtomicLong();
    private final AtomicLong responsesRecorded = new AtomicLong();
    private final AtomicLong samplesCompleted = new AtomicLong();
    private final AtomicLong responsesUnmatched = new AtomicLong();
    private final AtomicLong pendingEvicted = new AtomicLong();
    private final AtomicLong simulatedCompared = new AtomicLong();
    private final AtomicLong simulatedMatched = new AtomicLong();
    private final AtomicLong simulatedMismatched = new AtomicLong();
    private final AtomicLong simulatedUnsupported = new AtomicLong();
    private final AtomicLong proxyAcksSent = new AtomicLong();
    private final AtomicLong realAcksSuppressed = new AtomicLong();
    private final AtomicLong isolationHeldPackets = new AtomicLong();
    private final AtomicLong isolationReleasedPackets = new AtomicLong();
    private final AtomicLong isolationRejectedSessions = new AtomicLong();

    public AckProxyRequestDecision recordRequest(String ingressType,
                                                 UdpProxyRule rule,
                                                 String sourceIp,
                                                 int sourcePort,
                                                 String targetIp,
                                                 int targetPort,
                                                 byte[] requestData) {
        if (!properties.isLearningEnabled() || rule == null || requestData == null || requestData.length == 0) {
            return AckProxyRequestDecision.none("DISABLED_OR_EMPTY");
        }
        long now = System.currentTimeMillis();
        String key = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        PendingRequest pending = new PendingRequest();
        pending.sampleId = UUID.randomUUID().toString();
        pending.ingressType = ingressType;
        pending.rule = rule;
        pending.sourceIp = sourceIp;
        pending.sourcePort = sourcePort;
        pending.targetIp = targetIp;
        pending.targetPort = targetPort;
        pending.requestData = requestData.clone();
        pending.requestHex = toHex(requestData, properties.getMaxHexBytes());
        pending.requestLength = requestData.length;
        pending.requestTime = now;
        pending.correlationKey = key;
        FrameSummary frame = parseFrame(requestData);
        pending.requestFrame = frame;
        if (properties.isProxyAckEnabled() && ackProxySimulator != null) {
            byte[] proxyAck = ackProxySimulator.generate(requestData);
            if (proxyAck != null) {
                pending.proxyAckSent = true;
                pending.proxyAck = proxyAck;
                proxyAcksSent.incrementAndGet();
            }
        }

        Deque<PendingRequest> queue = pendingRequests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(pending);
            evictExpired(queue, now);
        }
        requestsRecorded.incrementAndGet();
        evictOverflow();
        if (pending.proxyAckSent) {
            return AckProxyRequestDecision.sent(pending.proxyAck);
        }
        return AckProxyRequestDecision.none("NOT_PROXY_ACK_COMMAND");
    }

    public AckIsolationDecision isolateRequest(String ingressType,
                                               UdpProxyRule rule,
                                               String sourceIp,
                                               int sourcePort,
                                               String targetIp,
                                               int targetPort,
                                               byte[] requestData) {
        if (!properties.isIsolationActive() || rule == null || requestData == null || requestData.length == 0) {
            return AckIsolationDecision.pass("ISOLATION_DISABLED");
        }
        if (ackProxySimulator == null) {
            return AckIsolationDecision.pass("ACK_SIMULATOR_MISSING");
        }
        byte[] proxyAck = ackProxySimulator.generate(requestData);
        if (proxyAck == null) {
            return AckIsolationDecision.pass("NOT_ISOLATION_COMMAND");
        }
        cleanupExpiredIsolation(System.currentTimeMillis());
        String sessionKey = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        IsolationSession session = isolationSessions.computeIfAbsent(sessionKey,
                ignored -> new IsolationSession(sessionKey, ingressType, rule, sourceIp, sourcePort, targetIp, targetPort));
        synchronized (session) {
            long maxBytes = Math.max(1L, properties.getIsolationMaxSessionBytes());
            if (session.totalBytes + requestData.length > maxBytes) {
                isolationSessions.remove(sessionKey);
                isolationRejectedSessions.incrementAndGet();
                log.warn("[ACK-Isolation] reject session because cache exceeds limit: key={}, bytes={}, add={}, max={}",
                        sessionKey, session.totalBytes, requestData.length, maxBytes);
                return AckIsolationDecision.hold(proxyAck, true, "ISOLATION_CACHE_LIMIT_EXCEEDED");
            }
            session.packets.add(requestData.clone());
            session.totalBytes += requestData.length;
            session.lastUpdatedAt = System.currentTimeMillis();
        }
        isolationHeldPackets.incrementAndGet();
        proxyAcksSent.incrementAndGet();
        return AckIsolationDecision.hold(proxyAck, true, "ISOLATION_HOLD");
    }

    public void recordReplayRequestForSuppression(String ingressType,
                                                  UdpProxyRule rule,
                                                  String sourceIp,
                                                  int sourcePort,
                                                  String targetIp,
                                                  int targetPort,
                                                  byte[] requestData) {
        if (!properties.isLearningEnabled() || rule == null || requestData == null || requestData.length == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        PendingRequest pending = new PendingRequest();
        pending.sampleId = UUID.randomUUID().toString();
        pending.ingressType = ingressType;
        pending.rule = rule;
        pending.sourceIp = sourceIp;
        pending.sourcePort = sourcePort;
        pending.targetIp = targetIp;
        pending.targetPort = targetPort;
        pending.requestData = requestData.clone();
        pending.requestHex = toHex(requestData, properties.getMaxHexBytes());
        pending.requestLength = requestData.length;
        pending.requestTime = now;
        pending.correlationKey = key;
        pending.requestFrame = parseFrame(requestData);
        pending.proxyAckSent = true;
        pending.proxyAck = ackProxySimulator == null ? null : ackProxySimulator.generate(requestData);

        Deque<PendingRequest> queue = pendingRequests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(pending);
            evictExpired(queue, now);
        }
        requestsRecorded.incrementAndGet();
        evictOverflow();
    }

    public int releaseIsolation(String ingressType,
                                UdpProxyRule rule,
                                String sourceIp,
                                int sourcePort,
                                String targetIp,
                                int targetPort,
                                IsolationReplayHandler replayHandler) {
        if (rule == null || replayHandler == null) {
            return 0;
        }
        String sessionKey = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        IsolationSession session = isolationSessions.remove(sessionKey);
        if (session == null) {
            return 0;
        }
        List<byte[]> packets;
        synchronized (session) {
            packets = new ArrayList<>(session.packets);
        }
        int replayed = 0;
        for (byte[] packet : packets) {
            try {
                replayHandler.replay(packet);
                replayed++;
            } catch (Exception e) {
                log.warn("[ACK-Isolation] replay packet failed: key={}, replayed={}, reason={}",
                        sessionKey, replayed, e.getMessage(), e);
                break;
            }
        }
        isolationReleasedPackets.addAndGet(replayed);
        log.info("[ACK-Isolation] released session: key={}, replayed={}, total={}",
                sessionKey, replayed, packets.size());
        return replayed;
    }

    public List<byte[]> drainIsolation(String ingressType,
                                       UdpProxyRule rule,
                                       String sourceIp,
                                       int sourcePort,
                                       String targetIp,
                                       int targetPort) {
        if (rule == null) {
            return new ArrayList<>();
        }
        String sessionKey = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        IsolationSession session = isolationSessions.remove(sessionKey);
        if (session == null) {
            return new ArrayList<>();
        }
        List<byte[]> packets;
        synchronized (session) {
            packets = new ArrayList<>(session.packets);
        }
        isolationReleasedPackets.addAndGet(packets.size());
        log.info("[ACK-Isolation] drained session for replay: key={}, packets={}", sessionKey, packets.size());
        return packets;
    }

    public int rejectIsolation(String ingressType,
                               UdpProxyRule rule,
                               String sourceIp,
                               int sourcePort,
                               String targetIp,
                               int targetPort,
                               String reason) {
        if (rule == null) {
            return 0;
        }
        String sessionKey = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        IsolationSession session = isolationSessions.remove(sessionKey);
        if (session == null) {
            return 0;
        }
        int packets;
        synchronized (session) {
            packets = session.packets.size();
        }
        isolationRejectedSessions.incrementAndGet();
        log.warn("[ACK-Isolation] rejected session: key={}, packets={}, reason={}", sessionKey, packets, reason);
        return packets;
    }

    public boolean recordResponse(String ingressType,
                                  UdpProxyRule rule,
                                  String sourceIp,
                                  int sourcePort,
                                  String targetIp,
                                  int targetPort,
                                  InetSocketAddress responseSender,
                                  byte[] responseData) {
        if (!properties.isLearningEnabled() || rule == null || responseData == null || responseData.length == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = buildCorrelationKey(ingressType, rule.getRuleId(), sourceIp, sourcePort, targetIp, targetPort);
        PendingRequest pending = pollPending(key, now);
        responsesRecorded.incrementAndGet();
        if (pending == null) {
            responsesUnmatched.incrementAndGet();
            log.debug("[ACK-Learn] response unmatched: key={}, responseBytes={}", key, responseData.length);
            return false;
        }

        AckProxySample sample = new AckProxySample();
        sample.setSampleId(pending.sampleId);
        sample.setIngressType(pending.ingressType);
        sample.setRuleId(rule.getRuleId());
        sample.setChainId(rule.getChainId());
        sample.setSourceIp(sourceIp);
        sample.setSourcePort(sourcePort);
        sample.setTargetIp(targetIp);
        sample.setTargetPort(targetPort);
        if (responseSender != null) {
            sample.setResponseSourceIp(responseSender.getAddress() == null ? null : responseSender.getAddress().getHostAddress());
            sample.setResponseSourcePort(responseSender.getPort());
        }
        sample.setRequestHex(pending.requestHex);
        sample.setResponseHex(toHex(responseData, properties.getMaxHexBytes()));
        sample.setRequestLength(pending.requestLength);
        sample.setResponseLength(responseData.length);
        sample.setRequestTime(pending.requestTime);
        sample.setResponseTime(now);
        sample.setRttMs(now - pending.requestTime);
        sample.setCorrelationKey(pending.correlationKey);
        sample.setProxyAckSent(pending.proxyAckSent);

        FrameSummary requestFrame = pending.requestFrame;
        FrameSummary responseFrame = parseFrame(responseData);
        applyRequestFrame(sample, requestFrame);
        sample.setResponseFrameType(responseFrame.frameType);
        sample.setMatchedBySerial(requestFrame.packetSerial != null
                && responseFrame.packetSerial != null
                && requestFrame.packetSerial.equals(responseFrame.packetSerial));
        if (sample.getParseError() == null) {
            sample.setParseError(joinParseErrors(requestFrame.parseError, responseFrame.parseError));
        }
        applySimulation(sample, pending.requestData, responseData);
        if (pending.proxyAckSent) {
            sample.setRealAckSuppressed(true);
            realAcksSuppressed.incrementAndGet();
        } else {
            sample.setRealAckSuppressed(false);
        }

        addSample(sample);
        samplesCompleted.incrementAndGet();
        log.debug("[ACK-Learn] captured sample: ruleId={}, serial={}, rttMs={}, requestBytes={}, responseBytes={}",
                rule.getRuleId(), sample.getPacketSerial(), sample.getRttMs(),
                sample.getRequestLength(), sample.getResponseLength());
        return pending.proxyAckSent;
    }

    public List<AckProxySample> listSamples(int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, properties.getMaxSamples());
        synchronized (samples) {
            List<AckProxySample> result = new ArrayList<>(Math.min(safeLimit, samples.size()));
            Iterator<AckProxySample> iterator = samples.descendingIterator();
            while (iterator.hasNext() && result.size() < safeLimit) {
                result.add(iterator.next());
            }
            return result;
        }
    }

    public List<AckProxySample> listMismatches(int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, properties.getMaxSamples());
        synchronized (samples) {
            List<AckProxySample> result = new ArrayList<>(Math.min(safeLimit, samples.size()));
            Iterator<AckProxySample> iterator = samples.descendingIterator();
            while (iterator.hasNext() && result.size() < safeLimit) {
                AckProxySample sample = iterator.next();
                if (Boolean.TRUE.equals(sample.getSimulatedCompared())
                        && !Boolean.TRUE.equals(sample.getSimulatedMatched())) {
                    result.add(sample);
                }
            }
            return result;
        }
    }

    public AckProxyStatus getStatus() {
        long now = System.currentTimeMillis();
        cleanupExpiredPending(now);
        cleanupExpiredIsolation(now);
        AckProxyStatus status = new AckProxyStatus();
        status.setEnabled(properties.isEnabled());
        status.setMode(properties.getMode());
        status.setLearningEnabled(properties.isLearningEnabled());
        status.setCompareEnabled(properties.isCompareEnabled());
        status.setProxyAckEnabled(properties.isProxyAckEnabled());
        status.setIsolationActive(properties.isIsolationActive());
        status.setRequestsRecorded(requestsRecorded.get());
        status.setResponsesRecorded(responsesRecorded.get());
        status.setSamplesCompleted(samplesCompleted.get());
        status.setResponsesUnmatched(responsesUnmatched.get());
        status.setPendingEvicted(pendingEvicted.get());
        status.setSimulatedCompared(simulatedCompared.get());
        status.setSimulatedMatched(simulatedMatched.get());
        status.setSimulatedMismatched(simulatedMismatched.get());
        status.setSimulatedUnsupported(simulatedUnsupported.get());
        status.setProxyAcksSent(proxyAcksSent.get());
        status.setRealAcksSuppressed(realAcksSuppressed.get());
        status.setIsolationHeldPackets(isolationHeldPackets.get());
        status.setIsolationReleasedPackets(isolationReleasedPackets.get());
        status.setIsolationRejectedSessions(isolationRejectedSessions.get());
        status.setIsolationSessions(isolationSessions.size());
        long compared = simulatedCompared.get();
        status.setSimulatedMatchRate(compared == 0 ? 0.0D : (simulatedMatched.get() * 1.0D / compared));
        status.setPendingRequests(countPending());
        synchronized (samples) {
            status.setSampleCount(samples.size());
        }
        status.setMaxSamples(properties.getMaxSamples());
        status.setMaxPendingRequests(properties.getMaxPendingRequests());
        status.setPendingTimeoutMs(properties.getPendingTimeoutMs());
        return status;
    }

    public List<AckProxyPendingRequest> listPending(int limit) {
        cleanupExpiredPending(System.currentTimeMillis());
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, properties.getMaxPendingRequests());
        long now = System.currentTimeMillis();
        List<AckProxyPendingRequest> result = new ArrayList<>();
        for (Deque<PendingRequest> queue : pendingRequests.values()) {
            synchronized (queue) {
                for (PendingRequest pending : queue) {
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                    result.add(toPendingSummary(pending, now));
                }
            }
        }
        return result;
    }

    public void clear() {
        pendingRequests.clear();
        synchronized (samples) {
            samples.clear();
        }
        requestsRecorded.set(0);
        responsesRecorded.set(0);
        samplesCompleted.set(0);
        responsesUnmatched.set(0);
        pendingEvicted.set(0);
        simulatedCompared.set(0);
        simulatedMatched.set(0);
        simulatedMismatched.set(0);
        simulatedUnsupported.set(0);
        proxyAcksSent.set(0);
        realAcksSuppressed.set(0);
        isolationHeldPackets.set(0);
        isolationReleasedPackets.set(0);
        isolationRejectedSessions.set(0);
        isolationSessions.clear();
    }

    private void applySimulation(AckProxySample sample, byte[] requestData, byte[] responseData) {
        if (!properties.isCompareEnabled() || ackProxySimulator == null) {
            return;
        }
        AckSimulationResult result = ackProxySimulator.simulateAndCompare(requestData, responseData);
        sample.setSimulatedCompared(result.isCompared());
        sample.setSimulatedMatched(result.isMatched());
        sample.setSimulatedAckLength(result.getSimulatedAck() == null ? null : result.getSimulatedAck().length);
        sample.setSimulatedAckHex(toHex(result.getSimulatedAck(), properties.getMaxHexBytes()));
        sample.setSimulatedAckReason(result.getReason());
        sample.setFirstDiffOffset(result.getFirstDiffOffset());
        sample.setRealByteAtDiff(result.getRealByteAtDiff());
        sample.setSimulatedByteAtDiff(result.getSimulatedByteAtDiff());
        if (!result.isCompared()) {
            simulatedUnsupported.incrementAndGet();
            return;
        }
        simulatedCompared.incrementAndGet();
        if (result.isMatched()) {
            simulatedMatched.incrementAndGet();
        } else {
            simulatedMismatched.incrementAndGet();
        }
    }

    private PendingRequest pollPending(String key, long now) {
        Deque<PendingRequest> queue = pendingRequests.get(key);
        if (queue == null) {
            return null;
        }
        synchronized (queue) {
            evictExpired(queue, now);
            PendingRequest pending = queue.pollFirst();
            if (queue.isEmpty()) {
                pendingRequests.remove(key, queue);
            }
            return pending;
        }
    }

    private void addSample(AckProxySample sample) {
        synchronized (samples) {
            samples.addLast(sample);
            int maxSamples = Math.max(1, properties.getMaxSamples());
            while (samples.size() > maxSamples) {
                samples.removeFirst();
            }
        }
    }

    private void cleanupExpiredPending(long now) {
        for (Map.Entry<String, Deque<PendingRequest>> entry : pendingRequests.entrySet()) {
            Deque<PendingRequest> queue = entry.getValue();
            synchronized (queue) {
                evictExpired(queue, now);
                if (queue.isEmpty()) {
                    pendingRequests.remove(entry.getKey(), queue);
                }
            }
        }
    }

    private void cleanupExpiredIsolation(long now) {
        long timeoutMs = Math.max(1000L, properties.getIsolationSessionTimeoutMs());
        for (Map.Entry<String, IsolationSession> entry : isolationSessions.entrySet()) {
            IsolationSession session = entry.getValue();
            synchronized (session) {
                if (now - session.lastUpdatedAt > timeoutMs) {
                    isolationSessions.remove(entry.getKey(), session);
                    isolationRejectedSessions.incrementAndGet();
                    log.warn("[ACK-Isolation] evicted expired session: key={}, packets={}, ageMs={}",
                            entry.getKey(), session.packets.size(), now - session.lastUpdatedAt);
                }
            }
        }
    }

    private AckProxyPendingRequest toPendingSummary(PendingRequest pending, long now) {
        AckProxyPendingRequest summary = new AckProxyPendingRequest();
        summary.setSampleId(pending.sampleId);
        summary.setIngressType(pending.ingressType);
        summary.setRuleId(pending.rule == null ? null : pending.rule.getRuleId());
        summary.setChainId(pending.rule == null ? null : pending.rule.getChainId());
        summary.setSourceIp(pending.sourceIp);
        summary.setSourcePort(pending.sourcePort);
        summary.setTargetIp(pending.targetIp);
        summary.setTargetPort(pending.targetPort);
        summary.setRequestTime(pending.requestTime);
        summary.setAgeMs(now - pending.requestTime);
        summary.setRequestLength(pending.requestLength);
        summary.setRequestHex(pending.requestHex);
        summary.setProxyAckSent(pending.proxyAckSent);
        summary.setCorrelationKey(pending.correlationKey);
        if (pending.requestFrame != null) {
            summary.setRequestFrameType(pending.requestFrame.frameType);
            summary.setPacketSerial(pending.requestFrame.packetSerial);
            summary.setMainCmd(pending.requestFrame.mainCmd);
            summary.setSubCmd(pending.requestFrame.subCmd);
            summary.setParseError(pending.requestFrame.parseError);
        }
        return summary;
    }

    private void evictExpired(Deque<PendingRequest> queue, long now) {
        long timeoutMs = Math.max(1000L, properties.getPendingTimeoutMs());
        while (!queue.isEmpty()) {
            PendingRequest first = queue.peekFirst();
            if (first == null || now - first.requestTime <= timeoutMs) {
                break;
            }
            queue.removeFirst();
            pendingEvicted.incrementAndGet();
        }
    }

    private void evictOverflow() {
        int maxPending = Math.max(1, properties.getMaxPendingRequests());
        while (countPending() > maxPending) {
            PendingRequest oldest = null;
            String oldestKey = null;
            for (Map.Entry<String, Deque<PendingRequest>> entry : pendingRequests.entrySet()) {
                Deque<PendingRequest> queue = entry.getValue();
                synchronized (queue) {
                    PendingRequest candidate = queue.peekFirst();
                    if (candidate != null && (oldest == null || candidate.requestTime < oldest.requestTime)) {
                        oldest = candidate;
                        oldestKey = entry.getKey();
                    }
                }
            }
            if (oldestKey == null) {
                return;
            }
            Deque<PendingRequest> queue = pendingRequests.get(oldestKey);
            if (queue == null) {
                continue;
            }
            synchronized (queue) {
                if (!queue.isEmpty()) {
                    queue.removeFirst();
                    pendingEvicted.incrementAndGet();
                }
                if (queue.isEmpty()) {
                    pendingRequests.remove(oldestKey, queue);
                }
            }
        }
    }

    private int countPending() {
        int count = 0;
        for (Deque<PendingRequest> queue : pendingRequests.values()) {
            synchronized (queue) {
                count += queue.size();
            }
        }
        return count;
    }

    private String buildCorrelationKey(String ingressType,
                                       String ruleId,
                                       String sourceIp,
                                       int sourcePort,
                                       String targetIp,
                                       int targetPort) {
        return safe(ingressType) + "|" + safe(ruleId) + "|"
                + safe(sourceIp) + ":" + sourcePort + "->" + safe(targetIp) + ":" + targetPort;
    }

    private FrameSummary parseFrame(byte[] data) {
        FrameSummary summary = new FrameSummary();
        if (data == null || data.length == 0) {
            summary.parseError = "EMPTY_PACKET";
            return summary;
        }
        if (data.length >= 2 && data[0] == 0x55) {
            if (data.length < 16) {
                summary.frameType = "UNKNOWN_55";
                summary.parseError = "JETFILE_HEADER_TOO_SHORT";
                return summary;
            }
            int syn2 = data[1] & 0xFF;
            if (syn2 == 0xA7 || syn2 == 0xA3) {
                summary.frameType = "TYPE2";
            } else if (syn2 == 0xA8 || syn2 == 0xA4) {
                summary.frameType = "TYPE3";
            } else {
                summary.frameType = "UNKNOWN_55";
                summary.parseError = "JETFILE_SYNC2_UNSUPPORTED";
                return summary;
            }
            boolean crcFrame = syn2 == 0xA3 || syn2 == 0xA4;
            summary.checksumType = crcFrame ? "CRC_X25" : "SUM";
            summary.checksum = crcFrame ? bigEndianUInt16(data, 2) : littleEndianUInt16(data, 2);
            summary.sourceAddress = littleEndianUInt16(data, 6);
            int groupAddr = data[8] & 0xFF;
            int unitAddr = data[9] & 0xFF;
            summary.destinationAddress = groupAddr + ":" + unitAddr;
            summary.packetSerial = littleEndianUInt16(data, 10);
            summary.mainCmd = String.format("0x%02X", data[12] & 0xFF);
            summary.subCmd = String.format("0x%02X", data[13] & 0xFF);
            int argLen = data[14] & 0xFF;
            int argBytes = argLen * 4;
            summary.needResponse = data[15] == 0;
            if (argBytes > 0 && data.length >= 20) {
                summary.currentPart = littleEndianInt32(data, 16);
            }
            return summary;
        }
        if (data[0] == 0x01) {
            summary.frameType = TYPE1_FRAME;
            return summary;
        }
        summary.frameType = "UNKNOWN";
        summary.parseError = "UNSUPPORTED_FRAME";
        return summary;
    }

    private Integer littleEndianUInt16(byte[] data, int offset) {
        if (data == null || data.length < offset + 2) {
            return null;
        }
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private Integer bigEndianUInt16(byte[] data, int offset) {
        if (data == null || data.length < offset + 2) {
            return null;
        }
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private Integer littleEndianInt32(byte[] data, int offset) {
        if (data == null || data.length < offset + 4) {
            return null;
        }
        int value = data[offset] & 0xFF;
        value |= (data[offset + 1] & 0xFF) << 8;
        value |= (data[offset + 2] & 0xFF) << 16;
        value |= (data[offset + 3] & 0xFF) << 24;
        return value;
    }

    private void applyRequestFrame(AckProxySample sample, FrameSummary frame) {
        sample.setRequestFrameType(frame.frameType);
        sample.setPacketSerial(frame.packetSerial);
        sample.setMainCmd(frame.mainCmd);
        sample.setSubCmd(frame.subCmd);
        sample.setCurrentPart(frame.currentPart);
        sample.setNeedResponse(frame.needResponse);
        sample.setChecksumType(frame.checksumType);
        sample.setChecksum(frame.checksum);
        sample.setSourceAddress(frame.sourceAddress);
        sample.setDestinationAddress(frame.destinationAddress);
        sample.setParseError(frame.parseError);
    }

    private String joinParseErrors(String requestError, String responseError) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (requestError != null && !requestError.trim().isEmpty()) {
            errors.put("request", requestError);
        }
        if (responseError != null && !responseError.trim().isEmpty()) {
            errors.put("response", responseError);
        }
        if (errors.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private String toHex(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            return "";
        }
        int safeMax = maxBytes <= 0 ? data.length : Math.min(data.length, maxBytes);
        StringBuilder builder = new StringBuilder(safeMax * 2 + (data.length > safeMax ? 16 : 0));
        for (int i = 0; i < safeMax; i++) {
            builder.append(String.format("%02X", data[i] & 0xFF));
        }
        if (data.length > safeMax) {
            builder.append("...TRUNCATED_").append(data.length - safeMax).append("_BYTES");
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class PendingRequest {
        private String sampleId;
        private String ingressType;
        private UdpProxyRule rule;
        private String sourceIp;
        private int sourcePort;
        private String targetIp;
        private int targetPort;
        private byte[] requestData;
        private String requestHex;
        private int requestLength;
        private long requestTime;
        private String correlationKey;
        private FrameSummary requestFrame;
        private boolean proxyAckSent;
        private byte[] proxyAck;
    }

    private static class FrameSummary {
        private String frameType;
        private Integer packetSerial;
        private String mainCmd;
        private String subCmd;
        private Integer currentPart;
        private Boolean needResponse;
        private String checksumType;
        private Integer checksum;
        private Integer sourceAddress;
        private String destinationAddress;
        private String parseError;
    }

    public interface IsolationReplayHandler {
        void replay(byte[] packet) throws Exception;
    }

    private static class IsolationSession {
        private final String sessionKey;
        private final String ingressType;
        private final UdpProxyRule rule;
        private final String sourceIp;
        private final int sourcePort;
        private final String targetIp;
        private final int targetPort;
        private final List<byte[]> packets = new ArrayList<>();
        private final long createdAt = System.currentTimeMillis();
        private long lastUpdatedAt = createdAt;
        private long totalBytes;

        private IsolationSession(String sessionKey,
                                 String ingressType,
                                 UdpProxyRule rule,
                                 String sourceIp,
                                 int sourcePort,
                                 String targetIp,
                                 int targetPort) {
            this.sessionKey = sessionKey;
            this.ingressType = ingressType;
            this.rule = rule;
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.targetIp = targetIp;
            this.targetPort = targetPort;
        }
    }
}
