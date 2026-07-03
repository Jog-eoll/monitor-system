package com.publishgateway.udpproxy.service;

/**
 * Isolated SVAC file-content crypto operations for diagnostics.
 *
 * <p>This service is deliberately separate from the normal {@link CryptoService}
 * path so enabling file-level SVAC tests cannot change existing publish/control
 * encryption behavior.</p>
 */
public interface SvacFileCryptoService {

    byte[] encryptFileData(byte[] data);

    byte[] decryptFileData(byte[] data);

    byte[] encryptPackData(byte[] data);

    byte[] decryptPackData(byte[] data);

    boolean isReady();
}
