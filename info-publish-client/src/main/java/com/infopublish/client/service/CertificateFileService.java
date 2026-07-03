package com.infopublish.client.service;

import java.nio.file.Path;

public interface CertificateFileService {

    String loadCertificateContent(String configuredPath);

    Path saveLocalCertificate(Path plainTargetPath, byte[] certificateBytes);

    boolean isEncryptionEnabled();
}
