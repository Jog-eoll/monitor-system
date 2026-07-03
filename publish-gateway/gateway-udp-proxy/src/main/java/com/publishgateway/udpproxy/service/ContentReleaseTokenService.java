package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.relay.RelayPacketCodec;

import java.util.Map;

public interface ContentReleaseTokenService {

    ContentReleaseTokenIssueResponse issue(ContentReleaseTokenIssueRequest request);

    ContentReleaseTokenVerifyResult verifyRelayPacket(RelayPacketCodec.RelayPacket packet, UdpProxyRule rule);

    Map<String, Object> getStatus();

    void clearStatus();
}
