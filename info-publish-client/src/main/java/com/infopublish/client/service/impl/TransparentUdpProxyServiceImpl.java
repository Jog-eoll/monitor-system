package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.TransparentProxyProperties;
import com.infopublish.client.config.AppConfig.WindivertProperties;
import com.infopublish.client.config.AppConfig.ContentPreAuditProperties;
import com.infopublish.client.entity.dto.ContentAuditRelayPacket;
import com.infopublish.client.service.ContentPreAuditRelaySender;
import com.infopublish.client.service.ContentPreAuditService;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.ProcessInfoResolver;
import com.infopublish.client.service.ProcessPolicyEngine;
import com.infopublish.client.service.RelayPacketCodec;
import com.infopublish.client.service.TransparentUdpProxyService;
import com.infopublish.client.service.UkeyLifecycleManager;
import com.infopublish.client.service.WindivertMonitorService;
import com.infopublish.client.service.ClientRelayFileSignatureService;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.net.SocketException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 3 transparent UDP takeover.
 */
@Slf4j
@Service
public class TransparentUdpProxyServiceImpl
        implements TransparentUdpProxyService, ApplicationRunner, Ordered, ContentPreAuditRelaySender {

    private static final int WINDIVERT_LAYER_NETWORK = 0;
    private static final int WINDIVERT_PARAM_QUEUE_LENGTH = 0;
    private static final int WINDIVERT_PARAM_QUEUE_TIME = 1;
    private static final int WINDIVERT_EVENT_NETWORK_PACKET = 0;
    private static final int ADDRESS_SIZE = 128;
    private static final int IPPROTO_UDP = 17;
    private static final int MAX_PACKET_SIZE = 65575;

    private interface WinDivertLibrary extends Library {
        Pointer WinDivertOpen(String filter, int layer, short priority, long flags);
        boolean WinDivertRecv(Pointer handle, Pointer packet, int packetLen, Pointer recvLen, Pointer addr);
        boolean WinDivertSend(Pointer handle, Pointer packet, int packetLen, Pointer sendLen, Pointer addr);
        boolean WinDivertClose(Pointer handle);
        boolean WinDivertSetParam(Pointer handle, int param, long value);
        boolean WinDivertHelperCalcChecksums(Pointer packet, int packetLen, Pointer addr, long flags);
    }

    @Resource
    private WindivertProperties windivertProperties;

    @Resource
    private TransparentProxyProperties proxyProperties;

    @Resource
    private ContentPreAuditProperties contentPreAuditProperties;

    @Resource
    private WindivertMonitorService windivertMonitorService;

    @Resource
    private ProcessInfoResolver processInfoResolver;

    @Resource
    private ProcessPolicyEngine processPolicyEngine;

    @Resource
    private RelayPacketCodec relayPacketCodec;

    @Resource
    private ClientRelayFileSignatureService clientRelayFileSignatureService;

    @Resource
    private ContentPreAuditService contentPreAuditService;

    @Resource
    private UkeyLifecycleManager ukeyLifecycleManager;

    @Resource
    private ClientAuthService clientAuthService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong packetsSeen = new AtomicLong();
    private final AtomicLong packetsMatched = new AtomicLong();
    private final AtomicLong packetsRelayed = new AtomicLong();
    private final AtomicLong packetsReinjected = new AtomicLong();
    private final AtomicLong packetsDropped = new AtomicLong();
    private final AtomicLong pidMisses = new AtomicLong();
    private final AtomicLong denied = new AtomicLong();
    private final AtomicLong relayErrors = new AtomicLong();
    private final AtomicLong recvErrors = new AtomicLong();
    private final AtomicLong responsesReceived = new AtomicLong();
    private final AtomicLong responsesInjected = new AtomicLong();
    private final AtomicLong responseErrors = new AtomicLong();
    private final AtomicLong ukeyPolicyDrops = new AtomicLong();
    private final AtomicInteger responsePacketId = new AtomicInteger(1);
    private final ConcurrentMap<String, AddressSnapshot> addressCache = new ConcurrentHashMap<>();

    private volatile WinDivertLibrary library;
    private volatile Pointer handle;
    private volatile DatagramSocket relaySocket;
    private volatile InetSocketAddress relayAddress;
    private volatile ExecutorService executorService;
    private volatile Future<?> workerFuture;
    private volatile Future<?> relayResponseFuture;
    private volatile boolean available = false;
    private volatile long startedAt = 0L;
    private volatile long lastPacketAt = 0L;
    private volatile String lastReason = "NOT_STARTED";
    private volatile String lastError;
    private volatile long lastUkeyPolicyDropAt = 0L;
    private volatile String lastUkeyPolicyReason;
    private volatile long lastUkeyPolicyLogAt = 0L;

    @PostConstruct
    public void initUkeyPolicyListener() {
        ukeyLifecycleManager.addStateChangeListener((oldState, newState, message) -> {
            if (!isUkeyStateAuthorized(newState)) {
                addressCache.clear();
                clientRelayFileSignatureService.clear();
                lastUkeyPolicyReason = "UKEY_STATE_" + newState;
                log.info("[TransparentUDP] UKey state changed {} -> {}, relay response/signature context cleared",
                        oldState, newState);
            }
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (shouldStart()) {
            startProxy();
        } else {
            lastReason = "TRANSPARENT_PROXY_DISABLED";
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE + 100;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", proxyProperties.isEnabled());
        status.put("mode", windivertProperties.getMode());
        status.put("running", running.get());
        status.put("available", available);
        status.put("filter", proxyProperties.getFilter());
        status.put("effectiveFilter", normalizeFilter(proxyProperties.getFilter()));
        status.put("relayHost", proxyProperties.getRelayHost());
        status.put("relayPort", proxyProperties.getRelayPort());
        status.put("targetPorts", proxyProperties.getTargetPorts());
        status.put("failOpen", proxyProperties.isFailOpen());
        status.put("requireUkeyAuthentication", proxyProperties.isRequireUkeyAuthentication());
        status.put("ukeyAuthenticated", isUkeyAuthorizedForRelay());
        status.put("clientAuthenticated", clientAuthService.isAuthenticated());
        status.put("ukeyState", ukeyLifecycleManager.getCurrentState());
        status.put("ukeyCertSerialNo", ukeyLifecycleManager.getCurrentCertSerialNo());
        status.put("contentPreAuditFailClosed", isContentPreAuditFailClosed());
        status.put("contentPreAuditTransmissionDisabled", true);
        status.put("packetsSeen", packetsSeen.get());
        status.put("packetsMatched", packetsMatched.get());
        status.put("packetsRelayed", packetsRelayed.get());
        status.put("packetsReinjected", packetsReinjected.get());
        status.put("packetsDropped", packetsDropped.get());
        status.put("pidMisses", pidMisses.get());
        status.put("denied", denied.get());
        status.put("relayErrors", relayErrors.get());
        status.put("recvErrors", recvErrors.get());
        status.put("responsesReceived", responsesReceived.get());
        status.put("responsesInjected", responsesInjected.get());
        status.put("responseErrors", responseErrors.get());
        status.put("ukeyPolicyDrops", ukeyPolicyDrops.get());
        status.put("lastUkeyPolicyDropAt", lastUkeyPolicyDropAt);
        status.put("lastUkeyPolicyReason", lastUkeyPolicyReason);
        status.put("addressCacheSize", addressCache.size());
        status.put("startedAt", startedAt);
        status.put("lastPacketAt", lastPacketAt);
        status.put("lastReason", lastReason);
        status.put("lastError", lastError);
        return status;
    }

    @Override
    public void clear() {
        packetsSeen.set(0L);
        packetsMatched.set(0L);
        packetsRelayed.set(0L);
        packetsReinjected.set(0L);
        packetsDropped.set(0L);
        pidMisses.set(0L);
        denied.set(0L);
        relayErrors.set(0L);
        recvErrors.set(0L);
        responsesReceived.set(0L);
        responsesInjected.set(0L);
        responseErrors.set(0L);
        ukeyPolicyDrops.set(0L);
        lastUkeyPolicyDropAt = 0L;
        lastUkeyPolicyReason = null;
        lastError = null;
    }

    @PreDestroy
    public void destroy() {
        stopProxy();
    }

    private boolean shouldStart() {
        return windivertProperties.isEnabled()
                && proxyProperties.isEnabled()
                && "proxy".equalsIgnoreCase(windivertProperties.getMode());
    }

    private void startProxy() {
        if (!isWindows()) {
            markUnavailable("NOT_WINDOWS", "WinDivert transparent proxy only supports Windows");
            return;
        }
        if (trimToNull(proxyProperties.getRelayHost()) == null || proxyProperties.getRelayPort() <= 0) {
            markUnavailable("INVALID_RELAY_TARGET", "transparent-proxy relay-host/relay-port is required");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            library = loadLibrary();
            relayAddress = new InetSocketAddress(
                    InetAddress.getByName(proxyProperties.getRelayHost()),
                    proxyProperties.getRelayPort());
            relaySocket = new DatagramSocket();
            String filter = normalizeFilter(proxyProperties.getFilter());
            Pointer opened = library.WinDivertOpen(filter, WINDIVERT_LAYER_NETWORK, (short) 0, 0L);
            if (opened == null || Pointer.nativeValue(opened) == -1L) {
                int err = Native.getLastError();
                throw new IllegalStateException("WinDivertOpen failed, lastError=" + err);
            }
            handle = opened;
            setParamQuietly(WINDIVERT_PARAM_QUEUE_LENGTH, windivertProperties.getQueueLength());
            setParamQuietly(WINDIVERT_PARAM_QUEUE_TIME, windivertProperties.getQueueTimeMs());

            available = true;
            startedAt = System.currentTimeMillis();
            lastReason = "RUNNING";
            lastError = null;
            AtomicInteger threadNo = new AtomicInteger(1);
            executorService = Executors.newFixedThreadPool(2, r -> {
                Thread thread = new Thread(r, "transparent-udp-proxy-" + threadNo.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });
            workerFuture = executorService.submit(this::receiveLoop);
            relayResponseFuture = executorService.submit(this::relayResponseLoop);
            log.info("[TransparentUDP] started: filter={}, relay={}:{}",
                    filter, proxyProperties.getRelayHost(), proxyProperties.getRelayPort());
        } catch (Throwable t) {
            running.set(false);
            closeHandleQuietly();
            closeRelaySocketQuietly();
            markUnavailable("START_FAILED", t.getMessage());
            log.warn("[TransparentUDP] start failed: {}", t.getMessage(), t);
        }
    }

    private void stopProxy() {
        running.set(false);
        Future<?> future = workerFuture;
        if (future != null) {
            future.cancel(true);
        }
        Future<?> responseFuture = relayResponseFuture;
        if (responseFuture != null) {
            responseFuture.cancel(true);
        }
        ExecutorService service = executorService;
        if (service != null) {
            service.shutdownNow();
        }
        closeHandleQuietly();
        closeRelaySocketQuietly();
        available = false;
        lastReason = "STOPPED";
    }

    private void receiveLoop() {
        int maxPacketSize = sanitizedMaxPacketSize();
        Memory packet = new Memory(maxPacketSize);
        Memory address = new Memory(ADDRESS_SIZE);
        Memory recvLen = new Memory(4);
        while (running.get()) {
            try {
                packet.clear();
                address.clear();
                recvLen.clear();
                boolean ok = library.WinDivertRecv(handle, packet, maxPacketSize, recvLen, address);
                if (!ok) {
                    int err = Native.getLastError();
                    recvErrors.incrementAndGet();
                    if (running.get()) {
                        lastError = "WinDivertRecv failed, lastError=" + err;
                        sleepQuietly(100L);
                    }
                    continue;
                }
                int packetLength = recvLen.getInt(0);
                packetsSeen.incrementAndGet();
                handlePacket(packet, packetLength, address);
            } catch (Throwable t) {
                recvErrors.incrementAndGet();
                lastError = t.getMessage();
                log.warn("[TransparentUDP] packet handling failed: {}", t.getMessage());
                sleepQuietly(100L);
            }
        }
    }

    private void relayResponseLoop() {
        int bufferSize = sanitizedMaxPacketSize();
        byte[] buffer = new byte[bufferSize];
        while (running.get()) {
            DatagramSocket socket = relaySocket;
            if (socket == null || socket.isClosed()) {
                sleepQuietly(100L);
                continue;
            }
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                responsesReceived.incrementAndGet();
                byte[] bytes = Arrays.copyOfRange(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getOffset() + packet.getLength());
                RelayPacketCodec.RelayPacket relayPacket = relayPacketCodec.decode(bytes, packet.getLength());
                learnContentAuditResponse(relayPacket);
                injectResponse(relayPacket);
            } catch (SocketException e) {
                if (running.get()) {
                    responseErrors.incrementAndGet();
                    lastError = e.getMessage();
                }
            } catch (Throwable t) {
                responseErrors.incrementAndGet();
                lastError = t.getMessage();
                log.warn("[TransparentUDP] relay response handling failed: {}", t.getMessage());
            }
        }
    }

    private void handlePacket(Memory packet, int packetLength, Memory address) {
        AddressFlags flags = parseAddressFlags(address);
        if (flags.layer != WINDIVERT_LAYER_NETWORK
                || flags.event != WINDIVERT_EVENT_NETWORK_PACKET
                || !flags.outbound) {
            reinject(packet, packetLength, address);
            return;
        }
        if (flags.impostor) {
            log.debug("[TransparentUDP] ignore impostor packet to avoid capture loop");
            return;
        }

        PacketInfo packetInfo = parseUdpPacket(packet, packetLength);
        if (packetInfo == null || !isTargetPort(packetInfo.destinationPort)) {
            reinject(packet, packetLength, address);
            return;
        }
        packetsMatched.incrementAndGet();

        if (isUkeyPolicyDenied()) {
            dropByUkeyPolicy(packet, packetLength, address);
            return;
        }

        WindivertMonitorService.PidLookupResult pidResult = findPidWithRetry(packetInfo);
        if (!pidResult.isHit()) {
            pidMisses.incrementAndGet();
            handleUnproxied(packet, packetLength, address, "PID_MISS");
            return;
        }

        ProcessInfoResolver.ProcessInfo processInfo = processInfoResolver.resolve(pidResult.getPid());
        ProcessPolicyEngine.PolicyDecision decision = processPolicyEngine.decide(processInfo);
        if (!"ALLOW_SHADOW".equals(decision.getDecision())) {
            denied.incrementAndGet();
            handleUnproxied(packet, packetLength, address, decision.getReasonCode());
            return;
        }

        try {
            byte[] payload = extractPayload(packet, packetInfo);
            RelayPacketCodec.RelayPacket relayPacketModel = new RelayPacketCodec.RelayPacket(
                    pidResult.getPid(),
                    processInfo.getProcessName(),
                    packetInfo.sourceIp,
                    packetInfo.sourcePort,
                    packetInfo.destinationIp,
                    packetInfo.destinationPort,
                    System.currentTimeMillis(),
                    payload);
            byte[] relayBytes = relayPacketCodec.encode(relayPacketModel);
            ContentAuditRelayPacket relayPacket = new ContentAuditRelayPacket();
            relayPacket.setSourceIp(packetInfo.sourceIp);
            relayPacket.setSourcePort(packetInfo.sourcePort);
            relayPacket.setTargetIp(packetInfo.destinationIp);
            relayPacket.setTargetPort(packetInfo.destinationPort);
            relayPacket.setPid(pidResult.getPid());
            relayPacket.setProcessName(processInfo.getProcessName());
            relayPacket.setPayload(payload);
            relayPacket.setRelayBytes(relayBytes);
            relayPacket.setTimestamp(System.currentTimeMillis());
            rememberAddress(packetInfo, address);

            clientRelayFileSignatureService.observePacket(
                    packetInfo.sourceIp,
                    packetInfo.sourcePort,
                    packetInfo.destinationIp,
                    packetInfo.destinationPort,
                    payload);
            if (!forwardRelayPacket(relayPacket)) {
                throw new IllegalStateException("forward relay packet failed");
            }
            packetsDropped.incrementAndGet();
            lastPacketAt = System.currentTimeMillis();
            log.debug("[TransparentUDP] relayed and dropped original: {}:{} -> {}:{}, pid={}, bytes={}",
                    packetInfo.sourceIp, packetInfo.sourcePort,
                    packetInfo.destinationIp, packetInfo.destinationPort,
                    pidResult.getPid(), payload.length);
        } catch (Throwable t) {
            relayErrors.incrementAndGet();
            lastError = t.getMessage();
            log.warn("[TransparentUDP] relay failed: {}:{} -> {}:{}, reason={}",
                    packetInfo.sourceIp, packetInfo.sourcePort,
                    packetInfo.destinationIp, packetInfo.destinationPort,
                    t.getMessage());
            handleUnproxied(packet, packetLength, address, "RELAY_FAILED");
        }
    }

    private boolean isUkeyPolicyDenied() {
        return proxyProperties.isRequireUkeyAuthentication() && !isUkeyAuthorizedForRelay();
    }

    private boolean isUkeyAuthorizedForRelay() {
        return ukeyLifecycleManager != null
                && ukeyLifecycleManager.isAuthenticated()
                && clientAuthService != null
                && clientAuthService.isAuthenticated();
    }

    private boolean isUkeyStateAuthorized(UkeyLifecycleManager.State state) {
        return state == UkeyLifecycleManager.State.AUTHENTICATED
                || state == UkeyLifecycleManager.State.CHANNEL_ACTIVE;
    }

    private void dropByUkeyPolicy(Memory packet, int packetLength, Memory address) {
        ukeyPolicyDrops.incrementAndGet();
        packetsDropped.incrementAndGet();
        lastUkeyPolicyDropAt = System.currentTimeMillis();
        lastUkeyPolicyReason = "UKEY_NOT_AUTHENTICATED";
        lastReason = "UKEY_POLICY_DROP";
        long now = System.currentTimeMillis();
        if (now - lastUkeyPolicyLogAt > 5000L) {
            lastUkeyPolicyLogAt = now;
            log.warn("[TransparentUDP] drop packet by UKey policy, state={}, cert={}, required=true",
                    ukeyLifecycleManager.getCurrentState(), ukeyLifecycleManager.getCurrentCertSerialNo());
        }
    }

    private void learnContentAuditResponse(RelayPacketCodec.RelayPacket relayPacket) {
        // Content pre-audit has moved to the signing flow; WinDivert relay no longer learns ACK templates.
    }

    @Override
    public boolean send(ContentAuditRelayPacket relayPacket) {
        try {
            return forwardRelayPacket(relayPacket);
        } catch (Throwable t) {
            relayErrors.incrementAndGet();
            lastError = t.getMessage();
            log.warn("[TransparentUDP] audited relay send failed: {}", t.getMessage());
            return false;
        }
    }

    private boolean forwardRelayPacket(ContentAuditRelayPacket relayPacket) throws Exception {
        if (relayPacket == null || relayPacket.getPayload() == null) {
            return false;
        }
        DatagramSocket socket = relaySocket;
        InetSocketAddress address = relayAddress;
        if (socket == null || socket.isClosed() || address == null) {
            lastError = "relay socket is not ready";
            return false;
        }
        byte[] relayBytes = relayPacket.getRelayBytes();
        if (relayBytes == null || hasText(relayPacket.getContentTokenId()) || hasText(relayPacket.getContentFileId())) {
            RelayPacketCodec.RelayPacket relayPacketModel = new RelayPacketCodec.RelayPacket(
                    relayPacket.getPid(),
                    relayPacket.getProcessName(),
                    relayPacket.getSourceIp(),
                    relayPacket.getSourcePort(),
                    relayPacket.getTargetIp(),
                    relayPacket.getTargetPort(),
                    relayPacket.getTimestamp() > 0 ? relayPacket.getTimestamp() : System.currentTimeMillis(),
                    relayPacket.getPayload(),
                    relayPacket.getContentTokenId(),
                    relayPacket.getContentFileId());
            relayBytes = relayPacketCodec.encode(relayPacketModel);
            relayPacket.setRelayBytes(relayBytes);
        }
        DatagramPacket datagramPacket = new DatagramPacket(relayBytes, relayBytes.length, address);
        socket.send(datagramPacket);
        packetsRelayed.incrementAndGet();
        lastPacketAt = System.currentTimeMillis();
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void injectResponse(RelayPacketCodec.RelayPacket relayPacket) {
        if (relayPacket == null || relayPacket.getPayload() == null) {
            return;
        }
        AddressSnapshot snapshot = addressCache.get(flowKey(
                relayPacket.getOriginalSrcIp(),
                relayPacket.getOriginalSrcPort(),
                relayPacket.getOriginalDstIp(),
                relayPacket.getOriginalDstPort()));
        if (snapshot == null) {
            responseErrors.incrementAndGet();
            lastError = "response address cache miss";
            log.warn("[TransparentUDP] response address cache miss: {}:{} <- {}:{}",
                    relayPacket.getOriginalSrcIp(), relayPacket.getOriginalSrcPort(),
                    relayPacket.getOriginalDstIp(), relayPacket.getOriginalDstPort());
            return;
        }

        byte[] packetBytes = buildUdpResponsePacket(relayPacket);
        Memory packetMemory = new Memory(packetBytes.length);
        packetMemory.write(0, packetBytes, 0, packetBytes.length);
        Memory addressMemory = new Memory(ADDRESS_SIZE);
        addressMemory.write(0, snapshot.addressBytes, 0, snapshot.addressBytes.length);
        markInbound(addressMemory);
        library.WinDivertHelperCalcChecksums(packetMemory, packetBytes.length, addressMemory, 0L);

        Memory sendLen = new Memory(4);
        boolean ok = library.WinDivertSend(handle, packetMemory, packetBytes.length, sendLen, addressMemory);
        if (ok) {
            responsesInjected.incrementAndGet();
            lastPacketAt = System.currentTimeMillis();
            log.debug("[TransparentUDP] injected response: {}:{} -> {}:{}, bytes={}",
                    relayPacket.getOriginalDstIp(), relayPacket.getOriginalDstPort(),
                    relayPacket.getOriginalSrcIp(), relayPacket.getOriginalSrcPort(),
                    relayPacket.getPayload().length);
        } else {
            int err = Native.getLastError();
            responseErrors.incrementAndGet();
            lastError = "WinDivertSend response failed, lastError=" + err;
            log.warn("[TransparentUDP] response inject failed: {}", lastError);
        }
    }

    private void injectSyntheticResponse(PacketInfo packetInfo, long pid, String processName, byte[] payload) {
        if (packetInfo == null || payload == null || payload.length == 0) {
            return;
        }
        RelayPacketCodec.RelayPacket response = new RelayPacketCodec.RelayPacket(
                pid,
                processName,
                packetInfo.sourceIp,
                packetInfo.sourcePort,
                packetInfo.destinationIp,
                packetInfo.destinationPort,
                System.currentTimeMillis(),
                payload);
        injectResponse(response);
    }

    private void handleUnproxied(Memory packet, int packetLength, Memory address, String reason) {
        if (proxyProperties.isFailOpen() && !isContentPreAuditFailClosed()) {
            reinject(packet, packetLength, address);
            return;
        }
        packetsDropped.incrementAndGet();
        log.warn("[TransparentUDP] drop packet, reason={}", reason);
    }

    private boolean isContentPreAuditFailClosed() {
        return false;
    }

    private void reinject(Memory packet, int packetLength, Memory address) {
        if (packet == null || packetLength <= 0 || handle == null || library == null) {
            return;
        }
        Memory sendLen = new Memory(4);
        boolean ok = library.WinDivertSend(handle, packet, packetLength, sendLen, address);
        if (ok) {
            packetsReinjected.incrementAndGet();
        } else {
            int err = Native.getLastError();
            lastError = "WinDivertSend failed, lastError=" + err;
            log.warn("[TransparentUDP] reinject failed: {}", lastError);
        }
    }

    private void rememberAddress(PacketInfo packetInfo, Memory address) {
        if (packetInfo == null || address == null) {
            return;
        }
        byte[] bytes = new byte[ADDRESS_SIZE];
        address.read(0, bytes, 0, bytes.length);
        addressCache.put(flowKey(
                packetInfo.sourceIp,
                packetInfo.sourcePort,
                packetInfo.destinationIp,
                packetInfo.destinationPort),
                new AddressSnapshot(bytes, System.currentTimeMillis()));
        cleanupAddressCache();
    }

    private void cleanupAddressCache() {
        long expiredBefore = System.currentTimeMillis() - 60000L;
        for (Map.Entry<String, AddressSnapshot> entry : addressCache.entrySet()) {
            if (entry.getValue().lastSeen < expiredBefore) {
                addressCache.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private String flowKey(String sourceIp, int sourcePort, String destinationIp, int destinationPort) {
        return sourceIp + ":" + sourcePort + "->" + destinationIp + ":" + destinationPort;
    }

    private byte[] buildUdpResponsePacket(RelayPacketCodec.RelayPacket relayPacket) {
        byte[] payload = relayPacket.getPayload();
        if (payload.length > 65507) {
            throw new IllegalArgumentException("UDP response payload too large: " + payload.length);
        }
        int totalLength = 20 + 8 + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x45);
        buffer.put((byte) 0);
        buffer.putShort((short) totalLength);
        buffer.putShort((short) (responsePacketId.getAndIncrement() & 0xFFFF));
        buffer.putShort((short) 0);
        buffer.put((byte) 64);
        buffer.put((byte) IPPROTO_UDP);
        buffer.putShort((short) 0);
        buffer.put(ipToBytes(relayPacket.getOriginalDstIp()));
        buffer.put(ipToBytes(relayPacket.getOriginalSrcIp()));
        buffer.putShort((short) relayPacket.getOriginalDstPort());
        buffer.putShort((short) relayPacket.getOriginalSrcPort());
        buffer.putShort((short) (8 + payload.length));
        buffer.putShort((short) 0);
        buffer.put(payload);
        return buffer.array();
    }

    private byte[] ipToBytes(String ip) {
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != 4) {
                throw new IllegalArgumentException("only IPv4 response injection is supported");
            }
            return bytes;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid IPv4 address: " + ip, e);
        }
    }

    private void markInbound(Memory address) {
        int flags = address.getInt(8);
        flags &= ~(1 << 16); // Sniffed
        flags &= ~(1 << 17); // Outbound
        flags &= ~(1 << 19); // Impostor
        flags &= ~(1 << 20); // IPv6
        address.setInt(8, flags);
    }

    private WindivertMonitorService.PidLookupResult findPidWithRetry(PacketInfo packetInfo) {
        WindivertMonitorService.PidLookupResult result =
                windivertMonitorService.findPid(packetInfo.sourceIp, packetInfo.sourcePort);
        if (result.isHit()) {
            return result;
        }
        int retries = sanitizedPidLookupRetryCount();
        long delayMs = sanitizedPidLookupRetryDelayMs();
        for (int i = 0; i < retries && running.get(); i++) {
            sleepQuietly(delayMs);
            result = windivertMonitorService.findPid(packetInfo.sourceIp, packetInfo.sourcePort);
            if (result.isHit()) {
                return result;
            }
        }
        return result;
    }

    private boolean isTargetPort(int destinationPort) {
        Set<Integer> ports = proxyProperties.getTargetPorts();
        return ports == null || ports.isEmpty() || ports.contains(destinationPort);
    }

    private PacketInfo parseUdpPacket(Memory packet, int packetLength) {
        if (packet == null || packetLength < 28) {
            return null;
        }
        int version = (packet.getByte(0) >> 4) & 0x0F;
        if (version != 4) {
            return null;
        }
        int headerLength = (packet.getByte(0) & 0x0F) * 4;
        if (headerLength < 20 || packetLength < headerLength + 8) {
            return null;
        }
        int protocol = packet.getByte(9) & 0xFF;
        if (protocol != IPPROTO_UDP) {
            return null;
        }
        int totalLength = readUnsignedShort(packet, 2);
        int effectiveLength = totalLength > 0 ? Math.min(totalLength, packetLength) : packetLength;
        int udpOffset = headerLength;
        int udpLength = readUnsignedShort(packet, udpOffset + 4);
        int payloadOffset = udpOffset + 8;
        int payloadLength = Math.max(0, Math.min(udpLength, effectiveLength - udpOffset) - 8);
        return new PacketInfo(
                formatIpv4(packet, 12),
                readUnsignedShort(packet, udpOffset),
                formatIpv4(packet, 16),
                readUnsignedShort(packet, udpOffset + 2),
                packetLength,
                payloadOffset,
                payloadLength);
    }

    private byte[] extractPayload(Memory packet, PacketInfo packetInfo) {
        byte[] payload = new byte[packetInfo.payloadLength];
        if (payload.length > 0) {
            packet.read(packetInfo.payloadOffset, payload, 0, payload.length);
        }
        return payload;
    }

    private AddressFlags parseAddressFlags(Memory address) {
        long flags = address.getLong(8);
        return new AddressFlags(
                (int) (flags & 0xFFL),
                (int) ((flags >> 8) & 0xFFL),
                ((flags >> 17) & 0x01L) != 0,
                ((flags >> 19) & 0x01L) != 0);
    }

    private int readUnsignedShort(Memory memory, int offset) {
        return ((memory.getByte(offset) & 0xFF) << 8)
                | (memory.getByte(offset + 1) & 0xFF);
    }

    private String formatIpv4(Memory packet, int offset) {
        return (packet.getByte(offset) & 0xFF) + "."
                + (packet.getByte(offset + 1) & 0xFF) + "."
                + (packet.getByte(offset + 2) & 0xFF) + "."
                + (packet.getByte(offset + 3) & 0xFF);
    }

    private WinDivertLibrary loadLibrary() {
        String dllPath = trimToNull(windivertProperties.getDllPath());
        if (dllPath == null) {
            return Native.load("WinDivert", WinDivertLibrary.class);
        }
        File file = new File(dllPath);
        if (file.isDirectory()) {
            String existing = System.getProperty("jna.library.path", "");
            String path = file.getAbsolutePath() + (existing.isEmpty() ? "" : File.pathSeparator + existing);
            System.setProperty("jna.library.path", path);
            return Native.load("WinDivert", WinDivertLibrary.class);
        }
        return Native.load(file.getAbsolutePath(), WinDivertLibrary.class);
    }

    private void setParamQuietly(int param, long value) {
        if (value <= 0 || library == null || handle == null) {
            return;
        }
        try {
            library.WinDivertSetParam(handle, param, value);
        } catch (Throwable t) {
            log.debug("[TransparentUDP] set param failed: {}", t.getMessage());
        }
    }

    private void closeHandleQuietly() {
        Pointer current = handle;
        handle = null;
        if (current != null && Pointer.nativeValue(current) != -1L && library != null) {
            try {
                library.WinDivertClose(current);
            } catch (Throwable ignored) {
                // ignore during shutdown
            }
        }
    }

    private void closeRelaySocketQuietly() {
        DatagramSocket socket = relaySocket;
        relaySocket = null;
        if (socket != null) {
            socket.close();
        }
    }

    private int sanitizedMaxPacketSize() {
        int configured = proxyProperties.getMaxPacketSize();
        if (configured <= 0) {
            return 65535;
        }
        return Math.min(Math.max(configured, 68), MAX_PACKET_SIZE);
    }

    private int sanitizedPidLookupRetryCount() {
        int configured = proxyProperties.getPidLookupRetryCount();
        if (configured <= 0) {
            return 0;
        }
        return Math.min(configured, 20);
    }

    private long sanitizedPidLookupRetryDelayMs() {
        long configured = proxyProperties.getPidLookupRetryDelayMs();
        if (configured <= 0L) {
            return 0L;
        }
        return Math.min(configured, 200L);
    }

    private String normalizeFilter(String filter) {
        String value = trimToNull(filter);
        String normalized = value != null ? value : "udp and outbound";
        if (!normalized.toLowerCase(Locale.ROOT).contains("impostor")) {
            return "(" + normalized + ") and !impostor";
        }
        return normalized;
    }

    private void markUnavailable(String reason, String error) {
        available = false;
        lastReason = reason;
        lastError = error;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class PacketInfo {
        private final String sourceIp;
        private final int sourcePort;
        private final String destinationIp;
        private final int destinationPort;
        private final int packetLength;
        private final int payloadOffset;
        private final int payloadLength;

        private PacketInfo(String sourceIp, int sourcePort,
                           String destinationIp, int destinationPort,
                           int packetLength, int payloadOffset, int payloadLength) {
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.destinationIp = destinationIp;
            this.destinationPort = destinationPort;
            this.packetLength = packetLength;
            this.payloadOffset = payloadOffset;
            this.payloadLength = payloadLength;
        }
    }

    private static class AddressFlags {
        private final int layer;
        private final int event;
        private final boolean outbound;
        private final boolean impostor;

        private AddressFlags(int layer, int event, boolean outbound, boolean impostor) {
            this.layer = layer;
            this.event = event;
            this.outbound = outbound;
            this.impostor = impostor;
        }
    }

    private static class AddressSnapshot {
        private final byte[] addressBytes;
        private final long lastSeen;

        private AddressSnapshot(byte[] addressBytes, long lastSeen) {
            this.addressBytes = addressBytes;
            this.lastSeen = lastSeen;
        }
    }
}
