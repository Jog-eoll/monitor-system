package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * File-level relay signature config.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "relay-file-signature")
public class RelayFileSignatureProperties {

    private boolean enabled = true;

    private String mode = "audit";

    private boolean verifyOnCompleteOnly = true;

    private boolean failOpenOnSignError = true;

    private long cacheTtlMs = 600_000L;

    private int maxFileSizeMb = 200;

    private boolean preferClientSignature = true;

    private boolean requireClientSignature = false;

    public boolean isAuditMode() {
        return mode == null || "audit".equalsIgnoreCase(mode.trim());
    }

    public boolean isEnforceMode() {
        return "enforce".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }
}
