package com.publishgateway.udpproxy.secure;

import java.util.Map;

public interface SecurePublishVerifierService {

    SecurePublishVerifyResult verifyPackage(byte[] packageBytes, String packageName, String sourceIp, Long chainId);

    Map<String, Object> getStatus();

    void clearStatus();
}
