package com.publishgateway.udpproxy.secure;

public interface SecurePublishIngressService {

    SecurePublishIngressDecision inspect(String ruleId, Long chainId, byte[] data, String sourceIp);
}
