package com.infopublish.client.service;

import java.util.Map;

public interface ClientRelayFileSignatureService {

    void observePacket(String sourceIp, int sourcePort, String targetIp, int targetPort, byte[] udpPayload);

    Map<String, Object> getStatus();

    void clear();
}
