package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ACK proxy learning and replay configuration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "secure-publish.ack-proxy")
public class AckProxyProperties {

    /**
     * Master switch for ACK proxy features.
     */
    private boolean enabled = false;

    /**
     * Current mode: off, learn, simulate, proxy, enforce.
     */
    private String mode = "off";

    /**
     * Whether real request/ACK samples should be recorded.
     */
    private boolean recordRealAck = true;

    /**
     * Whether simulated ACK should be compared with the real ACK.
     */
    private boolean compareSimulatedAck = false;

    /**
     * Command pairs included in simulate comparison, formatted as MAIN:SUB hex.
     */
    private List<String> simulateCommands = new ArrayList<>(Arrays.asList("02:08", "02:02"));

    /**
     * Whether strong isolation is enabled in enforce mode.
     */
    private boolean isolationEnabled = false;

    /**
     * Max bytes held in one isolation session.
     */
    private long isolationMaxSessionBytes = 500L * 1024L * 1024L;

    /**
     * Isolation session timeout.
     */
    private long isolationSessionTimeoutMs = 120000L;

    /**
     * Max in-memory completed samples kept for API inspection.
     */
    private int maxSamples = 2000;

    /**
     * Max pending requests kept per runtime before eviction.
     */
    private int maxPendingRequests = 10000;

    /**
     * Pending request timeout used when matching real ACKs.
     */
    private long pendingTimeoutMs = 30000L;

    /**
     * Max packet bytes converted to hex per request/response.
     */
    private int maxHexBytes = 4096;

    public boolean isLearnMode() {
        return "learn".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public boolean isSimulateMode() {
        return "simulate".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public boolean isProxyMode() {
        return "proxy".equalsIgnoreCase(mode == null ? "" : mode.trim())
                || "replay".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public boolean isEnforceMode() {
        return "enforce".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public boolean isLearningEnabled() {
        return enabled && recordRealAck && (isLearnMode() || isSimulateMode() || isProxyMode() || isEnforceMode());
    }

    public boolean isCompareEnabled() {
        return enabled && compareSimulatedAck && (isSimulateMode() || isProxyMode() || isEnforceMode());
    }

    public boolean isProxyAckEnabled() {
        return enabled && (isProxyMode() || isEnforceMode());
    }

    public boolean isIsolationActive() {
        return enabled && isolationEnabled && isEnforceMode();
    }
}
