package com.gateway.common.service;

/**
 * Isolated SVAC file-content crypto operations for diagnostics.
 *
 * <p>This service is separate from the normal secure-command package
 * encryption path, so tests can validate file-level SVAC without changing the
 * existing GM delivery chain.</p>
 */
public interface SvacFileCryptoService {

    byte[] encryptFileData(byte[] data);

    byte[] decryptFileData(byte[] data);

    byte[] encryptPackData(byte[] data);

    byte[] decryptPackData(byte[] data);

    boolean isReady();
}
