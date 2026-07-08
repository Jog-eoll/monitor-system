package com.infopublish.client.service.signature;

import com.infopublish.client.jna.VAuthSDKAdapter;
import com.infopublish.client.service.CertificateFileService;
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
public class ClientFileEnvelopeSigner {

    private final VAuthSDKAdapter sdkAdapter;
    private final ClientAuthService clientAuthService;
    private final UkeyLifecycleManager lifecycleManager;
    private final CertificateFileService certificateFileService;

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

        int handle = -1;
        try {
            handle = sdkAdapter.openUkey(ukeyPath, clientAuthService.getPassword(), clientAuthService.getAuthId());
            if (handle < 0) {
                throw new IllegalStateException("open ukey failed: " + handle);
            }

            if (!clientAuthService.isMockMode()) {
                String serverCert = certificateFileService.loadCertificateContent(clientAuthService.getServerCerPath());
                boolean setOk = sdkAdapter.setAuthServerInfo(handle, clientAuthService.getServerId(), serverCert);
                if (!setOk) {
                    throw new IllegalStateException("set auth server info failed");
                }
            }
            return sdkAdapter.encryptData(handle, true, manifestBytes);
        } finally {
            if (handle >= 0) {
                try {
                    sdkAdapter.closeHandle(handle);
                } catch (Exception e) {
                    log.debug("[RelayFileSignature] close ukey handle failed: {}", e.getMessage());
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
