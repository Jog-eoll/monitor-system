package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sigma Play secure package verification config.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "secure-publish")
public class SecurePublishProperties {

    private boolean enabled = false;

    private String trustStoreDir = "/opt/publish-gateway/trust";

    private List<String> trustedKeyIds = new ArrayList<>();

    private String defaultKeyId = "default";

    private String verifierPublicKeyPath = "";

    private boolean requireAuditPass = true;

    private boolean requireContentAllow = true;

    private boolean requireNotExpired = true;

    private int maxPackageSizeMb = 500;

    private String packageExtensions = "tar,spkg,zip";

    private long assemblyTimeoutMs = 120_000L;

    private long staleGapMs = 30_000L;

    private String rejectedDir = "/opt/publish-gateway/secure-publish/rejected";

    private boolean jpegEnabled = true;

    private String jpegMode = "audit";

    private boolean jpegRequireBlock = false;

    private boolean jpegRequireSignature = true;

    private boolean jpegRequireContentAllow = true;

    private boolean jpegRequireNotExpired = true;

    public boolean isJpegAuditMode() {
        return jpegMode == null || "audit".equalsIgnoreCase(jpegMode.trim());
    }

    public boolean isJpegEnforceMode() {
        return "enforce".equalsIgnoreCase(jpegMode == null ? "" : jpegMode.trim());
    }
}
