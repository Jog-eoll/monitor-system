package com.publishgateway.udpproxy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Source validation switch kept for compatibility.
 * Gateway no longer calls the client for per-packet PID validation.
 */
@Slf4j
@Service
public class ClientValidationService {

    @Value("${security.per-packet-client-validation-enabled:false}")
    private boolean perPacketClientValidationEnabled;

    public boolean validateSource(String senderIp, int senderPort, String sourceIp) {
        if (!perPacketClientValidationEnabled) {
            log.debug("[source-validation] per-packet client callback disabled, allow {}:{}", senderIp, senderPort);
            return true;
        }
        log.debug("[source-validation] per-packet client callback disabled, allow {}:{}", senderIp, senderPort);
        return true;
    }
}
