package com.publishgateway.udpproxy.service;

import java.util.Map;

/**
 * File-level signature audit service for WinDivert relay traffic.
 */
public interface RelayFileSignatureService {

    FileSignatureCheckResult signAndVerify(FileSignatureManifest manifest);

    FileSignatureResult sign(FileSignatureManifest manifest);

    FileSignatureVerifyResult verify(FileSignatureManifest manifest, byte[] signedEnvelope);

    Map<String, Object> acceptClientRecord(ClientFileSignatureRecord record);

    Map<String, Object> getStatus();

    void clearStatus();

    boolean isEnforceMode();
}
