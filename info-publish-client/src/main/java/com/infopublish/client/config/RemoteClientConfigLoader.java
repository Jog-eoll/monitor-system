package com.infopublish.client.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infopublish.client.jna.VAuthSDKAdapter;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.MonitorPlatformClient;
import com.infopublish.client.config.AppConfig.ProcessBindProperties;
import com.monitorplatform.registry.client.config.RegistryClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Component
public class RemoteClientConfigLoader implements ApplicationRunner, Ordered {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private RegistryClientProperties registryClientProperties;

    @Resource
    private MonitorPlatformClient monitorPlatformClient;

    @Resource
    private ClientAuthService clientAuthService;

    @Resource
    private VAuthSDKAdapter vAuthSDKAdapter;

    @Resource
    private ProcessBindProperties processBindProperties;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private Environment environment;

    private volatile boolean configLoaded;
    private volatile boolean configReady;
    private volatile String lastError;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 150;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(ApplicationArguments args) {
        reload();
    }

    @SuppressWarnings("unchecked")
    public synchronized boolean reload() {
        String url = null;
        configLoaded = false;
        configReady = environment.getProperty("client.remote-config.ready", Boolean.class, Boolean.FALSE);
        lastError = environment.getProperty("client.remote-config.last-error");

        String clientId = monitorPlatformClient.getClientId();
        if (isBlank(clientId)) {
            markNotReady("clientId is blank");
            log.warn("[RemoteConfig] skip pull config: {}", lastError);
            return false;
        }

        url = buildRegistryUrl("/client-config/" + clientId);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !"200".equals(String.valueOf(body.get("code")))) {
                markNotReady("config not available");
                log.warn("[RemoteConfig] {}, url={}, response={}", lastError, url, body);
                return false;
            }

            Object dataObject = body.get("data");
            if (!(dataObject instanceof Map)) {
                markNotReady("invalid response data");
                log.warn("[RemoteConfig] {}: {}", lastError, dataObject);
                return false;
            }

            Map<String, Object> data = (Map<String, Object>) dataObject;
            Object configObject = firstValue(data, "config", "configContent", "config_content");
            Map<String, Object> config = parseConfigContent(configObject);
            if (config == null || config.isEmpty()) {
                markNotReady("config content is empty or invalid");
                log.warn("[RemoteConfig] {}: clientId={}", lastError, clientId);
                return false;
            }

            String validationError = validateRequiredConfig(config);
            if (validationError != null) {
                markNotReady(validationError);
                log.warn("[RemoteConfig] config is incomplete: clientId={}, version={}, reason={}",
                        clientId, data.get("configVersion"), validationError);
                return false;
            }

            applyConfig(config);
            configLoaded = true;
            configReady = true;
            lastError = null;
            log.info("[RemoteConfig] config applied: clientId={}, version={}", clientId, data.get("configVersion"));
            return true;
        } catch (Exception e) {
            markNotReady("pull config failed: " + e.getMessage());
            log.warn("[RemoteConfig] pull config failed: url={}, error={}", url, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigContent(Object configObject) {
        if (configObject instanceof Map) {
            return (Map<String, Object>) configObject;
        }
        if (configObject instanceof String) {
            String configText = String.valueOf(configObject).trim();
            if (isBlank(configText)) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(configText, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("[RemoteConfig] invalid config json: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    public boolean isConfigLoaded() {
        return configLoaded;
    }

    public boolean isConfigReady() {
        return configReady;
    }

    public String getLastError() {
        return lastError;
    }

    private void applyConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }

        applyString(config, "monitorPlatformUrl", clientAuthService::setMonitorPlatformUrl);
        applyString(config, "monitor-platform-url", clientAuthService::setMonitorPlatformUrl);
        applyString(config, "serverId", clientAuthService::setServerId);
        applyString(config, "server-id", clientAuthService::setServerId);
        applyString(config, "serverCertPath", clientAuthService::setServerCerPath);
        applyString(config, "server-cert-path", clientAuthService::setServerCerPath);
        applyString(config, "clientCertPath", clientAuthService::setClientCerPath);
        applyString(config, "client-cert-path", clientAuthService::setClientCerPath);
        applyString(config, "authId", clientAuthService::setAuthId);
        applyString(config, "auth-id", clientAuthService::setAuthId);
        applyString(config, "password", clientAuthService::setPassword);
        applyString(config, "authId", vAuthSDKAdapter::setAuthId);
        applyString(config, "auth-id", vAuthSDKAdapter::setAuthId);
        applyString(config, "password", vAuthSDKAdapter::setPassword);
        applyString(config, "platformUrl", monitorPlatformClient::setPlatformUrl);
        applyString(config, "platform_url", monitorPlatformClient::setPlatformUrl);
        applyString(config, "monitorPlatformUrl", monitorPlatformClient::setPlatformUrl);
        applyString(config, "monitor-platform-url", monitorPlatformClient::setPlatformUrl);

        Object mockMode = firstValue(config, "mockMode", "mock-mode");
        if (mockMode != null) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(mockMode));
            clientAuthService.setMockMode(enabled);
            vAuthSDKAdapter.setMockMode(enabled);
            vAuthSDKAdapter.refreshRuntimeMode();
        }

        applyProcessBindConfig(config);
    }

    @SuppressWarnings("unchecked")
    private void applyProcessBindConfig(Map<String, Object> config) {
        Object processBindObject = firstValue(config, "processBind", "process-bind");
        if (!(processBindObject instanceof Map)) {
            return;
        }

        Map<String, Object> processBind = (Map<String, Object>) processBindObject;
        Object enabled = processBind.get("enabled");
        if (enabled != null) {
            processBindProperties.setEnabled(Boolean.parseBoolean(String.valueOf(enabled)));
        }

        applyString(processBind, "targetProcessName", processBindProperties::setTargetProcessName);
        applyString(processBind, "target-process-name", processBindProperties::setTargetProcessName);
        applyString(processBind, "targetProcessSearchRoots", processBindProperties::setTargetProcessSearchRoots);
        applyString(processBind, "target-process-search-roots", processBindProperties::setTargetProcessSearchRoots);
        applyString(processBind, "targetProcessPath", processBindProperties::setTargetProcessPath);
        applyString(processBind, "target-process-path", processBindProperties::setTargetProcessPath);
        applyString(processBind, "targetProcessMd5", processBindProperties::setTargetProcessMd5);
        applyString(processBind, "target-process-md5", processBindProperties::setTargetProcessMd5);
        applyBoolean(processBind, "autoStartEnabled", processBindProperties::setAutoStartEnabled);
        applyBoolean(processBind, "auto-start-enabled", processBindProperties::setAutoStartEnabled);
        applyLong(processBind, "startWaitMillis", processBindProperties::setStartWaitMillis);
        applyLong(processBind, "start-wait-millis", processBindProperties::setStartWaitMillis);
        applyLong(processBind, "startPollMillis", processBindProperties::setStartPollMillis);
        applyLong(processBind, "start-poll-millis", processBindProperties::setStartPollMillis);
        applyBoolean(processBind, "guardEnabled", processBindProperties::setGuardEnabled);
        applyBoolean(processBind, "guard-enabled", processBindProperties::setGuardEnabled);
        applyString(processBind, "guardMode", processBindProperties::setGuardMode);
        applyString(processBind, "guard-mode", processBindProperties::setGuardMode);
        applyLong(processBind, "guardCheckIntervalMillis", processBindProperties::setGuardCheckIntervalMillis);
        applyLong(processBind, "guard-check-interval-millis", processBindProperties::setGuardCheckIntervalMillis);
        applyLong(processBind, "pidAliveCacheMillis", processBindProperties::setPidAliveCacheMillis);
        applyLong(processBind, "pid-alive-cache-millis", processBindProperties::setPidAliveCacheMillis);
        applyLong(processBind, "udpTableCacheMillis", processBindProperties::setUdpTableCacheMillis);
        applyLong(processBind, "udp-table-cache-millis", processBindProperties::setUdpTableCacheMillis);
        applyLong(processBind, "integrityCacheMillis", processBindProperties::setIntegrityCacheMillis);
        applyLong(processBind, "integrity-cache-millis", processBindProperties::setIntegrityCacheMillis);
        applyLong(processBind, "integrityHashCacheMillis", processBindProperties::setIntegrityHashCacheMillis);
        applyLong(processBind, "integrity-hash-cache-millis", processBindProperties::setIntegrityHashCacheMillis);

        log.info("[RemoteConfig] processBind applied: enabled={}, displayName={}, searchRoots={}, pathCheck={}, md5Check={}, autoStart={}, guardMode={}",
                processBindProperties.isEnabled(),
                processBindProperties.getTargetProcessName(),
                processBindProperties.getTargetProcessSearchRoots(),
                !isBlank(processBindProperties.getTargetProcessPath()),
                !isBlank(processBindProperties.getTargetProcessMd5()),
                processBindProperties.isAutoStartEnabled(),
                processBindProperties.getGuardMode());
    }

    private String validateRequiredConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "config content is empty";
        }

        String monitorPlatformUrl = firstString(config,
                "monitorPlatformUrl", "monitor-platform-url", "platformUrl", "platform_url");
        if (isBlank(monitorPlatformUrl)) {
            return "monitorPlatformUrl is required";
        }
        if (!monitorPlatformUrl.startsWith("http://") && !monitorPlatformUrl.startsWith("https://")) {
            return "monitorPlatformUrl must start with http:// or https://";
        }

        boolean mockMode = false;
        Object mockModeValue = firstValue(config, "mockMode", "mock-mode");
        if (mockModeValue != null) {
            mockMode = Boolean.parseBoolean(String.valueOf(mockModeValue));
        }
        if (mockMode) {
            return null;
        }

        if (isBlank(firstString(config, "serverId", "server-id"))) {
            return "serverId is required";
        }
        if (isBlank(firstString(config, "authId", "auth-id"))) {
            return "authId is required";
        }
        if (isBlank(firstString(config, "password"))) {
            return "password is required";
        }
        return null;
    }

    private void applyString(Map<String, Object> config, String key, ValueConsumer consumer) {
        Object value = config.get(key);
        if (value == null || isBlank(String.valueOf(value))) {
            return;
        }
        consumer.accept(String.valueOf(value).trim());
    }

    private void applyLong(Map<String, Object> config, String key, LongValueConsumer consumer) {
        Object value = config.get(key);
        if (value == null || isBlank(String.valueOf(value))) {
            return;
        }
        try {
            consumer.accept(Long.parseLong(String.valueOf(value).trim()));
        } catch (NumberFormatException e) {
            log.warn("[RemoteConfig] invalid long config: key={}, value={}", key, value);
        }
    }

    private void applyBoolean(Map<String, Object> config, String key, BooleanValueConsumer consumer) {
        Object value = config.get(key);
        if (value == null || isBlank(String.valueOf(value))) {
            return;
        }
        consumer.accept(Boolean.parseBoolean(String.valueOf(value).trim()));
    }

    private Object firstValue(Map<String, Object> config, String firstKey, String secondKey) {
        Object value = config.get(firstKey);
        return value == null ? config.get(secondKey) : value;
    }

    private Object firstValue(Map<String, Object> config, String... keys) {
        if (config == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> config, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && !isBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private void markNotReady(String error) {
        configLoaded = true;
        configReady = false;
        lastError = error;
    }

    private String buildRegistryUrl(String path) {
        String serverAddr = registryClientProperties.getServerAddr();
        String baseUrl = serverAddr.startsWith("http://") || serverAddr.startsWith("https://")
                ? serverAddr : "http://" + serverAddr;
        String apiPrefix = normalizePath(registryClientProperties.getApiPrefix(), "/registry");
        return baseUrl + apiPrefix + normalizePath(path, "");
    }

    private String normalizePath(String path, String defaultPath) {
        String value = path;
        if (value == null || value.trim().isEmpty()) {
            value = defaultPath;
        }
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        value = value.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private interface ValueConsumer {
        void accept(String value);
    }

    private interface LongValueConsumer {
        void accept(long value);
    }

    private interface BooleanValueConsumer {
        void accept(boolean value);
    }
}
