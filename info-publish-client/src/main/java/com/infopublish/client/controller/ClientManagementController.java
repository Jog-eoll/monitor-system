package com.infopublish.client.controller;

import com.infopublish.client.common.Result;
import com.infopublish.client.entity.GatewayConfig;
import com.infopublish.client.entity.OperationLog;
import com.infopublish.client.entity.UdpProxyRule;
import com.infopublish.client.jna.VAuthSDKAdapter;
import com.infopublish.client.jna.VAuthSDKMock;
import com.infopublish.client.manager.UdpProxyRuleManager;
import com.infopublish.client.service.*;
import com.monitorplatform.registry.client.config.RegistryClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信息发布监管客户端 B/S 管理接口
 * 提供配置管理、状态查询、通道管理、Mock模拟、退出注销等功能
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ClientManagementController {

    @Resource
    private UkeyLifecycleManager lifecycleManager;

    @Resource
    private UkeyAuthenticationHandler ukeyAuthHandler;

    @Resource
    private GatewayService gatewayService;

    @Resource
    private PlatformConfigService platformConfigService;

    @Resource
    private OperationLogService operationLogService;

    @Resource
    private MonitorPlatformClient monitorPlatformClient;

    @Resource
    private ClientAuthService clientAuthService;

    @Resource
    private VAuthSDKAdapter vAuthSDKAdapter;

    @Resource
    private UdpProxyRuleManager proxyRuleManager;

    @Resource
    private RegistryClientProperties registryClientProperties;

    @Resource
    private org.springframework.web.client.RestTemplate restTemplate;

    @Value("${vauth.mock-mode:true}")
    private boolean mockMode;

    // ========== 状态查询 ==========

    /**
     * 获取综合状态概览
     */
    @GetMapping("/status/overview")
    public Result<Map<String, Object>> getStatusOverview() {
        UkeyLifecycleManager.StatusOverview status = lifecycleManager.getStatusOverview();

        Map<String, Object> data = new HashMap<>();
        data.put("state", status.state.name());
        data.put("stateDescription", status.stateDescription);
        data.put("certSerialNo", status.certSerialNo);
        data.put("ukeyPath", status.ukeyPath);
        data.put("lastError", status.lastError);
        data.put("authenticated", status.authenticated);
        data.put("channelActive", status.channelActive);
        data.put("mockMode", mockMode);

        // 规则状态
        List<UdpProxyRule> rules = proxyRuleManager.listAllRules();
        long enabledCount = rules.stream().filter(r -> "ENABLED".equals(r.getStatus())).count();
        Map<String, Object> ruleStatus = new HashMap<>();
        ruleStatus.put("totalRules", rules.size());
        ruleStatus.put("enabledRules", enabledCount);
        ruleStatus.put("disabledRules", rules.size() - enabledCount);
        data.put("ruleStatus", ruleStatus);

        // 通道状态（手动调试用）
        Map<String, Object> channelStatus = gatewayService.getChannelStatus();
        data.put("channelStatus", channelStatus);

        return Result.ok(data);
    }

    /**
     * 获取认证状态
     */
    @GetMapping("/auth/status")
    public Result<Map<String, Object>> getAuthStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("authenticated", clientAuthService.isAuthenticated());
        data.put("state", lifecycleManager.getCurrentState().name());
        data.put("certSerialNo", lifecycleManager.getCurrentCertSerialNo());
        return Result.ok(data);
    }

    // ========== 配置管理 ==========

    /**
     * 获取管控平台配置
     */
    @GetMapping("/config/platform")
    public Result<Map<String, String>> getPlatformConfig() {
        Map<String, String> configs = platformConfigService.getAllConfigs();
        return Result.ok(configs);
    }

    /**
     * 更新管控平台配置
     */
    @PutMapping("/config/platform")
    public Result<String> updatePlatformConfig(@RequestBody Map<String, String> configs) {
        platformConfigService.updateConfigs(configs);

        // 动态更新MonitorPlatformClient的地址
        String newUrl = configs.get("platform_url");
        if (newUrl != null && !newUrl.isEmpty()) {
            monitorPlatformClient.setPlatformUrl(newUrl);
        }
        String newClientId = configs.get("client_id");
        if (newClientId != null && !newClientId.isEmpty()) {
            monitorPlatformClient.setClientId(newClientId);
        }

        operationLogService.logSuccess("CONFIG_UPDATE", "更新平台配置: " + configs.keySet());
        return Result.ok("配置更新成功", null);
    }

    /**
     * 获取网关配置
     */
    @GetMapping("/config/gateway")
    public Result<GatewayConfig> getGatewayConfig() {
        GatewayConfig config = gatewayService.getCurrentConfig();
        return Result.ok(config);
    }

    // ========== 转发通道管理 ==========

    /**
     * 创建转发通道
     */
    @PostMapping("/channel/create")
    public Result<Map<String, Object>> createChannel(@Validated @RequestBody GatewayConfig config) {
        if (!lifecycleManager.isAuthenticated()) {
            return Result.error("请先完成UKey认证");
        }

        Map<String, Object> result = gatewayService.createForwardChannel(config);
        boolean success = (boolean) result.getOrDefault("success", false);

        if (success) {
            lifecycleManager.onChannelActive();
            operationLogService.logSuccess(OperationLogService.EVENT_CHANNEL_CREATE,
                    "通道创建成功: " + config.getChannelName());
        } else {
            operationLogService.logFail(OperationLogService.EVENT_CHANNEL_CREATE,
                    "通道创建失败: " + result.get("message"));
        }

        return success ? Result.ok(result) : Result.error((String) result.get("message"));
    }

    /**
     * 停止转发通道
     */
    @PostMapping("/channel/stop")
    public Result<Map<String, Object>> stopChannel() {
        Map<String, Object> result = gatewayService.stopForwardChannel();
        boolean success = (boolean) result.getOrDefault("success", false);

        if (success) {
            operationLogService.logSuccess(OperationLogService.EVENT_CHANNEL_STOP, "通道已停止");
        }

        return success ? Result.ok(result) : Result.error((String) result.get("message"));
    }

    /**
     * 查询通道状态
     */
    @GetMapping("/channel/status")
    public Result<Map<String, Object>> getChannelStatus() {
        Map<String, Object> status = gatewayService.getChannelStatus();
        return Result.ok(status);
    }

    /**
     * 测试网关连通性
     */
    @PostMapping("/channel/test")
    public Result<Map<String, Object>> testGatewayConnection(@RequestBody GatewayConfig config) {
        Map<String, Object> result = gatewayService.testGatewayConnection(config);
        boolean success = (boolean) result.getOrDefault("success", false);
        return success ? Result.ok(result) : Result.error((String) result.get("message"));
    }

    // ========== Mock 模拟操作 ==========

    /**
     * 模拟UKey插入
     */
    @PostMapping("/ukey/simulate/insert")
    public Result<String> simulateUkeyInsert(@RequestParam(defaultValue = "MockUKey-001") String deviceName) {
        if (!mockMode) {
            return Result.error("非Mock模式，无法模拟UKey操作");
        }

        log.info("模拟UKey插入: {}", deviceName);
        try {
            // 通过反射或直接访问mock SDK触发插入事件
            java.lang.reflect.Field mockField = VAuthSDKAdapter.class.getDeclaredField("mockSdk");
            mockField.setAccessible(true);
            VAuthSDKMock mockSdk = (VAuthSDKMock) mockField.get(vAuthSDKAdapter);
            if (mockSdk != null) {
                mockSdk.simulateUkeyInsert(deviceName);
                operationLogService.logSuccess(OperationLogService.EVENT_UKEY_INSERT, "模拟UKey插入: " + deviceName);
                return Result.ok("模拟UKey插入成功", null);
            }
            return Result.error("Mock SDK未初始化");
        } catch (Exception e) {
            log.error("模拟UKey插入失败", e);
            return Result.error("模拟失败: " + e.getMessage());
        }
    }

    /**
     * 模拟UKey拔出
     */
    @PostMapping("/ukey/simulate/remove")
    public Result<String> simulateUkeyRemove(@RequestParam(defaultValue = "MockUKey-001") String deviceName) {
        if (!mockMode) {
            return Result.error("非Mock模式，无法模拟UKey操作");
        }

        log.info("模拟UKey拔出: {}", deviceName);
        try {
            java.lang.reflect.Field mockField = VAuthSDKAdapter.class.getDeclaredField("mockSdk");
            mockField.setAccessible(true);
            VAuthSDKMock mockSdk = (VAuthSDKMock) mockField.get(vAuthSDKAdapter);
            if (mockSdk != null) {
                mockSdk.simulateUkeyRemove(deviceName);
                operationLogService.logSuccess(OperationLogService.EVENT_UKEY_REMOVE, "模拟UKey拔出: " + deviceName);
                return Result.ok("模拟UKey拔出成功", null);
            }
            return Result.error("Mock SDK未初始化");
        } catch (Exception e) {
            log.error("模拟UKey拔出失败", e);
            return Result.error("模拟失败: " + e.getMessage());
        }
    }

    // ========== 退出/注销 ==========

    /**
     * 主动退出/注销
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        ukeyAuthHandler.logout();
        operationLogService.logSuccess(OperationLogService.EVENT_LOGOUT, "用户主动退出");
        return Result.ok("已退出，后台服务继续运行", null);
    }

    // ========== 操作日志 ==========

    /**
     * 查询最近的操作日志
     */
    @GetMapping("/log/recent")
    public Result<List<OperationLog>> getRecentLogs(@RequestParam(defaultValue = "50") int limit) {
        List<OperationLog> logs = operationLogService.getRecentLogs(limit);
        return Result.ok(logs);
    }

    /**
     * 按事件类型查询日志
     */
    @GetMapping("/log/by-type")
    public Result<List<OperationLog>> getLogsByType(
            @RequestParam String eventType,
            @RequestParam(defaultValue = "20") int limit) {
        List<OperationLog> logs = operationLogService.getLogsByEventType(eventType, limit);
        return Result.ok(logs);
    }

    // ========== vauth 配置管理 ==========

    /**
     * 获取当前 vauth.* 配置（从内存 bean 中读取）
     */
    @GetMapping("/config/vauth")
    public Result<Map<String, Object>> getVauthConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("monitorPlatformUrl", clientAuthService.getMonitorPlatformUrl());
        data.put("serverId",           clientAuthService.getServerId());
        data.put("serverCertPath",     clientAuthService.getServerCerPath());
        data.put("authId",             clientAuthService.getAuthId());
        data.put("clientCertPath",     clientAuthService.getClientCerPath());
        data.put("password",           clientAuthService.getPassword());
        data.put("mockMode",           clientAuthService.isMockMode());
        data.put("clientId",           monitorPlatformClient.getClientId());  // 当前客户端ID
        return Result.ok(data);
    }

    /**
     * 保存 vauth.* 配置
     * 1. 动态更新内存中 ClientAuthService 字段（立即生效）
     * 2. 持久化写回平台注册配置库 registry_client_config
     * 3. 调用管控平台 /cert/bind-client，将客户端证书绑定到当前 client-id
     */
    @PutMapping("/config/vauth")
    public Result<String> saveVauthConfig(@RequestBody Map<String, Object> body) {
        String monitorPlatformUrl = (String) body.get("monitorPlatformUrl");
        String serverId           = (String) body.get("serverId");
        String serverCertPath     = (String) body.get("serverCertPath");
        String authId             = (String) body.get("authId");
        String clientCertPath     = (String) body.get("clientCertPath");
        String password           = (String) body.get("password");
        Object mockModeObj        = body.get("mockMode");
        boolean mockMode = mockModeObj != null && Boolean.parseBoolean(mockModeObj.toString());

        // 1. 动态更新内存
        if (monitorPlatformUrl != null) clientAuthService.setMonitorPlatformUrl(monitorPlatformUrl);
        if (serverId           != null) clientAuthService.setServerId(serverId);
        if (serverCertPath     != null) clientAuthService.setServerCerPath(serverCertPath);
        if (authId             != null) clientAuthService.setAuthId(authId);
        if (clientCertPath     != null) clientAuthService.setClientCerPath(clientCertPath);
        if (password           != null) clientAuthService.setPassword(password);
        clientAuthService.setMockMode(mockMode);

        // 2. Persist to registry_client_config, not local config/application.yml.
        try {
            persistVauthConfigToRegistry(body);
        } catch (Exception e) {
            log.warn("[RemoteConfig] vauth config registry save failed; in-memory values already updated: {}", e.getMessage());
            operationLogService.logFail("CONFIG_VAUTH", "vauth registry config save failed: " + e.getMessage());
            return Result.ok("配置已在内存中生效，但写入平台配置库失败: " + e.getMessage());
        }

        // 3. 将客户端证书（authId对应的客户端证书）绑定到当前 client-id
        String clientId = monitorPlatformClient.getClientId();
        if (authId != null && !authId.isEmpty() && monitorPlatformUrl != null && !monitorPlatformUrl.isEmpty()) {
            String bindResult = bindCertToClient(monitorPlatformUrl, authId, clientId);
            log.info("[vauth配置] 证书绑定结果: {}", bindResult);
            operationLogService.logSuccess("CONFIG_VAUTH", "vauth配置已更新，clientId=" + clientId + "，绑定结果: " + bindResult);
            return Result.ok("vauth 配置保存成功，" + bindResult, null);
        }

        operationLogService.logSuccess("CONFIG_VAUTH", "vauth config updated and saved to registry client config");
        return Result.ok("vauth 配置保存成功", null);
    }

    /**
     * Legacy note removed: vauth config is now persisted to registry_client_config.
     */
    @SuppressWarnings("unchecked")
    /**
     * 调用管控平台 PUT /cert/bind-client，将证书绑定到当前客户端 ID
     */
    private String bindCertToClient(String platformUrl, String certSerialNo, String clientId) {
        try {
            String url = platformUrl + "/cert/bind-client";
            Map<String, String> params = new HashMap<>();
            params.put("certSerialNo", certSerialNo);
            params.put("clientId", clientId);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, String>> entity = new org.springframework.http.HttpEntity<>(params, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.PUT, entity, Map.class).getBody();
            if (resp != null) {
                Object code = resp.get("code");
                if (Integer.valueOf(200).equals(code) || "200".equals(String.valueOf(code))) {
                    return "客户端证书已绑定到 clientId=" + clientId;
                }
                return "绑定请求返回: " + resp.get("message");
            }
            return "绑定请求无响应";
        } catch (Exception e) {
            log.warn("[vauth配置] 证书绑定到管控平台失败（不影响配置保存）: {}", e.getMessage());
            return "证书绑定失败（可稍后在管控平台手动绑定）: " + e.getMessage();
        }
    }

    private void persistVauthConfigToRegistry(Map<String, Object> params) throws IOException {
        String clientId = monitorPlatformClient.getClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IOException("clientId is blank");
        }

        String url = buildRegistryClientConfigUrl(clientId);
        Map<String, Object> config = loadRegistryConfig(url);
        mergeVauthConfig(config, params);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("serviceName", "info-publish-client");
        request.put("enabled", Boolean.TRUE);
        request.put("config", config);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null || !"200".equals(String.valueOf(body.get("code")))) {
            throw new IOException("registry save failed: " + body);
        }
        log.info("[RemoteConfig] vauth config saved to registry: clientId={}", clientId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRegistryConfig(String url) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !"200".equals(String.valueOf(body.get("code")))) {
                return new LinkedHashMap<>();
            }
            Object dataObject = body.get("data");
            if (!(dataObject instanceof Map)) {
                return new LinkedHashMap<>();
            }
            Object configObject = ((Map<String, Object>) dataObject).get("config");
            if (configObject instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) configObject);
            }
        } catch (Exception e) {
            log.warn("[RemoteConfig] load registry config before merge failed, will create new config: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    private void mergeVauthConfig(Map<String, Object> config, Map<String, Object> params) {
        if (config == null || params == null) {
            return;
        }

        Object monUrl = params.get("monitorPlatformUrl");
        Object srvId = params.get("serverId");
        Object srvCrt = params.get("serverCertPath");
        Object aId = params.get("authId");
        Object cliCrt = params.get("clientCertPath");
        Object pwd = params.get("password");
        Object mock = params.get("mockMode");

        putIfNotNull(config, "monitorPlatformUrl", monUrl);
        putIfNotNull(config, "serverId", srvId);
        putIfNotNull(config, "serverCertPath", srvCrt);
        putIfNotNull(config, "authId", aId);
        putIfNotNull(config, "clientCertPath", cliCrt);
        putIfNotNull(config, "password", pwd);
        if (mock != null) {
            config.put("mockMode", Boolean.parseBoolean(String.valueOf(mock)));
        }

        Map<String, Object> vauth = getOrCreateMap(config, "vauth");
        putIfNotNull(vauth, "monitorPlatformUrl", monUrl);
        putIfNotNull(vauth, "serverId", srvId);
        putIfNotNull(vauth, "serverCertPath", srvCrt);
        putIfNotNull(vauth, "authId", aId);
        putIfNotNull(vauth, "clientCertPath", cliCrt);
        putIfNotNull(vauth, "password", pwd);
        if (mock != null) {
            vauth.put("mockMode", Boolean.parseBoolean(String.valueOf(mock)));
        }

        if (monUrl != null) {
            Map<String, Object> monitorPlatform = getOrCreateMap(config, "monitorPlatform");
            monitorPlatform.put("url", monUrl);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        Map<String, Object> child = new LinkedHashMap<>();
        root.put(key, child);
        return child;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (target != null && key != null && value != null) {
            target.put(key, value);
        }
    }

    private String buildRegistryClientConfigUrl(String clientId) {
        String serverAddr = registryClientProperties.getServerAddr();
        String baseUrl = serverAddr != null
                && (serverAddr.startsWith("http://") || serverAddr.startsWith("https://"))
                ? serverAddr : "http://" + serverAddr;
        return baseUrl + normalizePath(registryClientProperties.getApiPrefix(), "/registry")
                + "/client-config/" + clientId;
    }

    private String normalizePath(String path, String defaultPath) {
        String value = path == null || path.trim().isEmpty() ? defaultPath : path.trim();
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

}
