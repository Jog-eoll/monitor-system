package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Windows client relay receiver config.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "client-relay")
public class ClientRelayProperties {

    private boolean enabled = false;

    private int relayPort = 18092;

    private List<String> trustedClientIps = new ArrayList<>();

    private boolean verifySignature = false;

    private String signatureSecret = "";

    private boolean rejectDirectUdp = false;
}
