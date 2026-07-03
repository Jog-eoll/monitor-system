package com.infopublish.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用配置类
 */
@Configuration
public class AppConfig {

    /**
     * 进程绑定配置属性
     * 绑定 application.yml 中的 process-bind.* 配置项
     */
    @Configuration
    @ConfigurationProperties(prefix = "process-bind")
    public static class ProcessBindProperties {

        /** 是否启用进程绑定及来源校验 */
        private boolean enabled = true;

        /** 目标进程文件名（用于 FindFirstFileW / FindNextFileW 发现可执行文件；不用于按进程名绑定 PID） */
        private String targetProcessName = "BigScreenApp.exe";

        /**
         * 目标进程可执行文件受控搜索目录。
         * 使用 Windows FindFirstFileW / FindNextFileW 在这些目录下递归查找 target-process-name。
         * 多个目录用英文分号或英文逗号分隔；留空时默认使用 target-process-path 的父目录。
         */
        private String targetProcessSearchRoots = "";

        /**
         * 目标进程可执行文件完整绝对路径。
         * PID 绑定与自动拉起均以该绝对路径为准，不再接受仅进程名匹配。
         * 示例：C:\Program Files\Sigma\Sigma Play.exe
         */
        private String targetProcessPath = "";

        /**
         * 目标进程可执行文件 MD5。
         * 留空时跳过 MD5 校验；多个版本的 MD5 可用英文逗号分隔。
         */
        private String targetProcessMd5 = "";

        /** 目标进程未运行时，是否按 target-process-path 自动拉起。 */
        private boolean autoStartEnabled = false;

        /** 拉起目标进程后等待绑定成功的最长时间。 */
        private long startWaitMillis = 15000L;

        /** 拉起目标进程后的 PID 探测间隔。 */
        private long startPollMillis = 500L;

        /** 是否守护已绑定 PID。PID 消亡时按 UKey 拔出处理。 */
        private boolean guardEnabled = true;

        /**
         * PID 守护模式：hybrid / wmi / scheduled。
         * hybrid 优先使用 WMI 退出事件，定时检查兜底。
         */
        private String guardMode = "hybrid";

        /** PID 守护兜底检查间隔。 */
        private long guardCheckIntervalMillis = 2000L;

        /** PID 存活检查缓存时间，避免每包执行 tasklist。 */
        private long pidAliveCacheMillis = 2000L;

        /** UDP 端口归属表查询缓存时间，降低高频校验时的系统 API 调用频率。 */
        private long udpTableCacheMillis = 200L;

        /** Process integrity lightweight check cache duration. */
        private long integrityCacheMillis = 1000L;

        /** Process executable MD5 check cache duration. */
        private long integrityHashCacheMillis = 30000L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTargetProcessName() { return targetProcessName; }
        public void setTargetProcessName(String targetProcessName) { this.targetProcessName = targetProcessName; }

        public String getTargetProcessSearchRoots() { return targetProcessSearchRoots; }
        public void setTargetProcessSearchRoots(String targetProcessSearchRoots) { this.targetProcessSearchRoots = targetProcessSearchRoots; }

        public String getTargetProcessPath() { return targetProcessPath; }
        public void setTargetProcessPath(String targetProcessPath) { this.targetProcessPath = targetProcessPath; }

        public String getTargetProcessMd5() { return targetProcessMd5; }
        public void setTargetProcessMd5(String targetProcessMd5) { this.targetProcessMd5 = targetProcessMd5; }

        public boolean isAutoStartEnabled() { return autoStartEnabled; }
        public void setAutoStartEnabled(boolean autoStartEnabled) { this.autoStartEnabled = autoStartEnabled; }

        public long getStartWaitMillis() { return startWaitMillis; }
        public void setStartWaitMillis(long startWaitMillis) { this.startWaitMillis = startWaitMillis; }

        public long getStartPollMillis() { return startPollMillis; }
        public void setStartPollMillis(long startPollMillis) { this.startPollMillis = startPollMillis; }

        public boolean isGuardEnabled() { return guardEnabled; }
        public void setGuardEnabled(boolean guardEnabled) { this.guardEnabled = guardEnabled; }

        public String getGuardMode() { return guardMode; }
        public void setGuardMode(String guardMode) { this.guardMode = guardMode; }

        public long getGuardCheckIntervalMillis() { return guardCheckIntervalMillis; }
        public void setGuardCheckIntervalMillis(long guardCheckIntervalMillis) { this.guardCheckIntervalMillis = guardCheckIntervalMillis; }

        public long getPidAliveCacheMillis() { return pidAliveCacheMillis; }
        public void setPidAliveCacheMillis(long pidAliveCacheMillis) { this.pidAliveCacheMillis = pidAliveCacheMillis; }

        public long getUdpTableCacheMillis() { return udpTableCacheMillis; }
        public void setUdpTableCacheMillis(long udpTableCacheMillis) { this.udpTableCacheMillis = udpTableCacheMillis; }

        public long getIntegrityCacheMillis() { return integrityCacheMillis; }
        public void setIntegrityCacheMillis(long integrityCacheMillis) { this.integrityCacheMillis = integrityCacheMillis; }

        public long getIntegrityHashCacheMillis() { return integrityHashCacheMillis; }
        public void setIntegrityHashCacheMillis(long integrityHashCacheMillis) { this.integrityHashCacheMillis = integrityHashCacheMillis; }
    }

    /**
     * WinDivert 旁路监听配置。
     * Phase 1 仅用于实时维护 UDP 来源端口到 PID 的缓存，不改变现有 ARP 流量路径。
     */
    @Configuration
    @ConfigurationProperties(prefix = "windivert")
    public static class WindivertProperties {

        /** 是否启用 WinDivert Monitor。默认关闭，避免未安装原生驱动时影响启动。 */
        private boolean enabled = false;

        /** 当前只支持 monitor 模式；透明代理属于后续阶段。 */
        private String mode = "monitor";

        /** WinDivert FLOW 层过滤器。 */
        private String filter = "udp and outbound";

        /** WinDivert.dll 路径。为空时按系统 PATH / jna.library.path 查找。 */
        private String dllPath = "";

        /** FLOW 事件队列长度。 */
        private long queueLength = 4096L;

        /** FLOW 事件队列保留时间，毫秒。 */
        private long queueTimeMs = 2000L;

        /** 来源端口 PID 缓存 TTL，毫秒。 */
        private long cacheTtlMs = 10000L;

        /** FLOW 删除事件后短暂保留 PID 映射，缓解 NETWORK/FLOW 事件乱序。 */
        private long deleteRetainMs = 2000L;

        /** WinDivert 未命中或不可用时是否回退到 GetExtendedUdpTable。 */
        private boolean fallbackToUdpTable = true;

        /** Shadow Mode 旁路抓包配置。 */
        private ShadowProperties shadow = new ShadowProperties();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getFilter() { return filter; }
        public void setFilter(String filter) { this.filter = filter; }

        public String getDllPath() { return dllPath; }
        public void setDllPath(String dllPath) { this.dllPath = dllPath; }

        public long getQueueLength() { return queueLength; }
        public void setQueueLength(long queueLength) { this.queueLength = queueLength; }

        public long getQueueTimeMs() { return queueTimeMs; }
        public void setQueueTimeMs(long queueTimeMs) { this.queueTimeMs = queueTimeMs; }

        public long getCacheTtlMs() { return cacheTtlMs; }
        public void setCacheTtlMs(long cacheTtlMs) { this.cacheTtlMs = cacheTtlMs; }

        public long getDeleteRetainMs() { return deleteRetainMs; }
        public void setDeleteRetainMs(long deleteRetainMs) { this.deleteRetainMs = deleteRetainMs; }

        public boolean isFallbackToUdpTable() { return fallbackToUdpTable; }
        public void setFallbackToUdpTable(boolean fallbackToUdpTable) { this.fallbackToUdpTable = fallbackToUdpTable; }

        public ShadowProperties getShadow() { return shadow; }
        public void setShadow(ShadowProperties shadow) {
            this.shadow = shadow != null ? shadow : new ShadowProperties();
        }

        public static class ShadowProperties {
            /** 是否启用 NETWORK 层只读抓包。 */
            private boolean enabled = false;

            /** NETWORK 层过滤器。 */
            private String packetFilter = "udp and outbound";

            /** 单包最大复制字节数。 */
            private int maxPacketSize = 65535;

            /** 内存保留最近影子事件数量。 */
            private int eventRetention = 500;

            /** 预留：采样 payload 字节数。默认 0，避免保存内容。 */
            private int payloadSampleBytes = 0;

            /** 未识别进程是否输出 WARN 日志。 */
            private boolean logUnknownProcess = true;

            /** PID 缓存未命中时重试次数。 */
            private int pidLookupRetryCount = 3;

            /** PID 缓存未命中时每次重试等待毫秒。 */
            private long pidLookupRetryDelayMs = 30L;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public String getPacketFilter() { return packetFilter; }
            public void setPacketFilter(String packetFilter) { this.packetFilter = packetFilter; }

            public int getMaxPacketSize() { return maxPacketSize; }
            public void setMaxPacketSize(int maxPacketSize) { this.maxPacketSize = maxPacketSize; }

            public int getEventRetention() { return eventRetention; }
            public void setEventRetention(int eventRetention) { this.eventRetention = eventRetention; }

            public int getPayloadSampleBytes() { return payloadSampleBytes; }
            public void setPayloadSampleBytes(int payloadSampleBytes) { this.payloadSampleBytes = payloadSampleBytes; }

            public boolean isLogUnknownProcess() { return logUnknownProcess; }
            public void setLogUnknownProcess(boolean logUnknownProcess) { this.logUnknownProcess = logUnknownProcess; }

            public int getPidLookupRetryCount() { return pidLookupRetryCount; }
            public void setPidLookupRetryCount(int pidLookupRetryCount) { this.pidLookupRetryCount = pidLookupRetryCount; }

            public long getPidLookupRetryDelayMs() { return pidLookupRetryDelayMs; }
            public void setPidLookupRetryDelayMs(long pidLookupRetryDelayMs) { this.pidLookupRetryDelayMs = pidLookupRetryDelayMs; }
        }
    }

    /**
     * WinDivert Shadow Mode 进程策略配置。
     */
    @Configuration
    @ConfigurationProperties(prefix = "process-policy")
    public static class ProcessPolicyProperties {

        /** 是否启用影子策略判断。 */
        private boolean enabled = false;

        /** 允许的进程名列表。 */
        private List<String> whitelist = new ArrayList<>();

        /** Phase 2 只记录该决策，不真正阻断。 */
        private boolean blockUnknownProcess = false;

        /** PID 进程信息缓存时间。 */
        private long processInfoCacheMs = 5000L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getWhitelist() { return whitelist; }
        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist != null ? whitelist : new ArrayList<>();
        }

        public boolean isBlockUnknownProcess() { return blockUnknownProcess; }
        public void setBlockUnknownProcess(boolean blockUnknownProcess) { this.blockUnknownProcess = blockUnknownProcess; }

        public long getProcessInfoCacheMs() { return processInfoCacheMs; }
        public void setProcessInfoCacheMs(long processInfoCacheMs) { this.processInfoCacheMs = processInfoCacheMs; }
    }

    /**
     * WinDivert transparent UDP proxy config.
     */
    @Configuration
    @ConfigurationProperties(prefix = "transparent-proxy")
    public static class TransparentProxyProperties {

        /** Enable real UDP takeover. */
        private boolean enabled = false;

        /** NETWORK layer filter used by takeover mode. */
        private String filter = "udp and outbound";

        /** Linux publish-gateway relay host. */
        private String relayHost = "";

        /** Linux publish-gateway relay UDP port. */
        private int relayPort = 18092;

        /** Original destination ports to intercept. Empty means all UDP, not recommended. */
        private Set<Integer> targetPorts = new HashSet<>();

        /** Fail-open when relay send fails or PID cannot be resolved. */
        private boolean failOpen = true;

        /** Max packet bytes copied from WinDivert. */
        private int maxPacketSize = 65535;

        /** PID lookup retry count before applying fail-open/drop decision. */
        private int pidLookupRetryCount = 5;

        /** PID lookup retry delay in milliseconds. */
        private long pidLookupRetryDelayMs = 30L;

        /** Whether to append HMAC-SHA256 signature to relay packets. */
        private boolean signPacket = false;

        /** HMAC shared secret for relay packets. */
        private String signatureSecret = "";

        /** Drop captured Sigma traffic when UKey authentication is not active. */
        private boolean requireUkeyAuthentication = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getFilter() { return filter; }
        public void setFilter(String filter) { this.filter = filter; }

        public String getRelayHost() { return relayHost; }
        public void setRelayHost(String relayHost) { this.relayHost = relayHost; }

        public int getRelayPort() { return relayPort; }
        public void setRelayPort(int relayPort) { this.relayPort = relayPort; }

        public Set<Integer> getTargetPorts() { return targetPorts; }
        public void setTargetPorts(Set<Integer> targetPorts) {
            this.targetPorts = targetPorts != null ? targetPorts : new HashSet<>();
        }

        public boolean isFailOpen() { return failOpen; }
        public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }

        public int getMaxPacketSize() { return maxPacketSize; }
        public void setMaxPacketSize(int maxPacketSize) { this.maxPacketSize = maxPacketSize; }

        public int getPidLookupRetryCount() { return pidLookupRetryCount; }
        public void setPidLookupRetryCount(int pidLookupRetryCount) { this.pidLookupRetryCount = pidLookupRetryCount; }

        public long getPidLookupRetryDelayMs() { return pidLookupRetryDelayMs; }
        public void setPidLookupRetryDelayMs(long pidLookupRetryDelayMs) { this.pidLookupRetryDelayMs = pidLookupRetryDelayMs; }

        public boolean isSignPacket() { return signPacket; }
        public void setSignPacket(boolean signPacket) { this.signPacket = signPacket; }

        public String getSignatureSecret() { return signatureSecret; }
        public void setSignatureSecret(String signatureSecret) { this.signatureSecret = signatureSecret; }

        public boolean isRequireUkeyAuthentication() { return requireUkeyAuthentication; }
        public void setRequireUkeyAuthentication(boolean requireUkeyAuthentication) {
            this.requireUkeyAuthentication = requireUkeyAuthentication;
        }
    }

    /**
     * File-level signature metadata for transparent relay.
     */
    @Configuration
    @ConfigurationProperties(prefix = "relay-file-signature")
    public static class RelayFileSignatureProperties {

        /** Enable client-side file completion signing. */
        private boolean enabled = false;

        /** First version is audit-only and must not block relay. */
        private String mode = "audit";

        /** Reassemble Sigma file payloads on the client side before signing. */
        private boolean shadowAssembleEnabled = false;

        /** Report signed metadata to publish-gateway over HTTP side channel. */
        private boolean reportEnabled = false;

        /** Optional explicit gateway HTTP base URL. Blank means derive from transparent-proxy.relay-host. */
        private String gatewayUrl = "";

        /** Gateway HTTP port used when gateway-url is blank. */
        private int gatewayHttpPort = 8092;

        /** Gateway endpoint receiving client signature records. */
        private String metadataPath = "/security/relay/file-signature/client-record";

        /** Do not assemble/sign files above this size. */
        private int maxFileSizeMb = 200;

        /** Assembly timeout for stale Sigma file packets. */
        private long assemblyTimeoutMs = 120000L;

        /** Quiet interval that marks a previous transfer as complete. */
        private long staleGapMs = 30000L;

        /** Manifest signature algorithm label. */
        private String signatureAlgorithm = "SM2-SIGN";

        /** File hash algorithm label. */
        private String hashAlgorithm = "SHA-256";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public boolean isShadowAssembleEnabled() { return shadowAssembleEnabled; }
        public void setShadowAssembleEnabled(boolean shadowAssembleEnabled) { this.shadowAssembleEnabled = shadowAssembleEnabled; }

        public boolean isReportEnabled() { return reportEnabled; }
        public void setReportEnabled(boolean reportEnabled) { this.reportEnabled = reportEnabled; }

        public String getGatewayUrl() { return gatewayUrl; }
        public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

        public int getGatewayHttpPort() { return gatewayHttpPort; }
        public void setGatewayHttpPort(int gatewayHttpPort) { this.gatewayHttpPort = gatewayHttpPort; }

        public String getMetadataPath() { return metadataPath; }
        public void setMetadataPath(String metadataPath) { this.metadataPath = metadataPath; }

        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }

        public long getAssemblyTimeoutMs() { return assemblyTimeoutMs; }
        public void setAssemblyTimeoutMs(long assemblyTimeoutMs) { this.assemblyTimeoutMs = assemblyTimeoutMs; }

        public long getStaleGapMs() { return staleGapMs; }
        public void setStaleGapMs(long staleGapMs) { this.staleGapMs = staleGapMs; }

        public String getSignatureAlgorithm() { return signatureAlgorithm; }
        public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }

        public String getHashAlgorithm() { return hashAlgorithm; }
        public void setHashAlgorithm(String hashAlgorithm) { this.hashAlgorithm = hashAlgorithm; }

        public boolean isAuditMode() {
            return mode == null || "audit".equalsIgnoreCase(mode.trim());
        }
    }

    /**
     * Client-side content pre-audit config.
     */
    @Configuration
    @ConfigurationProperties(prefix = "content-pre-audit")
    public static class ContentPreAuditProperties {

        /** Global business switch. */
        private boolean enabled = false;

        /** `enforce` or `audit`. */
        private String mode = "enforce";

        /** Monitor gateway pre-audit API URL. */
        private String auditUrl = "";

        /** Policy when remote pre-audit fails: manual/reject/allow. */
        private String failPolicy = "manual";

        /** Keep handled decisions for this TTL. */
        private long decisionCacheTtlMs = 600000L;

        /** Pending manual item timeout. */
        private long pendingTimeoutMs = 300000L;

        /** Mark a buffered file complete after this idle interval. */
        private long assemblyIdleCompleteMs = 8000L;

        /** Max image file size allowed for auto pre-audit. */
        private int maxImageSizeMb = 50;

        /** Max video file size allowed for auto pre-audit. */
        private int maxVideoSizeMb = 500;

        /** Max buffered packets per pending item. */
        private int maxBufferedPacketsPerItem = 200000;

        /** Require a gateway-issued content release token before relay. */
        private boolean tokenRequired = false;

        /** Inject learned Sigma ACK responses while holding content packets. */
        private boolean ackSimulationEnabled = false;

        /** Learn ACK response payloads from normal passthrough traffic. */
        private boolean ackLearningEnabled = true;

        /** Delay between replayed UDP packets after content passes audit. */
        private long replayDelayMs = 30L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getAuditUrl() { return auditUrl; }
        public void setAuditUrl(String auditUrl) { this.auditUrl = auditUrl; }

        public String getFailPolicy() { return failPolicy; }
        public void setFailPolicy(String failPolicy) { this.failPolicy = failPolicy; }

        public long getDecisionCacheTtlMs() { return decisionCacheTtlMs; }
        public void setDecisionCacheTtlMs(long decisionCacheTtlMs) { this.decisionCacheTtlMs = decisionCacheTtlMs; }

        public long getPendingTimeoutMs() { return pendingTimeoutMs; }
        public void setPendingTimeoutMs(long pendingTimeoutMs) { this.pendingTimeoutMs = pendingTimeoutMs; }

        public long getAssemblyIdleCompleteMs() { return assemblyIdleCompleteMs; }
        public void setAssemblyIdleCompleteMs(long assemblyIdleCompleteMs) {
            this.assemblyIdleCompleteMs = assemblyIdleCompleteMs;
        }

        public int getMaxImageSizeMb() { return maxImageSizeMb; }
        public void setMaxImageSizeMb(int maxImageSizeMb) { this.maxImageSizeMb = maxImageSizeMb; }

        public int getMaxVideoSizeMb() { return maxVideoSizeMb; }
        public void setMaxVideoSizeMb(int maxVideoSizeMb) { this.maxVideoSizeMb = maxVideoSizeMb; }

        public int getMaxBufferedPacketsPerItem() { return maxBufferedPacketsPerItem; }
        public void setMaxBufferedPacketsPerItem(int maxBufferedPacketsPerItem) {
            this.maxBufferedPacketsPerItem = maxBufferedPacketsPerItem;
        }

        public boolean isTokenRequired() { return tokenRequired; }
        public void setTokenRequired(boolean tokenRequired) { this.tokenRequired = tokenRequired; }

        public boolean isAckSimulationEnabled() { return ackSimulationEnabled; }
        public void setAckSimulationEnabled(boolean ackSimulationEnabled) {
            this.ackSimulationEnabled = ackSimulationEnabled;
        }

        public boolean isAckLearningEnabled() { return ackLearningEnabled; }
        public void setAckLearningEnabled(boolean ackLearningEnabled) {
            this.ackLearningEnabled = ackLearningEnabled;
        }

        public long getReplayDelayMs() { return replayDelayMs; }
        public void setReplayDelayMs(long replayDelayMs) { this.replayDelayMs = replayDelayMs; }
    }

    /**
     * Pre-send signed package verification config.
     */
    @Configuration
    @ConfigurationProperties(prefix = "secure-publish")
    public static class SecurePublishProperties {

        /** Enable .spkg scanning and admission. */
        private boolean enabled = false;

        /** Incoming signed package directory. */
        private String incomingDir = "D:/SecurePublish/incoming";

        /** Verified package archive directory. */
        private String verifiedDir = "D:/SecurePublish/verified";

        /** Sigma runtime directory that should contain only verified payloads. */
        private String runtimeDir = "D:/SecurePublish/runtime";

        /** Rejected package directory. */
        private String rejectedDir = "D:/SecurePublish/rejected";

        /** Temporary extraction directory. */
        private String tempDir = "D:/SecurePublish/temp";

        /** Scheduled scan interval. */
        private long scanIntervalMs = 2000L;

        /** Max accepted package size. */
        private int maxPackageSizeMb = 500;

        /** Whether test package creation API is enabled. */
        private boolean testPackageEnabled = false;

        /** Enable signing-machine APIs. Keep disabled on verifier client hosts. */
        private boolean signerEnabled = false;

        /** Directory watched manually by signing-machine scan API. */
        private String signerInputDir = "D:/SecurePublish/signer/incoming";

        /** Directory where signing-machine writes .spkg packages. */
        private String signerOutputDir = "D:/SecurePublish/signer/outgoing";

        /** Directory where signing-machine archives successfully signed sources. */
        private String signerArchiveDir = "D:/SecurePublish/signer/archive";

        /** Directory where signing-machine moves rejected sources. */
        private String signerRejectedDir = "D:/SecurePublish/signer/rejected";

        /** Publisher value written into manifest. */
        private String publisher = "secure-publish-signer";

        /** Enable AI audit before signing. */
        private boolean signerAuditEnabled = true;

        /** Local model pre-audit API, for example http://host:8065/content/detection/pre-audit. */
        private String signerAuditUrl = "";

        /** Policy when signer pre-audit fails: reject or allow. */
        private String signerAuditFailPolicy = "reject";

        /** Signer audit request timeout. */
        private int signerAuditTimeoutMs = 120000;

        /** Policy version written into .spkg manifest. */
        private String signerAuditPolicyVersion = "v1";

        /** Package validity window after signing. */
        private long packageExpireMs = 7L * 24L * 60L * 60L * 1000L;

        /** Verifier rejects packages without PASS audit result. */
        private boolean requireAuditPass = true;

        /** Signature provider for .spkg: rsa or vauth-envelope. */
        private String signatureProvider = "rsa";

        /** Key id used by database-backed demo signing. */
        private String signatureKeyId = "default";

        /** RSA private key used by test package creation. */
        private String signerPrivateKeyPath = "D:/SecurePublish/keys/spkg-private.pem";

        /** RSA public key used by package verification. */
        private String verifierPublicKeyPath = "D:/SecurePublish/keys/spkg-public.pem";

        /** Comma separated allowed payload extensions. Blank means built-in list. */
        private String allowedExtensions = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getIncomingDir() { return incomingDir; }
        public void setIncomingDir(String incomingDir) { this.incomingDir = incomingDir; }

        public String getVerifiedDir() { return verifiedDir; }
        public void setVerifiedDir(String verifiedDir) { this.verifiedDir = verifiedDir; }

        public String getRuntimeDir() { return runtimeDir; }
        public void setRuntimeDir(String runtimeDir) { this.runtimeDir = runtimeDir; }

        public String getRejectedDir() { return rejectedDir; }
        public void setRejectedDir(String rejectedDir) { this.rejectedDir = rejectedDir; }

        public String getTempDir() { return tempDir; }
        public void setTempDir(String tempDir) { this.tempDir = tempDir; }

        public long getScanIntervalMs() { return scanIntervalMs; }
        public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }

        public int getMaxPackageSizeMb() { return maxPackageSizeMb; }
        public void setMaxPackageSizeMb(int maxPackageSizeMb) { this.maxPackageSizeMb = maxPackageSizeMb; }

        public boolean isTestPackageEnabled() { return testPackageEnabled; }
        public void setTestPackageEnabled(boolean testPackageEnabled) { this.testPackageEnabled = testPackageEnabled; }

        public boolean isSignerEnabled() { return signerEnabled; }
        public void setSignerEnabled(boolean signerEnabled) { this.signerEnabled = signerEnabled; }

        public String getSignerInputDir() { return signerInputDir; }
        public void setSignerInputDir(String signerInputDir) { this.signerInputDir = signerInputDir; }

        public String getSignerOutputDir() { return signerOutputDir; }
        public void setSignerOutputDir(String signerOutputDir) { this.signerOutputDir = signerOutputDir; }

        public String getSignerArchiveDir() { return signerArchiveDir; }
        public void setSignerArchiveDir(String signerArchiveDir) { this.signerArchiveDir = signerArchiveDir; }

        public String getSignerRejectedDir() { return signerRejectedDir; }
        public void setSignerRejectedDir(String signerRejectedDir) { this.signerRejectedDir = signerRejectedDir; }

        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }

        public boolean isSignerAuditEnabled() { return signerAuditEnabled; }
        public void setSignerAuditEnabled(boolean signerAuditEnabled) { this.signerAuditEnabled = signerAuditEnabled; }

        public String getSignerAuditUrl() { return signerAuditUrl; }
        public void setSignerAuditUrl(String signerAuditUrl) { this.signerAuditUrl = signerAuditUrl; }

        public String getSignerAuditFailPolicy() { return signerAuditFailPolicy; }
        public void setSignerAuditFailPolicy(String signerAuditFailPolicy) { this.signerAuditFailPolicy = signerAuditFailPolicy; }

        public int getSignerAuditTimeoutMs() { return signerAuditTimeoutMs; }
        public void setSignerAuditTimeoutMs(int signerAuditTimeoutMs) { this.signerAuditTimeoutMs = signerAuditTimeoutMs; }

        public String getSignerAuditPolicyVersion() { return signerAuditPolicyVersion; }
        public void setSignerAuditPolicyVersion(String signerAuditPolicyVersion) { this.signerAuditPolicyVersion = signerAuditPolicyVersion; }

        public long getPackageExpireMs() { return packageExpireMs; }
        public void setPackageExpireMs(long packageExpireMs) { this.packageExpireMs = packageExpireMs; }

        public boolean isRequireAuditPass() { return requireAuditPass; }
        public void setRequireAuditPass(boolean requireAuditPass) { this.requireAuditPass = requireAuditPass; }

        public String getSignatureProvider() { return signatureProvider; }
        public void setSignatureProvider(String signatureProvider) { this.signatureProvider = signatureProvider; }

        public String getSignatureKeyId() { return signatureKeyId; }
        public void setSignatureKeyId(String signatureKeyId) { this.signatureKeyId = signatureKeyId; }

        public String getSignerPrivateKeyPath() { return signerPrivateKeyPath; }
        public void setSignerPrivateKeyPath(String signerPrivateKeyPath) { this.signerPrivateKeyPath = signerPrivateKeyPath; }

        public String getVerifierPublicKeyPath() { return verifierPublicKeyPath; }
        public void setVerifierPublicKeyPath(String verifierPublicKeyPath) { this.verifierPublicKeyPath = verifierPublicKeyPath; }

        public String getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(String allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    }

    /**
     * Gateway-issued content release token config.
     */
    @Configuration
    @ConfigurationProperties(prefix = "content-release-token")
    public static class ContentReleaseTokenProperties {

        /** Enable token request and local consistency verification. */
        private boolean enabled = false;

        /** Publish-gateway token issue API. */
        private String gatewayIssueUrl = "";

        /** Token TTL requested from gateway. */
        private long tokenTtlMs = 600000L;

        /** Client id bound into the token. Blank falls back to source IP. */
        private String clientId = "";

        /** Policy version label bound into the token. */
        private String policyVersion = "v1";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getGatewayIssueUrl() { return gatewayIssueUrl; }
        public void setGatewayIssueUrl(String gatewayIssueUrl) { this.gatewayIssueUrl = gatewayIssueUrl; }

        public long getTokenTtlMs() { return tokenTtlMs; }
        public void setTokenTtlMs(long tokenTtlMs) { this.tokenTtlMs = tokenTtlMs; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getPolicyVersion() { return policyVersion; }
        public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    }
}

