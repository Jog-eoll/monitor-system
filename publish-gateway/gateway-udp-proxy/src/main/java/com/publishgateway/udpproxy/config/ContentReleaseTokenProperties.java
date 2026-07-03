package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway-issued content release token config.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "content-release-token")
public class ContentReleaseTokenProperties {

    private boolean enabled = false;

    private boolean requireToken = false;

    private long tokenTtlMs = 600_000L;

    private long cacheTtlMs = 600_000L;

    private int maxFileSizeMb = 500;

    private String issuer = "publish-gateway";

    private String policyVersion = "v1";
}
