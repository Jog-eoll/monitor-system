package com.monitorplatform.registry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorplatform.registry.dto.ClientConfigDTO;
import com.monitorplatform.registry.entity.ClientConfig;
import com.monitorplatform.registry.entity.ServiceInstance;
import com.monitorplatform.registry.mapper.ClientConfigMapper;
import com.monitorplatform.registry.service.ClientConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class ClientConfigServiceImpl implements ClientConfigService {

    @Resource
    private ClientConfigMapper clientConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultConfig(ServiceInstance instance) {
        if (instance == null || isBlank(instance.getClientId())) {
            return;
        }
        try {
            ClientConfig existing = getByClientId(instance.getClientId());
            if (existing != null) {
                ensureConfigKeys(existing, instance);
                return;
            }

            ClientConfig config = new ClientConfig();
            config.setClientId(instance.getClientId().trim());
            config.setServiceName(instance.getServiceName());
            config.setConfigContent(toJson(defaultConfig()));
            config.setConfigVersion(1L);
            config.setEnabled(Boolean.TRUE);
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            clientConfigMapper.insert(config);
            log.info("created default client config: clientId={}", instance.getClientId());
        } catch (Exception e) {
            log.warn("skip default client config init: clientId={}, error={}",
                    instance.getClientId(), e.getMessage());
        }
    }

    @Override
    public ClientConfig getByClientId(String clientId) {
        if (isBlank(clientId)) {
            return null;
        }
        LambdaQueryWrapper<ClientConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClientConfig::getClientId, clientId.trim());
        return clientConfigMapper.selectOne(wrapper);
    }

    @Override
    public Map<String, Object> getConfigMap(String clientId) {
        ClientConfig clientConfig = getByClientId(clientId);
        if (clientConfig == null || !Boolean.TRUE.equals(clientConfig.getEnabled())) {
            return null;
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clientId", clientConfig.getClientId());
            result.put("serviceName", clientConfig.getServiceName());
            result.put("configVersion", clientConfig.getConfigVersion());
            result.put("config", parseConfig(clientConfig.getConfigContent()));
            result.put("updateTime", clientConfig.getUpdateTime());
            return result;
        } catch (Exception e) {
            log.error("parse client config failed: clientId={}", clientId, e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientConfig saveConfig(ClientConfigDTO dto) {
        if (dto == null || isBlank(dto.getClientId())) {
            throw new IllegalArgumentException("clientId cannot be blank");
        }

        ClientConfig existing = getByClientId(dto.getClientId());
        ClientConfig target = existing == null ? new ClientConfig() : existing;
        target.setClientId(dto.getClientId().trim());
        target.setServiceName(dto.getServiceName());
        target.setConfigContent(toJson(dto.getConfig()));
        target.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        target.setUpdateTime(LocalDateTime.now());

        if (existing == null) {
            target.setConfigVersion(1L);
            target.setCreateTime(LocalDateTime.now());
            clientConfigMapper.insert(target);
        } else {
            Long currentVersion = existing.getConfigVersion() == null ? 1L : existing.getConfigVersion();
            target.setConfigVersion(currentVersion + 1);
            clientConfigMapper.updateById(target);
        }
        return target;
    }

    private Map<String, Object> parseConfig(String configContent) throws Exception {
        if (isBlank(configContent)) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(configContent, new TypeReference<Map<String, Object>>() {});
    }

    private void ensureConfigKeys(ClientConfig existing, ServiceInstance instance) throws Exception {
        Map<String, Object> config = parseConfig(existing.getConfigContent());
        Map<String, Object> defaults = defaultConfig();
        boolean changed = false;

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!config.containsKey(entry.getKey())) {
                config.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        if (isBlank(existing.getServiceName()) && instance != null && !isBlank(instance.getServiceName())) {
            existing.setServiceName(instance.getServiceName());
            changed = true;
        }

        if (!changed) {
            return;
        }

        Long currentVersion = existing.getConfigVersion() == null ? 1L : existing.getConfigVersion();
        existing.setConfigContent(toJson(config));
        existing.setConfigVersion(currentVersion + 1);
        existing.setUpdateTime(LocalDateTime.now());
        clientConfigMapper.updateById(existing);
        log.info("filled missing client config keys: clientId={}", existing.getClientId());
    }

    private String toJson(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config == null ? new LinkedHashMap<>() : config);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid config json", e);
        }
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("monitorPlatformUrl", "");
        config.put("serverId", "");
        config.put("serverCertPath", "");
        config.put("clientCertPath", "");
        config.put("authId", "");
        config.put("password", "");
        config.put("mockMode", Boolean.FALSE);
        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
