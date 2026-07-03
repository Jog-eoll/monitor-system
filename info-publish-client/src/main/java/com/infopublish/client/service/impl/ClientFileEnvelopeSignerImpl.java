package com.infopublish.client.service.impl;

import com.infopublish.client.jna.VAuthSDKAdapter;
import com.infopublish.client.service.ClientFileEnvelopeSigner;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin client-side VAuth envelope signer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientFileEnvelopeSignerImpl implements ClientFileEnvelopeSigner {

    private final ClientAuthService clientAuthService;
    private final UkeyLifecycleManager lifecycleManager;

    public byte[] sign(byte[] manifestBytes) {
        if (manifestBytes == null || manifestBytes.length == 0) {
            throw new IllegalArgumentException("manifest bytes is empty");
        }

        if (!clientAuthService.isMockMode() && !clientAuthService.isAuthenticated()) {
            throw new IllegalStateException("client is not authenticated");
        }

        String ukeyPath = lifecycleManager.getCurrentUkeyPath();
        if (isBlank(ukeyPath) && clientAuthService.isMockMode()) {
            ukeyPath = "/mock/ukey/client";
        }
        if (isBlank(ukeyPath)) {
            throw new IllegalStateException("ukey path is empty");
        }

        log.info("[RelayFileSignature] signing file manifest envelope after auth handshake: path={}, authId={}, bytes={}",
                ukeyPath, clientAuthService.getAuthId(), manifestBytes.length);
        byte[] envelope = clientAuthService.signEnvelopeWithControlPlatform(ukeyPath, manifestBytes);
        log.info("[RelayFileSignature] signed file manifest envelope: inputBytes={}, envelopeBytes={}",
                manifestBytes.length, envelope == null ? 0 : envelope.length);
        return envelope;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
