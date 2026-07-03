package com.publishgateway.udpproxy.service;

/**
 * Creates and verifies a signed envelope for file-level relay manifests.
 */
public interface SignedEnvelopeCryptoService {

    byte[] signEnvelope(byte[] data);

    byte[] verifyEnvelope(byte[] signedEnvelope);
}
