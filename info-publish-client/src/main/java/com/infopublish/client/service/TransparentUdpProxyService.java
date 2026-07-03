package com.infopublish.client.service;

import java.util.Map;

/**
 * WinDivert transparent UDP relay service.
 */
public interface TransparentUdpProxyService {

    Map<String, Object> getStatus();

    void clear();
}
