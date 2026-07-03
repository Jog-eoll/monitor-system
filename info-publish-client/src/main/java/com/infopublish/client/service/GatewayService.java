package com.infopublish.client.service;

import com.infopublish.client.entity.GatewayConfig;

import java.util.Map;

public interface GatewayService {

    Map<String, Object> createForwardChannel(GatewayConfig config);

    Map<String, Object> stopForwardChannel();

    Map<String, Object> getChannelStatus();

    boolean isSecureDeliveryReady();

    Map<String, Object> testGatewayConnection(GatewayConfig config);

    GatewayConfig getCurrentConfig();

    Long getCurrentChannelId();
}
