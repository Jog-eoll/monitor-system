package com.monitorplatform.registry.service;

import com.monitorplatform.registry.dto.ClientConfigDTO;
import com.monitorplatform.registry.entity.ClientConfig;
import com.monitorplatform.registry.entity.ServiceInstance;

import java.util.Map;

public interface ClientConfigService {
    void ensureDefaultConfig(ServiceInstance instance);

    ClientConfig getByClientId(String clientId);

    Map<String, Object> getConfigMap(String clientId);

    ClientConfig saveConfig(ClientConfigDTO dto);
}
