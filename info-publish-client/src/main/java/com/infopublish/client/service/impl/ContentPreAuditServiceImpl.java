package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.infopublish.client.config.AppConfig.ContentPreAuditProperties;
import com.infopublish.client.entity.dto.ContentAuditItem;
import com.infopublish.client.entity.dto.ContentAuditRelayPacket;
import com.infopublish.client.entity.dto.ContentReleaseTokenIssueResponse;
import com.infopublish.client.service.ContentPreAuditRelaySender;
import com.infopublish.client.service.ContentPreAuditService;
import com.infopublish.client.service.ContentReleaseTokenClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentPreAuditServiceImpl implements ContentPreAuditService {

    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xA7;
    private static final byte SYN_TYPE2_CRC = (byte) 0xA3;
    private static final byte SYN_TYPE3_SUM = (byte) 0xA8;
    private static final byte SYN_TYPE3_CRC = (byte) 0xA4;
    private static final int DEFAULT_STANDARD_PAYLOAD_SIZE = 768;
    private static final Charset GBK = Charset.forName("GBK");

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "bmp", "gif", "webp"));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "mpeg", "mpg", "ts"));
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "json", "xml", "csv", "log"));

    private final ContentPreAuditProperties properties;
    private final RestTemplate restTemplate;
    private final ContentReleaseTokenClient contentReleaseTokenClient;
    private final ObjectProvider<ContentPreAuditRelaySender> relaySenderProvider;
    private final ExecutorService auditExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "content-pre-audit");
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentMap<String, FileAssembly> assemblyCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ContentAuditItem> itemCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DecisionCacheEntry> decisionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AckTemplate> ackTemplates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AckCandidate> recentAckCandidates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> blockedContentRefs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> itemOrder = new ConcurrentLinkedDeque<>();

    private final AtomicLong packetsSeen = new AtomicLong();
    private final AtomicLong auditablePackets = new AtomicLong();
    private final AtomicLong autoApproved = new AtomicLong();
    private final AtomicLong manualApproved = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong remoteFailures = new AtomicLong();
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong idleCompleted = new AtomicLong();
    private final AtomicLong cachedAllowed = new AtomicLong();
    private final AtomicLong cachedRejected = new AtomicLong();
    private final AtomicLong duplicateSuppressed = new AtomicLong();
    private final AtomicLong tokensRequested = new AtomicLong();
    private final AtomicLong tokensIssued = new AtomicLong();
    private final AtomicLong tokenFailed = new AtomicLong();
    private final AtomicLong packetsHeld = new AtomicLong();
    private final AtomicLong packetsReplayed = new AtomicLong();
    private final AtomicLong localFileReadHits = new AtomicLong();
    private final AtomicLong localFileReadMisses = new AtomicLong();
    private final AtomicLong ackLearned = new AtomicLong();
    private final AtomicLong ackInjected = new AtomicLong();
    private final AtomicLong ackTemplateMissing = new AtomicLong();
    private final AtomicLong ackTemplateFallbackUsed = new AtomicLong();
    private final AtomicLong ackTemplateGlobalFallbackUsed = new AtomicLong();
    private final AtomicLong contentBlocked = new AtomicLong();
    private final AtomicLong referencedCommandsBlocked = new AtomicLong();

    private volatile String lastError;
    private volatile long lastAuditAt;
    private volatile String lastCompleteReason;
    private volatile String lastLocalFileReadError;
    private volatile String lastLearnedAckKey;
    private volatile String lastMissingAckKey;

    @PreDestroy
    public void shutdown() {
        auditExecutor.shutdownNow();
    }

    @Override
    public PreAuditDecision inspect(ContentAuditRelayPacket relayPacket) {
        // Transmission-stage pre-audit is retired; AI detection now runs before signing.
        return PreAuditDecision.pass("CONTENT_PRE_AUDIT_RETIRED");
    }

    private PreAuditDecision inspectLegacy(ContentAuditRelayPacket relayPacket) {
        if (relayPacket == null || relayPacket.getPayload() == null) {
            return PreAuditDecision.pass("EMPTY_PACKET");
        }
        if (!properties.isEnabled()) {
            return PreAuditDecision.pass("CONTENT_PRE_AUDIT_DISABLED");
        }

        packetsSeen.incrementAndGet();
        cleanupExpiredItems();
        cleanupStaleAssemblies();

        try {
            ParsedFilePacket parsed = parseFileTransfer(relayPacket.getPayload());
            if (parsed == null) {
                String blockedRef = findBlockedContentReference(relayPacket);
                if (blockedRef != null) {
                    referencedCommandsBlocked.incrementAndGet();
                    contentBlocked.incrementAndGet();
                    lastError = "REJECTED_CONTENT_REFERENCE_BLOCKED";
                    log.warn("[ContentPreAudit] blocked rejected content reference: target={}:{}, ref={}",
                            relayPacket.getTargetIp(), relayPacket.getTargetPort(), blockedRef);
                    return PreAuditDecision.reject("REJECTED_CONTENT_REFERENCE_BLOCKED");
                }
                return PreAuditDecision.pass("NOT_RECOGNIZED_CONTENT");
            }
            String contentType = resolveContentType(parsed.fileName);
            if (contentType == null) {
                return PreAuditDecision.pass("UNSUPPORTED_FILE_TYPE");
            }
            auditablePackets.incrementAndGet();
            rememberAckCandidate(relayPacket, parsed);
            if (isAckEnforceMode()) {
                return enforceAck(relayPacket, parsed, contentType);
            }
            if (isTokenEnforceMode()) {
                return enforceToken(relayPacket, parsed, contentType);
            }
            return assembleAndAudit(relayPacket, parsed, contentType);
        } catch (Throwable t) {
            lastError = t.getMessage();
            log.warn("[ContentPreAudit] inspect failed, fallback pass: {}", t.getMessage());
            return PreAuditDecision.pass("PRE_AUDIT_EXCEPTION_PASS");
        }
    }

    @Override
    public void learnResponse(ContentAuditRelayPacket relayPacket) {
        // Transmission-stage pre-audit is retired; ACK learning must not affect WinDivert relay.
    }

    @Override
    public Map<String, Object> getStatus() {
        cleanupExpiredItems();
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", false);
        status.put("mode", "disabled");
        status.put("running", false);
        status.put("retired", true);
        status.put("replacement", "secure-publish.signer-audit");
        status.put("configuredEnabled", properties.isEnabled());
        status.put("configuredMode", properties.getMode());
        status.put("pendingCount", pendingCount.get());
        status.put("autoApproved", autoApproved.get());
        status.put("manualApproved", manualApproved.get());
        status.put("rejected", rejected.get());
        status.put("lastError", lastError);
        status.put("lastAuditAt", lastAuditAt);
        status.put("lastCompleteReason", lastCompleteReason);
        status.put("packetsSeen", packetsSeen.get());
        status.put("auditablePackets", auditablePackets.get());
        status.put("remoteFailures", remoteFailures.get());
        status.put("idleCompleted", idleCompleted.get());
        status.put("cachedAllowed", cachedAllowed.get());
        status.put("cachedRejected", cachedRejected.get());
        status.put("duplicateSuppressed", duplicateSuppressed.get());
        status.put("tokenRequired", false);
        status.put("enforceToken", false);
        status.put("tokensRequested", tokensRequested.get());
        status.put("tokensIssued", tokensIssued.get());
        status.put("tokenFailed", tokenFailed.get());
        status.put("packetsHeld", packetsHeld.get());
        status.put("packetsReplayed", packetsReplayed.get());
        status.put("localFileReadHits", localFileReadHits.get());
        status.put("localFileReadMisses", localFileReadMisses.get());
        status.put("lastLocalFileReadError", lastLocalFileReadError);
        status.put("ackSimulationEnabled", false);
        status.put("ackLearningEnabled", false);
        status.put("ackTemplates", 0);
        status.put("ackLearned", 0);
        status.put("ackInjected", 0);
        status.put("ackTemplateMissing", 0);
        status.put("ackTemplateFallbackUsed", 0);
        status.put("ackTemplateGlobalFallbackUsed", 0);
        status.put("lastLearnedAckKey", null);
        status.put("lastMissingAckKey", null);
        status.put("ackTemplateKeys", Collections.emptyList());
        status.put("ackTemplatePayloadCounts", Collections.emptyMap());
        status.put("ackLearningRetired", true);
        status.put("assemblyIdleCompleteMs", properties.getAssemblyIdleCompleteMs());
        status.put("contentBlocked", contentBlocked.get());
        status.put("referencedCommandsBlocked", referencedCommandsBlocked.get());
        status.put("blockedContentRefs", blockedContentRefs.size());
        status.put("replayDelayMs", properties.getReplayDelayMs());
        status.put("activeAssemblies", assemblyCache.size());
        status.put("activeAssemblyDetails", activeAssemblyDetails());
        status.put("decisionCacheSize", decisionCache.size());
        status.put("items", itemCache.size());
        return status;
    }

    @Override
    public synchronized Map<String, Object> updateRuntimeSettings(Map<String, Object> settings) {
        properties.setEnabled(false);
        properties.setMode("audit");
        properties.setAckSimulationEnabled(false);
        properties.setAckLearningEnabled(false);
        properties.setTokenRequired(false);
        ackTemplates.clear();
        recentAckCandidates.clear();
        ackLearned.set(0L);
        ackInjected.set(0L);
        ackTemplateMissing.set(0L);
        ackTemplateFallbackUsed.set(0L);
        ackTemplateGlobalFallbackUsed.set(0L);
        lastLearnedAckKey = null;
        lastMissingAckKey = null;
        log.info("[ContentPreAudit] runtime settings ignored because transmission-stage pre-audit is retired");
        return getStatus();
    }

    @Override
    public List<ContentAuditItem> listItems(String status) {
        cleanupExpiredItems();
        List<ContentAuditItem> items = new ArrayList<>();
        for (ContentAuditItem item : itemCache.values()) {
            if (item == null) {
                continue;
            }
            if (status == null || status.trim().isEmpty()
                    || status.equalsIgnoreCase(item.getStatus())
                    || ("PENDING_MANUAL".equalsIgnoreCase(status) && isPendingManualStatus(item.getStatus()))) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparingLong(ContentAuditItem::getUpdatedAt).reversed());
        return items;
    }

    @Override
    public ContentAuditItem getItem(String auditId) {
        if (auditId == null || auditId.trim().isEmpty()) {
            return null;
        }
        cleanupExpiredItems();
        return itemCache.get(auditId.trim());
    }

    @Override
    public boolean approve(String auditId, String operator) {
        ContentAuditItem item = getItem(auditId);
        if (item == null || !isPendingManualStatus(item.getStatus())) {
            return false;
        }
        boolean countAsPending = isPendingManualStatus(item.getStatus());
        if (isTokenEnforceMode() && !item.isForwarded()) {
            ContentReleaseTokenIssueResponse token = issueToken(item, "LOW");
            if (!isTokenIssued(token)) {
                item.setTokenError(token == null ? "TOKEN_ISSUE_FAILED" : token.getError());
                item.setUpdatedAt(System.currentTimeMillis());
                lastError = item.getTokenError();
                return false;
            }
            applyToken(item, token);
            if (!forwardBufferedPackets(item)) {
                item.setTokenError("TOKEN_REPLAY_FAILED");
                item.setUpdatedAt(System.currentTimeMillis());
                lastError = item.getTokenError();
                return false;
            }
        }
        item.setStatus("MANUAL_APPROVED");
        item.setDecision("MANUAL_APPROVED");
        item.setHandledBy(operator);
        item.setHandledAt(System.currentTimeMillis());
        item.setUpdatedAt(System.currentTimeMillis());
        item.setForwarded(item.isForwarded() || item.isPassthroughForwarded());
        cacheDecision(item.getAuditKey(), "PASS", item.getAuditId(),
                item.getContentTokenId(), item.getContentFileId(), item.getTokenExpireAt());
        discardHeavyPayload(item);
        manualApproved.incrementAndGet();
        if (countAsPending) {
            pendingCount.updateAndGet(v -> Math.max(0L, v - 1L));
        }
        return true;
    }

    @Override
    public boolean reject(String auditId, String operator) {
        ContentAuditItem item = getItem(auditId);
        if (item == null || !isPendingManualStatus(item.getStatus())) {
            return false;
        }
        boolean countAsPending = isPendingManualStatus(item.getStatus());
        item.setStatus("REJECTED");
        item.setDecision("REJECT");
        item.setReason("MANUAL_REJECTED");
        item.setHandledBy(operator);
        item.setHandledAt(System.currentTimeMillis());
        item.setUpdatedAt(System.currentTimeMillis());
        cacheDecision(item.getAuditKey(), "REJECT", item.getAuditId());
        discardHeavyPayload(item);
        rejected.incrementAndGet();
        if (countAsPending) {
            pendingCount.updateAndGet(v -> Math.max(0L, v - 1L));
        }
        return true;
    }

    @Override
    public void clear() {
        assemblyCache.clear();
        itemCache.clear();
        decisionCache.clear();
        itemOrder.clear();
        packetsSeen.set(0L);
        auditablePackets.set(0L);
        autoApproved.set(0L);
        manualApproved.set(0L);
        rejected.set(0L);
        remoteFailures.set(0L);
        pendingCount.set(0L);
        idleCompleted.set(0L);
        cachedAllowed.set(0L);
        cachedRejected.set(0L);
        duplicateSuppressed.set(0L);
        tokensRequested.set(0L);
        tokensIssued.set(0L);
        tokenFailed.set(0L);
        packetsHeld.set(0L);
        packetsReplayed.set(0L);
        localFileReadHits.set(0L);
        localFileReadMisses.set(0L);
        ackLearned.set(0L);
        ackInjected.set(0L);
        ackTemplateMissing.set(0L);
        ackTemplateFallbackUsed.set(0L);
        ackTemplateGlobalFallbackUsed.set(0L);
        contentBlocked.set(0L);
        referencedCommandsBlocked.set(0L);
        lastError = null;
        lastAuditAt = 0L;
        lastCompleteReason = null;
        lastLocalFileReadError = null;
        lastLearnedAckKey = null;
        lastMissingAckKey = null;
        ackTemplates.clear();
        recentAckCandidates.clear();
        blockedContentRefs.clear();
    }

    @Scheduled(fixedDelay = 500)
    public void completeIdleAssemblies() {
        if (!properties.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long idleMs = Math.max(500L, properties.getAssemblyIdleCompleteMs());
        for (Map.Entry<String, FileAssembly> entry : assemblyCache.entrySet()) {
            FileAssembly assembly = entry.getValue();
            if (assembly == null || assembly.getPackets().isEmpty()) {
                assemblyCache.remove(entry.getKey());
                continue;
            }
            if (now - assembly.lastPacketAt >= idleMs
                    && assemblyCache.remove(entry.getKey(), assembly)) {
                idleCompleted.incrementAndGet();
                if (isAckEnforceMode()) {
                    submitAckCompleteAudit(assembly, "ASSEMBLY_IDLE_COMPLETE");
                } else if (isTokenEnforceMode()) {
                    saveTokenAssemblyForManual(assembly, "ASSEMBLY_TIMEOUT_PENDING_MANUAL");
                } else {
                    saveIncompleteAssembly(assembly, "ASSEMBLY_TIMEOUT_AFTER_SEND");
                }
            }
        }
    }

    private PreAuditDecision assembleAndAudit(ContentAuditRelayPacket relayPacket,
                                              ParsedFilePacket parsed,
                                              String contentType) {
        String auditKey = buildAuditKey(relayPacket, parsed);
        String key = buildCacheKey(relayPacket, parsed);
        FileAssembly assembly = assemblyCache.computeIfAbsent(key,
                ignore -> new FileAssembly(parsed.filePath, parsed.fileName, contentType));
        assembly.auditKey = auditKey;
        assembly.passthroughForwarded = true;
        assembly.append(relayPacket.copy(), parsed.payload);

        if (assembly.getPackets().size() > Math.max(1, properties.getMaxBufferedPacketsPerItem())) {
            if (assemblyCache.remove(key, assembly)) {
                saveIncompleteAssembly(assembly, "BUFFER_LIMIT_EXCEEDED_AFTER_SEND");
            }
            return PreAuditDecision.pass("CONTENT_AUDIT_BUFFER_LIMIT_PASS");
        }

        if (parsed.lastPacket) {
            if (assemblyCache.remove(key, assembly)) {
                submitCompleteAudit(assembly, auditKey);
            }
            return PreAuditDecision.pass("ASSEMBLED_CONTENT_AUDITED");
        }

        return PreAuditDecision.pass("ASSEMBLING_CONTENT_PASSTHROUGH");
    }

    private PreAuditDecision enforceToken(ContentAuditRelayPacket relayPacket,
                                          ParsedFilePacket parsed,
                                          String contentType) {
        String auditKey = buildAuditKey(relayPacket, parsed);
        String key = buildCacheKey(relayPacket, parsed);
        FileAssembly activeAssembly = assemblyCache.get(key);
        DecisionCacheEntry cached = getValidDecision(auditKey);
        if (cached != null && (activeAssembly == null || !"PENDING".equalsIgnoreCase(cached.decision))) {
            if ("PASS".equalsIgnoreCase(cached.decision)) {
                if (!applyToken(relayPacket, cached.tokenId, cached.fileId)) {
                    packetsHeld.incrementAndGet();
                    return PreAuditDecision.hold("CONTENT_TOKEN_CACHE_MISSING");
                }
                cachedAllowed.incrementAndGet();
                return PreAuditDecision.pass("CONTENT_TOKEN_CACHE_PASS");
            }
            if ("REJECT".equalsIgnoreCase(cached.decision)) {
                cachedRejected.incrementAndGet();
                return PreAuditDecision.reject("CONTENT_AUDIT_CACHE_REJECT");
            }
            packetsHeld.incrementAndGet();
            return PreAuditDecision.hold("CONTENT_AUDIT_PENDING");
        }

        byte[] localFile = readLocalFileBytes(parsed.filePath, contentType);
        if (localFile != null) {
            FileAssembly assembly = new FileAssembly(parsed.filePath, parsed.fileName, contentType);
            assembly.auditKey = auditKey;
            assembly.passthroughForwarded = false;
            assembly.append(relayPacket.copy(), localFile);
            return completeAndEvaluateForToken(assembly, "LOCAL_FILE_COMPLETE", auditKey, false, relayPacket);
        }

        FileAssembly assembly = assemblyCache.computeIfAbsent(key,
                ignore -> new FileAssembly(parsed.filePath, parsed.fileName, contentType));
        assembly.auditKey = auditKey;
        assembly.passthroughForwarded = false;
        assembly.append(relayPacket.copy(), parsed.payload);
        packetsHeld.incrementAndGet();

        if (assembly.getPackets().size() > Math.max(1, properties.getMaxBufferedPacketsPerItem())) {
            if (assemblyCache.remove(key, assembly)) {
                saveTokenAssemblyForManual(assembly, "BUFFER_LIMIT_EXCEEDED_PENDING_MANUAL");
            }
            return PreAuditDecision.hold("BUFFER_LIMIT_EXCEEDED_PENDING_MANUAL");
        }

        if (parsed.lastPacket && assemblyCache.remove(key, assembly)) {
            return completeAndEvaluateForToken(assembly, "PACKET_LAST_COMPLETE", auditKey, true, null);
        }

        return PreAuditDecision.hold("ASSEMBLING_CONTENT_WAIT_TOKEN");
    }

    private PreAuditDecision enforceAck(ContentAuditRelayPacket relayPacket,
                                        ParsedFilePacket parsed,
                                        String contentType) {
        String auditKey = buildAuditKey(relayPacket, parsed);
        String key = buildCacheKey(relayPacket, parsed);
        FileAssembly assembly = assemblyCache.computeIfAbsent(key,
                ignore -> new FileAssembly(parsed.filePath, parsed.fileName, contentType));
        assembly.auditKey = auditKey;
        assembly.passthroughForwarded = false;
        assembly.append(relayPacket.copy(), parsed.payload);
        packetsHeld.incrementAndGet();
        int packetIndex = assembly.getPackets().size();

        byte[] ackPayload = findAckPayload(relayPacket, parsed, packetIndex);
        if (ackPayload == null) {
            ackTemplateMissing.incrementAndGet();
            lastError = "ACK_TEMPLATE_MISSING";
            if (assembly.getPackets().size() > Math.max(1, properties.getMaxBufferedPacketsPerItem())) {
                assemblyCache.remove(key, assembly);
                blockAssembly(assembly, "ACK_TEMPLATE_MISSING_BUFFER_LIMIT");
            }
            return PreAuditDecision.hold("ACK_TEMPLATE_MISSING");
        }
        ackInjected.incrementAndGet();

        if (assembly.getPackets().size() > Math.max(1, properties.getMaxBufferedPacketsPerItem())) {
            if (assemblyCache.remove(key, assembly)) {
                blockAssembly(assembly, "BUFFER_LIMIT_EXCEEDED_BLOCKED");
            }
            return PreAuditDecision.holdWithAck("BUFFER_LIMIT_EXCEEDED_BLOCKED", ackPayload);
        }

        if (parsed.lastPacket && assemblyCache.remove(key, assembly)) {
            submitAckCompleteAudit(assembly, "PACKET_LAST_COMPLETE");
        }
        return PreAuditDecision.holdWithAck("ACK_SIMULATED_WAIT_AUDIT", ackPayload);
    }

    private void submitCompleteAudit(FileAssembly assembly, String auditKey) {
        try {
            auditExecutor.submit(() -> {
                try {
                    completeAndEvaluate(assembly, "PACKET_LAST_COMPLETE", auditKey);
                } catch (Throwable t) {
                    lastError = t.getMessage();
                    log.warn("[ContentPreAudit] async complete audit failed: {}", t.getMessage());
                    saveIncompleteAssembly(assembly, "ASYNC_AUDIT_ERROR_AFTER_SEND");
                }
            });
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            log.warn("[ContentPreAudit] submit async audit failed: {}", e.getMessage());
            saveIncompleteAssembly(assembly, "ASYNC_AUDIT_SUBMIT_FAILED_AFTER_SEND");
        }
    }

    private void submitAckCompleteAudit(FileAssembly assembly, String completeReason) {
        try {
            auditExecutor.submit(() -> {
                try {
                    completeAndEvaluateForAck(assembly, completeReason);
                } catch (Throwable t) {
                    lastError = t.getMessage();
                    log.warn("[ContentPreAudit] ACK-mode audit failed: {}", t.getMessage(), t);
                    blockAssembly(assembly, "ASYNC_ACK_AUDIT_ERROR_BLOCKED");
                }
            });
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            log.warn("[ContentPreAudit] submit ACK-mode audit failed: {}", e.getMessage());
            blockAssembly(assembly, "ASYNC_ACK_AUDIT_SUBMIT_FAILED_BLOCKED");
        }
    }

    private void completeAndEvaluateForAck(FileAssembly assembly, String completeReason) {
        if (assembly == null || assembly.getPackets().isEmpty()) {
            return;
        }
        ContentAuditRelayPacket relayPacket = assembly.getLastPacket();
        if (relayPacket == null) {
            return;
        }
        ContentAuditItem item = buildItemFromAssembly(assembly, relayPacket);
        item.setAuditKey(assembly.auditKey);
        item.setPassthroughForwarded(false);
        item.setForwarded(false);
        item.setCompletedAt(System.currentTimeMillis());
        item.setFileHash(sha256Hex(assembly.getData()));
        item.setFileSize(assembly.getDataSize());
        item.setTotalPackets(assembly.getPackets().size());
        item.setReason(completeReason);
        lastCompleteReason = completeReason;

        if (!isFileSizeAllowed(item.getContentType(), item.getFileSize())) {
            blockItem(item, "FILE_SIZE_EXCEEDED_BLOCKED");
            return;
        }

        RemoteAuditResult remoteResult = callRemoteAudit(item);
        lastAuditAt = System.currentTimeMillis();
        if (remoteResult.error) {
            remoteFailures.incrementAndGet();
            handleAckFailPolicy(item, "REMOTE_AUDIT_ERROR");
            return;
        }

        item.setAuditCode(remoteResult.code);
        item.setAuditMessage(remoteResult.message);
        item.setRemoteDecision(remoteResult.decision);
        item.setRemoteReason(remoteResult.reason);
        item.setRiskLevel(remoteResult.riskLevel);

        if ("REJECT".equalsIgnoreCase(remoteResult.decision)) {
            blockItem(item, "MODEL_REJECTED");
            return;
        }
        if ("NEED_MANUAL".equalsIgnoreCase(remoteResult.decision)) {
            blockItem(item, "MODEL_NEED_MANUAL_BLOCKED");
            return;
        }

        if (!forwardBufferedPackets(item)) {
            blockItem(item, "ACK_REPLAY_FAILED_BLOCKED");
            return;
        }
        item.setStatus("AUTO_APPROVED");
        item.setDecision("PASS");
        item.setReason("MODEL_APPROVED_REPLAYED");
        item.setForwarded(true);
        item.setUpdatedAt(System.currentTimeMillis());
        saveItem(item);
        cacheDecision(item.getAuditKey(), "PASS", item.getAuditId());
        autoApproved.incrementAndGet();
        log.info("[ContentPreAudit] ACK-mode content approved and replayed: file={}, packets={}, bytes={}",
                item.getFileName(), item.getTotalPackets(), item.getFileSize());
    }

    private void handleAckFailPolicy(ContentAuditItem item, String reasonCode) {
        String policy = safeLower(properties.getFailPolicy());
        if ("allow".equals(policy)) {
            if (forwardBufferedPackets(item)) {
                item.setStatus("AUTO_APPROVED");
                item.setDecision("PASS");
                item.setReason(reasonCode + "_FAIL_OPEN_REPLAYED");
                item.setForwarded(true);
                item.setUpdatedAt(System.currentTimeMillis());
                saveItem(item);
                cacheDecision(item.getAuditKey(), "PASS", item.getAuditId());
                autoApproved.incrementAndGet();
                return;
            }
            reasonCode = reasonCode + "_FAIL_OPEN_REPLAY_FAILED";
        }
        blockItem(item, reasonCode + "_BLOCKED");
    }

    private void blockAssembly(FileAssembly assembly, String reason) {
        if (assembly == null || assembly.getPackets().isEmpty()) {
            return;
        }
        ContentAuditRelayPacket relayPacket = assembly.getLastPacket();
        if (relayPacket == null) {
            return;
        }
        ContentAuditItem item = buildItemFromAssembly(assembly, relayPacket);
        item.setAuditKey(assembly.auditKey);
        item.setCompletedAt(System.currentTimeMillis());
        item.setFileHash(sha256Hex(assembly.getData()));
        item.setFileSize(assembly.getDataSize());
        item.setTotalPackets(assembly.getPackets().size());
        blockItem(item, reason);
    }

    private void blockItem(ContentAuditItem item, String reason) {
        if (item == null) {
            return;
        }
        item.setStatus("REJECTED");
        item.setDecision("REJECT");
        item.setReason(reason);
        item.setForwarded(false);
        item.setUpdatedAt(System.currentTimeMillis());
        saveItem(item);
        cacheDecision(item.getAuditKey(), "REJECT", item.getAuditId());
        rememberBlockedContentReference(item);
        rejected.incrementAndGet();
        contentBlocked.incrementAndGet();
        lastCompleteReason = reason;
        log.warn("[ContentPreAudit] content blocked: file={}, packets={}, bytes={}, reason={}",
                item.getFileName(), item.getTotalPackets(), item.getFileSize(), reason);
    }

    private PreAuditDecision completeAndEvaluate(FileAssembly assembly, String completeReason) {
        return completeAndEvaluate(assembly, completeReason, assembly != null ? assembly.auditKey : null);
    }

    private PreAuditDecision completeAndEvaluate(FileAssembly assembly, String completeReason, String auditKey) {
        if (assembly == null || assembly.getPackets().isEmpty()) {
            return PreAuditDecision.pass("EMPTY_ASSEMBLY");
        }
        ContentAuditRelayPacket relayPacket = assembly.getLastPacket();
        if (relayPacket == null) {
            return PreAuditDecision.pass("EMPTY_ASSEMBLY_PACKET");
        }
        ContentAuditItem item = buildItemFromAssembly(assembly, relayPacket);
        item.setAuditKey(auditKey);
        item.setPassthroughForwarded(assembly.passthroughForwarded);
        item.setCompletedAt(System.currentTimeMillis());
        item.setFileHash(sha256Hex(assembly.getData()));
        item.setFileSize(assembly.getData().length);
        item.setTotalPackets(assembly.getPackets().size());
        item.setReason(completeReason);
        lastCompleteReason = completeReason;

        if (!isFileSizeAllowed(item.getContentType(), item.getFileSize())) {
            handleByFailPolicy(item, "FILE_SIZE_EXCEEDED");
            return PreAuditDecision.hold(item.getReason());
        }

        cacheDecision(auditKey,
                item.isPassthroughForwarded() ? "AUDITING_PASS" : "PENDING",
                item.getAuditId());
        RemoteAuditResult remoteResult = callRemoteAudit(item);
        lastAuditAt = System.currentTimeMillis();
        if (remoteResult.error) {
            remoteFailures.incrementAndGet();
            if (item.isPassthroughForwarded()) {
                item.setStatus("AUDIT_FAILED_AFTER_SEND");
                item.setDecision("UNKNOWN");
                item.setReason("REMOTE_AUDIT_ERROR_AFTER_SEND");
                item.setUpdatedAt(System.currentTimeMillis());
                saveItem(item);
                cacheDecision(auditKey, "PASSTHROUGH", item.getAuditId());
                return PreAuditDecision.pass("REMOTE_AUDIT_ERROR_AFTER_SEND");
            }
            handleByFailPolicy(item, "REMOTE_AUDIT_ERROR");
            return "REJECTED".equalsIgnoreCase(item.getStatus())
                    ? PreAuditDecision.reject(item.getReason())
                    : PreAuditDecision.hold(item.getReason());
        }

        item.setAuditCode(remoteResult.code);
        item.setAuditMessage(remoteResult.message);
        item.setRemoteDecision(remoteResult.decision);
        item.setRemoteReason(remoteResult.reason);
        item.setRiskLevel(remoteResult.riskLevel);

        if ("REJECT".equalsIgnoreCase(remoteResult.decision)) {
            item.setStatus(item.isPassthroughForwarded() ? "REJECTED_AFTER_SEND" : "REJECTED");
            item.setDecision("REJECT");
            item.setReason(item.isPassthroughForwarded() ? "MODEL_REJECTED_AFTER_SEND" : "MODEL_REJECTED");
            item.setUpdatedAt(System.currentTimeMillis());
            saveItem(item);
            cacheDecision(auditKey, item.isPassthroughForwarded() ? "PASSTHROUGH" : "REJECT", item.getAuditId());
            rejected.incrementAndGet();
            return item.isPassthroughForwarded()
                    ? PreAuditDecision.pass("MODEL_REJECTED_AFTER_SEND")
                    : PreAuditDecision.reject("MODEL_REJECTED");
        }

        if ("NEED_MANUAL".equalsIgnoreCase(remoteResult.decision)) {
            item.setStatus(item.isPassthroughForwarded() ? "PENDING_MANUAL_AFTER_SEND" : "PENDING_MANUAL");
            item.setDecision("MANUAL");
            item.setReason(item.isPassthroughForwarded() ? "MODEL_NEED_MANUAL_AFTER_SEND" : "MODEL_NEED_MANUAL");
            item.setUpdatedAt(System.currentTimeMillis());
            saveItem(item);
            cacheDecision(auditKey, item.isPassthroughForwarded() ? "PASSTHROUGH" : "PENDING", item.getAuditId());
            pendingCount.incrementAndGet();
            return item.isPassthroughForwarded()
                    ? PreAuditDecision.pass("PENDING_MANUAL_AFTER_SEND")
                    : PreAuditDecision.hold("PENDING_MANUAL");
        }

        item.setStatus("AUTO_APPROVED");
        item.setDecision("PASS");
        item.setReason("MODEL_APPROVED");
        item.setUpdatedAt(System.currentTimeMillis());
        item.setForwarded(item.isPassthroughForwarded());
        saveItem(item);
        cacheDecision(auditKey, "PASS", item.getAuditId());
        autoApproved.incrementAndGet();
        return item.isPassthroughForwarded()
                ? PreAuditDecision.pass("AUTO_APPROVED_AFTER_SEND")
                : PreAuditDecision.hold("AUTO_APPROVED_PENDING_RELAY_DISABLED");
    }

    private PreAuditDecision completeAndEvaluateForToken(FileAssembly assembly,
                                                         String completeReason,
                                                         String auditKey,
                                                         boolean replayBuffered,
                                                         ContentAuditRelayPacket currentPacket) {
        if (assembly == null || assembly.getPackets().isEmpty()) {
            packetsHeld.incrementAndGet();
            return PreAuditDecision.hold("EMPTY_TOKEN_ASSEMBLY");
        }
        ContentAuditRelayPacket relayPacket = currentPacket != null ? currentPacket : assembly.getLastPacket();
        if (relayPacket == null) {
            packetsHeld.incrementAndGet();
            return PreAuditDecision.hold("EMPTY_TOKEN_ASSEMBLY_PACKET");
        }
        ContentAuditItem item = buildItemFromAssembly(assembly, relayPacket);
        item.setAuditKey(auditKey);
        item.setPassthroughForwarded(false);
        item.setCompletedAt(System.currentTimeMillis());
        item.setFileHash(sha256Hex(assembly.getData()));
        item.setFileSize(assembly.getData().length);
        item.setTotalPackets(assembly.getPackets().size());
        item.setReason(completeReason);
        lastCompleteReason = completeReason;
        cacheDecision(auditKey, "PENDING", item.getAuditId());

        if (!isFileSizeAllowed(item.getContentType(), item.getFileSize())) {
            handleTokenFailPolicy(item, "FILE_SIZE_EXCEEDED");
            return tokenFailureDecision(item);
        }

        RemoteAuditResult remoteResult = callRemoteAudit(item);
        lastAuditAt = System.currentTimeMillis();
        if (remoteResult.error) {
            remoteFailures.incrementAndGet();
            handleTokenFailPolicy(item, "REMOTE_AUDIT_ERROR");
            return tokenFailureDecision(item);
        }

        item.setAuditCode(remoteResult.code);
        item.setAuditMessage(remoteResult.message);
        item.setRemoteDecision(remoteResult.decision);
        item.setRemoteReason(remoteResult.reason);
        item.setRiskLevel(remoteResult.riskLevel);

        if ("REJECT".equalsIgnoreCase(remoteResult.decision)) {
            item.setStatus("REJECTED");
            item.setDecision("REJECT");
            item.setReason("MODEL_REJECTED");
            item.setUpdatedAt(System.currentTimeMillis());
            saveItem(item);
            cacheDecision(auditKey, "REJECT", item.getAuditId());
            rejected.incrementAndGet();
            return PreAuditDecision.reject("MODEL_REJECTED");
        }

        if ("NEED_MANUAL".equalsIgnoreCase(remoteResult.decision)) {
            item.setStatus("PENDING_MANUAL");
            item.setDecision("MANUAL");
            item.setReason("MODEL_NEED_MANUAL");
            item.setUpdatedAt(System.currentTimeMillis());
            saveItem(item);
            cacheDecision(auditKey, "PENDING", item.getAuditId());
            pendingCount.incrementAndGet();
            packetsHeld.incrementAndGet();
            return PreAuditDecision.hold("PENDING_MANUAL");
        }

        ContentReleaseTokenIssueResponse token = issueToken(item, "LOW");
        if (!isTokenIssued(token)) {
            item.setTokenError(token == null ? "TOKEN_ISSUE_FAILED" : token.getError());
            handleTokenFailPolicy(item, "CONTENT_TOKEN_ISSUE_FAILED");
            return tokenFailureDecision(item);
        }

        applyToken(item, token);
        item.setStatus("AUTO_APPROVED");
        item.setDecision("PASS");
        item.setReason("MODEL_APPROVED_TOKEN_ISSUED");
        item.setUpdatedAt(System.currentTimeMillis());

        if (replayBuffered) {
            if (!forwardBufferedPackets(item)) {
                item.setTokenError("TOKEN_REPLAY_FAILED");
                handleTokenFailPolicy(item, "CONTENT_TOKEN_REPLAY_FAILED");
                return tokenFailureDecision(item);
            }
            item.setForwarded(true);
            saveItem(item);
            cacheDecision(auditKey, "PASS", item.getAuditId(),
                    item.getContentTokenId(), item.getContentFileId(), item.getTokenExpireAt());
            autoApproved.incrementAndGet();
            return PreAuditDecision.hold("TOKEN_REPLAYED");
        }

        if (!applyToken(currentPacket, item.getContentTokenId(), item.getContentFileId())) {
            item.setTokenError("TOKEN_APPLY_FAILED");
            handleTokenFailPolicy(item, "CONTENT_TOKEN_APPLY_FAILED");
            return tokenFailureDecision(item);
        }
        item.setForwarded(true);    
        saveItem(item);
        cacheDecision(auditKey, "PASS", item.getAuditId(),
                item.getContentTokenId(), item.getContentFileId(), item.getTokenExpireAt());
        autoApproved.incrementAndGet();
        return PreAuditDecision.pass("TOKEN_APPROVED");
    }

    private PreAuditDecision tokenFailureDecision(ContentAuditItem item) {
        if (item != null && "REJECTED".equalsIgnoreCase(item.getStatus())) {
            return PreAuditDecision.reject(item.getReason());
        }
        packetsHeld.incrementAndGet();
        return PreAuditDecision.hold(item == null ? "TOKEN_FAIL_PENDING" : item.getReason());
    }

    private void handleTokenFailPolicy(ContentAuditItem item, String reasonCode) {
        if (item == null) {
            return;
        }
        String policy = safeLower(properties.getFailPolicy());
        if ("reject".equals(policy)) {
            item.setStatus("REJECTED");
            item.setDecision("REJECT");
            item.setReason(reasonCode + "_FAIL_CLOSED");
            item.setUpdatedAt(System.currentTimeMillis());
            saveItem(item);
            cacheDecision(item.getAuditKey(), "REJECT", item.getAuditId());
            rejected.incrementAndGet();
            return;
        }

        item.setStatus("PENDING_MANUAL");
        item.setDecision("MANUAL");
        item.setReason(reasonCode + "_PENDING_MANUAL");
        item.setUpdatedAt(System.currentTimeMillis());
        saveItem(item);
        cacheDecision(item.getAuditKey(), "PENDING", item.getAuditId());
        pendingCount.incrementAndGet();
    }

    private void handleByFailPolicy(ContentAuditItem item, String reasonCode) {
        String policy = safeLower(properties.getFailPolicy());
        if ("allow".equals(policy)) {
            item.setStatus("AUTO_APPROVED");
            item.setDecision("PASS");
            item.setReason(reasonCode + "_FAIL_OPEN");
            item.setUpdatedAt(System.currentTimeMillis());
            item.setForwarded(item.isPassthroughForwarded());
            saveItem(item);
            cacheDecision(item.getAuditKey(), "PASS", item.getAuditId());
            autoApproved.incrementAndGet();
            return;
        }

        if ("reject".equals(policy)) {
            item.setStatus(item.isPassthroughForwarded() ? "REJECTED_AFTER_SEND" : "REJECTED");
            item.setDecision("REJECT");
            item.setReason(reasonCode + "_FAIL_CLOSED");
            item.setUpdatedAt(System.currentTimeMillis());
            saveItem(item);
            cacheDecision(item.getAuditKey(), item.isPassthroughForwarded() ? "PASSTHROUGH" : "REJECT", item.getAuditId());
            rejected.incrementAndGet();
            return;
        }

        item.setStatus(item.isPassthroughForwarded() ? "PENDING_MANUAL_AFTER_SEND" : "PENDING_MANUAL");
        item.setDecision("MANUAL");
        item.setReason(reasonCode + "_PENDING_MANUAL");
        item.setUpdatedAt(System.currentTimeMillis());
        saveItem(item);
        cacheDecision(item.getAuditKey(), item.isPassthroughForwarded() ? "PASSTHROUGH" : "PENDING", item.getAuditId());
        pendingCount.incrementAndGet();
    }

    private RemoteAuditResult callRemoteAudit(ContentAuditItem item) {
        RemoteAuditResult result = new RemoteAuditResult();
        String auditUrl = resolveAuditEndpoint(properties.getAuditUrl(), item == null ? null : item.getContentType());
        if (auditUrl == null) {
            result.error = true;
            result.message = "content-pre-audit.audit-url is empty or unsupported content type";
            lastError = result.message;
            return result;
        }

        try {
            HttpEntity<MultiValueMap<String, Object>> request = buildRemoteAuditRequest(item);
            if (request == null) {
                result.error = true;
                result.message = "content-pre-audit payload is empty or unsupported";
                lastError = result.message;
                return result;
            }

            ResponseEntity<Map> response = restTemplate.postForEntity(auditUrl, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                result.error = true;
                result.message = "HTTP_" + response.getStatusCodeValue();
                lastError = result.message;
                return result;
            }

            Map<?, ?> payload = response.getBody();
            if (payload == null) {
                result.error = true;
                result.message = "empty pre-audit response";
                return result;
            }
            Object cnCodeObj = payload.get("状态码");
            Object enCodeObj = payload.get("code");
            int code = parseInt(cnCodeObj != null ? cnCodeObj : enCodeObj, -1);
            result.code = code;
            result.message = safeString(firstValue(payload, "消息", "message", "msg"), "");
            boolean success = cnCodeObj != null ? code == 0 : code == 200;
            if (!success) {
                result.error = true;
                if (trimToNull(result.message) == null) {
                    result.message = "remote audit failed: code=" + code;
                }
                return result;
            }
            Object dataObj = firstValue(payload, "数据", "data");
            if (!(dataObj instanceof Map)) {
                result.error = true;
                result.message = "pre-audit response data is empty";
                return result;
            }
            Map<?, ?> data = (Map<?, ?>) dataObj;
            result.decision = normalizeRemoteDecision(safeString(firstValue(data, "审核结果", "decision"), "NEED_MANUAL"));
            result.riskLevel = safeString(firstValue(data, "违规等级", "riskLevel"), "");
            result.reason = buildRemoteAuditReason(data, result.decision, result.riskLevel);
            result.error = false;
            return result;
        } catch (RestClientException e) {
            result.error = true;
            result.message = e.getMessage();
            lastError = e.getMessage();
            return result;
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> buildRemoteAuditRequest(ContentAuditItem item) {
        if (item == null) {
            return null;
        }
        byte[] content = item.getFullContentBytes();
        if (content == null || content.length == 0) {
            return null;
        }
        String contentType = safeLower(item.getContentType());
        String fieldName;
        if ("image".equals(contentType)) {
            fieldName = "image";
        } else if ("text".equals(contentType)) {
            fieldName = "file";
        } else {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(fieldName, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                String fileName = trimToNull(item.getFileName());
                if (fileName != null) {
                    return fileName;
                }
                return "text".equals(contentType) ? "payload.txt" : "payload.jpg";
            }
        });
        return new HttpEntity<>(body, headers);
    }

    private String resolveAuditEndpoint(String configuredUrl, String contentType) {
        String endpointPath = auditEndpointPath(contentType);
        if (endpointPath == null) {
            return null;
        }
        String url = trimToNull(configuredUrl);
        if (url == null) {
            return null;
        }
        url = stripTrailingSlash(url);
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/api/audit/image") || lower.endsWith("/api/audit/text")) {
            return lower.endsWith(endpointPath) ? url : null;
        }
        if (lower.endsWith("/api/audit")) {
            return url + endpointPath.substring("/api/audit".length());
        }
        if (lower.endsWith("/admin")) {
            url = url.substring(0, url.length() - "/admin".length());
        } else if (lower.endsWith("/test")) {
            url = url.substring(0, url.length() - "/test".length());
        }
        return stripTrailingSlash(url) + endpointPath;
    }

    private String auditEndpointPath(String contentType) {
        String value = safeLower(contentType);
        if ("image".equals(value)) {
            return "/api/audit/image";
        }
        if ("text".equals(value)) {
            return "/api/audit/text";
        }
        return null;
    }

    private String stripTrailingSlash(String value) {
        String url = trimToNull(value);
        if (url == null) {
            return null;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private Object firstValue(Map<?, ?> data, String... keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private String normalizeRemoteDecision(String decision) {
        String value = trimToNull(decision);
        if (value == null) {
            return "NEED_MANUAL";
        }
        if ("通过".equals(value)) {
            return "PASS";
        }
        if ("复核".equals(value)) {
            return "NEED_MANUAL";
        }
        if ("阻断".equals(value)) {
            return "REJECT";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("PASS".equals(upper) || "ALLOW".equals(upper) || "APPROVED".equals(upper)) {
            return "PASS";
        }
        if ("REJECT".equals(upper) || "BLOCK".equals(upper) || "BLOCKED".equals(upper)) {
            return "REJECT";
        }
        if ("NEED_MANUAL".equals(upper) || "MANUAL".equals(upper) || "REVIEW".equals(upper)) {
            return "NEED_MANUAL";
        }
        return "NEED_MANUAL";
    }

    private String buildRemoteAuditReason(Map<?, ?> data, String decision, String riskLevel) {
        Object reason = firstValue(data, "reason", "违规原因", "消息", "message");
        String text = safeString(reason, "");
        if (trimToNull(text) != null) {
            return text;
        }
        Object detail = firstValue(data, "违规详情");
        if (detail != null) {
            return JSON.toJSONString(detail);
        }
        Object summary = firstValue(data, "统计摘要");
        if (summary != null) {
            return JSON.toJSONString(summary);
        }
        String risk = trimToNull(riskLevel);
        if (risk != null) {
            return "riskLevel=" + risk;
        }
        return decision;
    }

    private void saveIncompleteAssembly(FileAssembly assembly, String reason) {
        if (assembly == null || assembly.getPackets().isEmpty()) {
            return;
        }
        ContentAuditRelayPacket relayPacket = assembly.getLastPacket();
        if (relayPacket == null) {
            return;
        }
        ContentAuditItem item = buildItemFromAssembly(assembly, relayPacket);
        item.setAuditKey(assembly.auditKey);
        item.setPassthroughForwarded(true);
        item.setForwarded(true);
        item.setCompletedAt(System.currentTimeMillis());
        item.setFileHash(sha256Hex(assembly.getData()));
        item.setFileSize(assembly.getDataSize());
        item.setTotalPackets(assembly.getPackets().size());
        item.setStatus("PENDING_MANUAL_AFTER_SEND");
        item.setDecision("MANUAL");
        item.setReason(reason);
        item.setUpdatedAt(System.currentTimeMillis());
        saveItem(item);
        cacheDecision(item.getAuditKey(), "PASSTHROUGH", item.getAuditId());
        pendingCount.incrementAndGet();
        lastCompleteReason = reason;
        log.warn("[ContentPreAudit] incomplete assembly recorded without AI audit: file={}, packets={}, bytes={}, reason={}",
                item.getFileName(), item.getTotalPackets(), item.getFileSize(), reason);
    }

    private void saveTokenAssemblyForManual(FileAssembly assembly, String reason) {
        if (assembly == null || assembly.getPackets().isEmpty()) {
            return;
        }
        ContentAuditRelayPacket relayPacket = assembly.getLastPacket();
        if (relayPacket == null) {
            return;
        }
        ContentAuditItem item = buildItemFromAssembly(assembly, relayPacket);
        item.setAuditKey(assembly.auditKey);
        item.setPassthroughForwarded(false);
        item.setForwarded(false);
        item.setCompletedAt(System.currentTimeMillis());
        item.setFileHash(sha256Hex(assembly.getData()));
        item.setFileSize(assembly.getDataSize());
        item.setTotalPackets(assembly.getPackets().size());
        item.setStatus("PENDING_MANUAL");
        item.setDecision("MANUAL");
        item.setReason(reason);
        item.setUpdatedAt(System.currentTimeMillis());
        saveItem(item);
        cacheDecision(item.getAuditKey(), "PENDING", item.getAuditId());
        pendingCount.incrementAndGet();
        lastCompleteReason = reason;
    }

    private ContentReleaseTokenIssueResponse issueToken(ContentAuditItem item, String riskLevel) {
        tokensRequested.incrementAndGet();
        ContentReleaseTokenIssueResponse token = contentReleaseTokenClient.issue(item, "PASS", riskLevel);
        if (isTokenIssued(token)) {
            tokensIssued.incrementAndGet();
        } else {
            tokenFailed.incrementAndGet();
        }
        return token;
    }

    private boolean isTokenIssued(ContentReleaseTokenIssueResponse token) {
        return token != null
                && token.isIssued()
                && trimToNull(token.getTokenId()) != null
                && trimToNull(token.getFileId()) != null;
    }

    private void applyToken(ContentAuditItem item, ContentReleaseTokenIssueResponse token) {
        if (item == null || token == null) {
            return;
        }
        item.setContentTokenId(token.getTokenId());
        item.setContentFileId(token.getFileId());
        item.setRiskLevel(token.getMetadata() == null ? null : token.getMetadata().getRiskLevel());
        item.setTokenExpireAt(token.getMetadata() == null ? null : token.getMetadata().getExpireAt());
        item.setTokenError(null);
        synchronized (item.getBufferedPackets()) {
            for (ContentAuditRelayPacket packet : item.getBufferedPackets()) {
                applyToken(packet, item.getContentTokenId(), item.getContentFileId());
            }
        }
    }

    private boolean applyToken(ContentAuditRelayPacket packet, String tokenId, String fileId) {
        String token = trimToNull(tokenId);
        String file = trimToNull(fileId);
        if (packet == null || token == null || file == null) {
            return false;
        }
        packet.setContentTokenId(token);
        packet.setContentFileId(file);
        packet.setRelayBytes(null);
        return true;
    }

    private boolean forwardBufferedPackets(ContentAuditItem item) {
        if (item == null) {
            return false;
        }
        boolean needsToken = isTokenEnforceMode();
        if (needsToken && (trimToNull(item.getContentTokenId()) == null || trimToNull(item.getContentFileId()) == null)) {
            return false;
        }
        ContentPreAuditRelaySender sender = relaySenderProvider.getIfAvailable();
        if (sender == null) {
            lastError = "content relay sender is unavailable";
            return false;
        }
        List<ContentAuditRelayPacket> packets;
        synchronized (item.getBufferedPackets()) {
            packets = new ArrayList<>(item.getBufferedPackets());
        }
        if (packets.isEmpty()) {
            lastError = "buffered packets are empty";
            return false;
        }
        for (ContentAuditRelayPacket packet : packets) {
            if (needsToken && !applyToken(packet, item.getContentTokenId(), item.getContentFileId())) {
                return false;
            }
            if (!sender.send(packet)) {
                return false;
            }
            packetsReplayed.incrementAndGet();
        }
        return true;
    }

    private void saveItem(ContentAuditItem item) {
        if (item == null || trimToNull(item.getAuditId()) == null) {
            return;
        }
        if (!shouldKeepReplayPayload(item)) {
            discardHeavyPayload(item);
        }
        itemCache.put(item.getAuditId(), item);
        itemOrder.remove(item.getAuditId());
        itemOrder.addFirst(item.getAuditId());
    }

    private boolean shouldKeepReplayPayload(ContentAuditItem item) {
        return item != null
                && isTokenEnforceMode()
                && isPendingManualStatus(item.getStatus())
                && !item.isForwarded()
                && !item.isPassthroughForwarded();
    }

    private void discardHeavyPayload(ContentAuditItem item) {
        if (item == null) {
            return;
        }
        item.setFullContentBytes(null);
        item.getBufferedPackets().clear();
    }

    private ContentAuditItem buildItemFromAssembly(FileAssembly assembly, ContentAuditRelayPacket relayPacket) {
        ContentAuditItem item = new ContentAuditItem();
        long now = System.currentTimeMillis();
        item.setAuditId(UUID.randomUUID().toString().replace("-", ""));
        item.setContentType(assembly.contentType);
        item.setFileName(assembly.fileName);
        item.setFilePath(assembly.filePath);
        item.setSourceIp(relayPacket.getSourceIp());
        item.setSourcePort(relayPacket.getSourcePort());
        item.setTargetIp(relayPacket.getTargetIp());
        item.setTargetPort(relayPacket.getTargetPort());
        item.setPid(relayPacket.getPid());
        item.setProcessName(relayPacket.getProcessName());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.getBufferedPackets().addAll(assembly.getPackets());
        item.setFullContentBytes(assembly.getData());
        return item;
    }

    private ParsedFilePacket parseFileTransfer(byte[] udpData) {
        if (udpData == null || udpData.length < 20 || udpData[0] != SYN_BYTE1) {
            return null;
        }
        byte syn2 = udpData[1];
        if (syn2 != SYN_TYPE2_SUM && syn2 != SYN_TYPE2_CRC
                && syn2 != SYN_TYPE3_SUM && syn2 != SYN_TYPE3_CRC) {
            return null;
        }

        int sourceAddr = ByteBuffer.wrap(udpData, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        int argLen = udpData[14] & 0xFF;
        int argBytes = argLen * 4;
        int dataStartIndex = 16 + argBytes;
        if (argLen == 0 || udpData.length <= dataStartIndex) {
            return null;
        }

        byte[] args = new byte[argBytes];
        System.arraycopy(udpData, 16, args, 0, argBytes);
        String filePath = extractFilePathFromArgs(args);
        if (filePath == null) {
            return null;
        }

        int payloadLen = udpData.length - dataStartIndex;
        byte[] payload = new byte[payloadLen];
        System.arraycopy(udpData, dataStartIndex, payload, 0, payloadLen);
        int standardPayloadSize = parseStandardPayloadSize(args);

        ParsedFilePacket parsed = new ParsedFilePacket();
        parsed.sourceAddr = sourceAddr;
        parsed.filePath = filePath;
        parsed.fileName = extractFileName(filePath);
        parsed.standardPayloadSize = standardPayloadSize;
        parsed.payload = payload;
        parsed.lastPacket = payloadLen < standardPayloadSize;
        return parsed;
    }

    private String extractFilePathFromArgs(byte[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length - 3; i++) {
            byte b = args[i];
            if (b >= 'A' && b <= 'Z' && args[i + 1] == ':' && args[i + 2] == '\\') {
                int end = i;
                while (end < args.length && args[end] != 0x00) {
                    end++;
                }
                if (end > i + 3) {
                    return new String(args, i, end - i, GBK);
                }
            }
        }
        return null;
    }

    private int parseStandardPayloadSize(byte[] args) {
        if (args != null && args.length >= 6) {
            int size = ByteBuffer.wrap(args, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (size > 0 && size <= 4096) {
                return size;
            }
        }
        return DEFAULT_STANDARD_PAYLOAD_SIZE;
    }

    private String extractFileName(String filePath) {
        if (filePath == null) {
            return null;
        }
        int slash = Math.max(filePath.lastIndexOf('\\'), filePath.lastIndexOf('/'));
        if (slash >= 0 && slash < filePath.length() - 1) {
            return filePath.substring(slash + 1);
        }
        return filePath;
    }

    private String buildCacheKey(ContentAuditRelayPacket relayPacket, ParsedFilePacket parsed) {
        return relayPacket.getTargetIp() + ":" + relayPacket.getTargetPort()
                + "_" + parsed.sourceAddr + "_" + parsed.filePath;
    }

    private void rememberAckCandidate(ContentAuditRelayPacket relayPacket, ParsedFilePacket parsed) {
        if (relayPacket == null || parsed == null || !properties.isAckLearningEnabled()) {
            return;
        }
        String ackKey = buildAckKey(relayPacket, parsed);
        if (ackKey == null) {
            return;
        }
        recentAckCandidates.put(flowKey(relayPacket.getSourceIp(), relayPacket.getSourcePort(),
                        relayPacket.getTargetIp(), relayPacket.getTargetPort()),
                new AckCandidate(ackKey, ackDescription(relayPacket, parsed), System.currentTimeMillis()));
        cleanupAckState();
    }

    private byte[] findAckPayload(ContentAuditRelayPacket relayPacket, ParsedFilePacket parsed, int packetIndex) {
        if (!properties.isAckSimulationEnabled()) {
            return null;
        }
        String ackKey = buildAckKey(relayPacket, parsed);
        if (ackKey == null) {
            return null;
        }
        AckTemplate template = ackTemplates.get(ackKey);
        if (template == null || !template.hasPayload()) {
            template = findFallbackAckTemplate(ackKey);
            if (template == null || !template.hasPayload()) {
                lastMissingAckKey = ackKey;
                return null;
            }
            ackTemplateFallbackUsed.incrementAndGet();
        }
        template.lastUsedAt = System.currentTimeMillis();
        byte[] payload = template.payloadForIndex(packetIndex);
        if (payload == null || payload.length == 0) {
            lastMissingAckKey = ackKey;
            return null;
        }
        return payload;
    }

    private AckTemplate findFallbackAckTemplate(String ackKey) {
        String key = trimToNull(ackKey);
        if (key == null || ackTemplates.isEmpty()) {
            return null;
        }
        AckTemplate template = findAckTemplateByPrefix(prefixBefore(key, "|flag="));
        if (template != null) {
            return template;
        }
        template = findAckTemplateByPrefix(prefixBefore(key, "|cmd="));
        if (template != null) {
            return template;
        }
        template = findAckTemplateByPrefix(prefixBefore(key, "|syn="));
        if (template != null) {
            return template;
        }
        template = findOnlyAckTemplate();
        if (template != null) {
            ackTemplateGlobalFallbackUsed.incrementAndGet();
            log.warn("[ContentPreAudit] using single ACK template fallback, missingKey={}, learnedKey={}",
                    key, lastLearnedAckKey);
            return template;
        }
        return null;
    }

    private AckTemplate findOnlyAckTemplate() {
        if (ackTemplates.size() != 1) {
            return null;
        }
        for (AckTemplate template : ackTemplates.values()) {
            if (template != null && template.hasPayload()) {
                return template;
            }
        }
        return null;
    }

    private AckTemplate findAckTemplateByPrefix(String prefix) {
        String value = trimToNull(prefix);
        if (value == null) {
            return null;
        }
        for (Map.Entry<String, AckTemplate> entry : ackTemplates.entrySet()) {
            AckTemplate template = entry.getValue();
            if (entry.getKey() != null && entry.getKey().startsWith(value)
                    && template != null && template.hasPayload()) {
                return template;
            }
        }
        return null;
    }

    private String prefixBefore(String value, String marker) {
        String text = trimToNull(value);
        String token = trimToNull(marker);
        if (text == null || token == null) {
            return null;
        }
        int index = text.indexOf(token);
        return index > 0 ? text.substring(0, index) : null;
    }

    private List<String> ackTemplateKeys() {
        List<String> keys = new ArrayList<>(ackTemplates.keySet());
        keys.sort(String::compareTo);
        if (keys.size() > 10) {
            return new ArrayList<>(keys.subList(0, 10));
        }
        return keys;
    }

    private Map<String, Integer> ackTemplatePayloadCounts() {
        Map<String, Integer> counts = new HashMap<>();
        List<String> keys = ackTemplateKeys();
        for (String key : keys) {
            AckTemplate template = ackTemplates.get(key);
            counts.put(key, template == null ? 0 : template.payloadCount());
        }
        return counts;
    }

    private String buildAckKey(ContentAuditRelayPacket relayPacket, ParsedFilePacket parsed) {
        byte[] data = relayPacket == null ? null : relayPacket.getPayload();
        if (data == null || data.length < 16 || parsed == null) {
            return null;
        }
        int syn2 = data[1] & 0xFF;
        int groupAddr = data[8] & 0xFF;
        int unitAddr = data[9] & 0xFF;
        int mainCmd = data[12] & 0xFF;
        int subCmd = data[13] & 0xFF;
        int flag = data[15] & 0xFF;
        return relayPacket.getTargetIp() + ":" + relayPacket.getTargetPort()
                + "|syn=" + syn2
                + "|srcAddr=" + parsed.sourceAddr
                + "|dst=" + groupAddr + ":" + unitAddr
                + "|cmd=" + mainCmd + ":" + subCmd
                + "|flag=" + flag;
    }

    private String ackDescription(ContentAuditRelayPacket relayPacket, ParsedFilePacket parsed) {
        return relayPacket.getSourceIp() + ":" + relayPacket.getSourcePort()
                + "->" + relayPacket.getTargetIp() + ":" + relayPacket.getTargetPort()
                + ", file=" + parsed.fileName;
    }

    private String flowKey(String sourceIp, int sourcePort, String targetIp, int targetPort) {
        return safeString(sourceIp, "") + ":" + sourcePort + "->" + safeString(targetIp, "") + ":" + targetPort;
    }

    private void cleanupAckState() {
        long expiredBefore = System.currentTimeMillis() - Math.max(60000L, properties.getDecisionCacheTtlMs());
        for (Map.Entry<String, AckCandidate> entry : recentAckCandidates.entrySet()) {
            AckCandidate candidate = entry.getValue();
            if (candidate == null || candidate.createdAt < expiredBefore) {
                recentAckCandidates.remove(entry.getKey(), candidate);
            }
        }
        for (Map.Entry<String, AckTemplate> entry : ackTemplates.entrySet()) {
            AckTemplate template = entry.getValue();
            if (template == null || Math.max(template.createdAt, template.lastUsedAt) < expiredBefore) {
                ackTemplates.remove(entry.getKey(), template);
            }
        }
    }

    private String resolveContentType(String fileName) {
        String extension = extensionOf(fileName);
        if (extension == null) {
            return null;
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        return null;
    }

    private boolean isFileSizeAllowed(String contentType, long bytes) {
        if (bytes < 0) {
            return false;
        }
        long bytesLimit;
        if ("video".equalsIgnoreCase(contentType)) {
            bytesLimit = Math.max(1L, properties.getMaxVideoSizeMb()) * 1024L * 1024L;
            return bytes <= bytesLimit;
        }
        if ("image".equalsIgnoreCase(contentType)) {
            bytesLimit = Math.max(1L, properties.getMaxImageSizeMb()) * 1024L * 1024L;
            return bytes <= bytesLimit;
        }
        return true;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index >= fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data == null ? new byte[0] : data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void cleanupExpiredItems() {
        long now = System.currentTimeMillis();
        long ttl = Math.max(60000L, properties.getDecisionCacheTtlMs());
        long pendingTimeout = Math.max(ttl, properties.getPendingTimeoutMs());

        for (Map.Entry<String, ContentAuditItem> entry : itemCache.entrySet()) {
            ContentAuditItem item = entry.getValue();
            if (item == null) {
                itemCache.remove(entry.getKey());
                continue;
            }
            long age = now - item.getUpdatedAt();
            if (isPendingManualStatus(item.getStatus())) {
                if (age > pendingTimeout) {
                    pendingCount.updateAndGet(v -> Math.max(0L, v - 1L));
                    itemCache.remove(entry.getKey());
                    itemOrder.remove(entry.getKey());
                    removeDecision(item.getAuditKey());
                }
                continue;
            }
            if (age > ttl) {
                itemCache.remove(entry.getKey());
                itemOrder.remove(entry.getKey());
                removeDecision(item.getAuditKey());
            }
        }
        cleanupBlockedContentReferences(now);
        for (Map.Entry<String, DecisionCacheEntry> entry : decisionCache.entrySet()) {
            DecisionCacheEntry cached = entry.getValue();
            if (cached == null || cached.isExpired(now)) {
                decisionCache.remove(entry.getKey(), cached);
            }
        }
    }

    private void rememberBlockedContentReference(ContentAuditItem item) {
        if (item == null) {
            return;
        }
        String targetKey = targetKey(item.getTargetIp(), item.getTargetPort());
        long ttl = Math.max(60000L, Math.max(properties.getDecisionCacheTtlMs(), properties.getPendingTimeoutMs()));
        long expiresAt = System.currentTimeMillis() + ttl;
        putBlockedContentReference(targetKey, normalizePath(item.getFilePath()), expiresAt);
        String fileName = normalizePath(item.getFileName());
        if (fileName.isEmpty()) {
            fileName = normalizePath(extractFileName(item.getFilePath()));
        }
        if (fileName.length() >= 4) {
            putBlockedContentReference(targetKey, fileName, expiresAt);
        }
    }

    private void putBlockedContentReference(String targetKey, String ref, long expiresAt) {
        String key = trimToNull(targetKey);
        String value = trimToNull(ref);
        if (key == null || value == null) {
            return;
        }
        blockedContentRefs.put(key + "|" + value, expiresAt);
    }

    private String findBlockedContentReference(ContentAuditRelayPacket relayPacket) {
        if (relayPacket == null || relayPacket.getPayload() == null || blockedContentRefs.isEmpty()) {
            return null;
        }
        cleanupBlockedContentReferences(System.currentTimeMillis());
        String text = new String(relayPacket.getPayload(), GBK)
                .replace('/', '\\')
                .toLowerCase(Locale.ROOT);
        String prefix = targetKey(relayPacket.getTargetIp(), relayPacket.getTargetPort()) + "|";
        for (Map.Entry<String, Long> entry : blockedContentRefs.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix)) {
                continue;
            }
            String ref = key.substring(prefix.length());
            if (ref.length() >= 4 && text.contains(ref)) {
                return ref;
            }
        }
        return null;
    }

    private void cleanupBlockedContentReferences(long now) {
        for (Map.Entry<String, Long> entry : blockedContentRefs.entrySet()) {
            Long expiresAt = entry.getValue();
            if (expiresAt == null || expiresAt <= now) {
                blockedContentRefs.remove(entry.getKey(), expiresAt);
            }
        }
    }

    private void cleanupStaleAssemblies() {
        long now = System.currentTimeMillis();
        long stale = Math.max(Math.max(10000L, properties.getPendingTimeoutMs()),
                Math.max(500L, properties.getAssemblyIdleCompleteMs()) * 5);
        for (Map.Entry<String, FileAssembly> entry : assemblyCache.entrySet()) {
            FileAssembly assembly = entry.getValue();
            if (assembly == null) {
                assemblyCache.remove(entry.getKey());
                continue;
            }
            if (now - assembly.lastPacketAt > stale) {
                assemblyCache.remove(entry.getKey());
            }
        }
    }

    private byte[] readLocalFileBytes(String filePath, String contentType) {
        String path = trimToNull(filePath);
        if (path == null) {
            localFileReadMisses.incrementAndGet();
            lastLocalFileReadError = "LOCAL_FILE_PATH_EMPTY";
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            localFileReadMisses.incrementAndGet();
            lastLocalFileReadError = "LOCAL_FILE_NOT_FOUND: " + path;
            return null;
        }
        long length = file.length();
        if (length <= 0 || length > Integer.MAX_VALUE || !isFileSizeAllowed(contentType, length)) {
            localFileReadMisses.incrementAndGet();
            lastLocalFileReadError = "LOCAL_FILE_SIZE_NOT_ALLOWED: " + path + ", size=" + length;
            return null;
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(length, 1024 * 1024))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            localFileReadHits.incrementAndGet();
            lastLocalFileReadError = null;
            return output.toByteArray();
        } catch (IOException e) {
            localFileReadMisses.incrementAndGet();
            lastLocalFileReadError = "LOCAL_FILE_READ_FAILED: " + path + ", error=" + e.getMessage();
            log.debug("[ContentPreAudit] local file read skipped: path={}, error={}", filePath, e.getMessage());
            return null;
        }
    }

    private boolean isTokenEnforceMode() {
        return properties.isTokenRequired()
                || "enforce-token".equalsIgnoreCase(trimToNull(properties.getMode()));
    }

    private boolean isAckEnforceMode() {
        // ACK simulation/learning was retired because content detection moved before signing.
        return false;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String safeString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.trim().isEmpty() ? defaultValue : text;
    }

    private String safeLower(String text) {
        String value = trimToNull(text);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeRuntimeMode(String mode) {
        String value = safeLower(mode);
        if ("audit".equals(value)
                || "enforce".equals(value)
                || "enforce-token".equals(value)
                || "enforce-ack".equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("unsupported content-pre-audit mode: " + mode);
    }

    private String normalizeFailPolicy(String failPolicy) {
        String value = safeLower(failPolicy);
        if ("manual".equals(value) || "reject".equals(value) || "allow".equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("unsupported content-pre-audit fail-policy: " + failPolicy);
    }

    private String stringSetting(Map<String, Object> settings, String... names) {
        Object value = settingValue(settings, names);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean booleanSetting(Map<String, Object> settings, String... names) {
        Object value = settingValue(settings, names);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("invalid boolean value: " + text);
    }

    private Long longSetting(Map<String, Object> settings, String... names) {
        Object value = settingValue(settings, names);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid long value: " + text);
        }
    }

    private Object settingValue(Map<String, Object> settings, String... names) {
        if (settings == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (name != null && settings.containsKey(name)) {
                return settings.get(name);
            }
        }
        return null;
    }

    private boolean isPendingManualStatus(String status) {
        return "PENDING_MANUAL".equalsIgnoreCase(status)
                || "PENDING_MANUAL_AFTER_SEND".equalsIgnoreCase(status);
    }

    private String buildAuditKey(ContentAuditRelayPacket relayPacket, ParsedFilePacket parsed) {
        String filePath = parsed != null ? parsed.filePath : "";
        String sourceIp = relayPacket != null ? relayPacket.getSourceIp() : "";
        String targetIp = relayPacket != null ? relayPacket.getTargetIp() : "";
        int targetPort = relayPacket != null ? relayPacket.getTargetPort() : 0;
        return sourceIp + "->" + targetIp + ":" + targetPort + "|" + normalizePath(filePath);
    }

    private String normalizePath(String filePath) {
        String value = trimToNull(filePath);
        return value == null ? "" : value.replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private String targetKey(String targetIp, int targetPort) {
        return safeString(targetIp, "") + ":" + targetPort;
    }

    private DecisionCacheEntry getValidDecision(String auditKey) {
        String key = trimToNull(auditKey);
        if (key == null) {
            return null;
        }
        DecisionCacheEntry cached = decisionCache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.isExpired(System.currentTimeMillis())) {
            decisionCache.remove(key, cached);
            return null;
        }
        return cached;
    }

    private void cacheDecision(String auditKey, String decision, String auditId) {
        cacheDecision(auditKey, decision, auditId, null, null, null);
    }

    private void cacheDecision(String auditKey,
                               String decision,
                               String auditId,
                               String tokenId,
                               String fileId,
                               Long tokenExpireAt) {
        String key = trimToNull(auditKey);
        String value = trimToNull(decision);
        if (key == null || value == null) {
            return;
        }
        long ttl = Math.max(60000L, properties.getDecisionCacheTtlMs());
        if ("PENDING".equalsIgnoreCase(value)) {
            ttl = Math.max(ttl, properties.getPendingTimeoutMs());
        }
        long expiresAt = System.currentTimeMillis() + ttl;
        if (tokenExpireAt != null && tokenExpireAt > 0) {
            expiresAt = Math.min(expiresAt, tokenExpireAt);
        }
        decisionCache.put(key, new DecisionCacheEntry(value, auditId, expiresAt, tokenId, fileId));
    }

    private void removeDecision(String auditKey) {
        String key = trimToNull(auditKey);
        if (key != null) {
            decisionCache.remove(key);
        }
    }

    private List<Map<String, Object>> activeAssemblyDetails() {
        List<Map<String, Object>> details = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (FileAssembly assembly : assemblyCache.values()) {
            if (assembly == null) {
                continue;
            }
            Map<String, Object> detail = new HashMap<>();
            detail.put("fileName", assembly.fileName);
            detail.put("contentType", assembly.contentType);
            detail.put("packets", assembly.getPackets().size());
            detail.put("bytes", assembly.getDataSize());
            detail.put("idleMs", now - assembly.lastPacketAt);
            details.add(detail);
        }
        return details;
    }

    private static class AckTemplate {
        private static final int MAX_PAYLOADS = 256;
        private final List<byte[]> payloads = Collections.synchronizedList(new ArrayList<>());
        private final long createdAt;
        private final String description;
        private volatile long lastUsedAt;

        private AckTemplate(long createdAt, String description) {
            this.createdAt = createdAt;
            this.description = description;
            this.lastUsedAt = createdAt;
        }

        private void addPayload(byte[] payload) {
            if (payload == null || payload.length == 0) {
                return;
            }
            synchronized (payloads) {
                if (payloads.size() >= MAX_PAYLOADS) {
                    payloads.remove(0);
                }
                payloads.add(payload.clone());
            }
        }

        private boolean hasPayload() {
            synchronized (payloads) {
                return !payloads.isEmpty();
            }
        }

        private byte[] payloadForIndex(int packetIndex) {
            synchronized (payloads) {
                if (payloads.isEmpty()) {
                    return null;
                }
                int index = Math.max(0, packetIndex - 1);
                if (index >= payloads.size()) {
                    index = payloads.size() - 1;
                }
                return payloads.get(index).clone();
            }
        }

        private int payloadCount() {
            synchronized (payloads) {
                return payloads.size();
            }
        }
    }

    private static class AckCandidate {
        private final String ackKey;
        private final String description;
        private final long createdAt;

        private AckCandidate(String ackKey, String description, long createdAt) {
            this.ackKey = ackKey;
            this.description = description;
            this.createdAt = createdAt;
        }
    }

    private static class ParsedFilePacket {
        private int sourceAddr;
        private String filePath;
        private String fileName;
        private int standardPayloadSize;
        private byte[] payload;
        private boolean lastPacket;
    }

    private static class FileAssembly {
        private final String filePath;
        private final String fileName;
        private final String contentType;
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final List<ContentAuditRelayPacket> packets = Collections.synchronizedList(new ArrayList<>());
        private volatile long lastPacketAt = System.currentTimeMillis();
        private volatile String auditKey;
        private volatile boolean passthroughForwarded;

        private FileAssembly(String filePath, String fileName, String contentType) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.contentType = contentType;
        }

        private void append(ContentAuditRelayPacket packet, byte[] payload) {
            if (packet != null) {
                packets.add(packet);
            }
            if (payload != null && payload.length > 0) {
                data.write(payload, 0, payload.length);
            }
            lastPacketAt = System.currentTimeMillis();
        }

        private List<ContentAuditRelayPacket> getPackets() {
            return packets;
        }

        private byte[] getData() {
            return data.toByteArray();
        }

        private int getDataSize() {
            return data.size();
        }

        private ContentAuditRelayPacket getLastPacket() {
            synchronized (packets) {
                if (packets.isEmpty()) {
                    return null;
                }
                return packets.get(packets.size() - 1);
            }
        }
    }

    private static class RemoteAuditResult {
        private int code;
        private String message;
        private String decision = "NEED_MANUAL";
        private String reason;
        private String riskLevel;
        private boolean error;
    }

    private static class DecisionCacheEntry {
        private final String decision;
        private final String auditId;
        private final long expiresAt;
        private final String tokenId;
        private final String fileId;

        private DecisionCacheEntry(String decision, String auditId, long expiresAt) {
            this(decision, auditId, expiresAt, null, null);
        }

        private DecisionCacheEntry(String decision, String auditId, long expiresAt, String tokenId, String fileId) {
            this.decision = decision;
            this.auditId = auditId;
            this.expiresAt = expiresAt;
            this.tokenId = tokenId;
            this.fileId = fileId;
        }

        private boolean isExpired(long now) {
            return expiresAt > 0 && now > expiresAt;
        }
    }
}
