package com.infopublish.client.service.impl;

import com.infopublish.client.jna.VAuthSDKAdapter;
import com.infopublish.client.service.CertificateFileService;
import com.infopublish.client.service.ClientAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 监管程序认证服务
 */
@Slf4j
@Service
public class ClientAuthServiceImpl implements ClientAuthService {

    @Resource
    private VAuthSDKAdapter sdkAdapter;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private CertificateFileService certificateFileService;

    @Value("${vauth.monitor-platform-url:}")
    private String monitorPlatformUrl;

    @Value("${vauth.server-id:}")
    private String serverId;

    @Value("${vauth.server-cert-path:certs/server.cer}")
    private String serverCerPath;

    @Value("${vauth.client-cert-path:certs/client.cer}")
    private String clientCerPath;

    @Value("${vauth.auth-id:}")
    private String authId;

    @Value("${vauth.password:}")
    private String password;

    @Value("${vauth.mock-mode:true}")
    private boolean mockMode;

    /**
     * 认证状态
     */
    private volatile boolean authenticated = false;

    /**
     * 与管控平台进行双向认证
     */
    public boolean authenticateWithControlPlatform(String ukeyPath){
        String validationError = validateRequiredConfig();
        if (validationError != null) {
            log.error("client auth config is incomplete: {}", validationError);
            authenticated = false;
            return false;
        }

        if (mockMode) {
            log.info("[Mock模式] 跳过与管控平台的真实双向认证，直接认为认证成功");
            authenticated = true;
            return true;
        }

        int handle = - 1;
        try{
            // 1.打开Ukey
            handle = sdkAdapter.openUkey(ukeyPath,password,authId);
            if(handle < 0 ){
                log.error("打开Ukey失败：{}",handle);
                return false;
            }
            log.info("打开Ukey成功：handle-{}",handle);

            // 2. 读取服务器证书
            String serverCert = loadServerCertificate();

            // 3. 设置服务器信息
            boolean setSuccess = sdkAdapter.setAuthServerInfo(handle, serverId, serverCert);
            if (!setSuccess) {
                log.error("设置服务器信息失败");
                return false;
            }

            // 4. 第一步：生成认证请求
            String authReq = sdkAdapter.buildAuthReq(handle);
            if (authReq == null) {
                log.error("生成认证请求失败");
                return false;
            }
            log.info("认证请求: {}", authReq);


            // 5. 发送到管控平台
            Map<String, Object> response1 = sendAuthRequest(authReq);
            if ((Integer) response1.get("code") != 200) {
                log.error("服务器拒绝认证请求: {}", response1.get("msg"));
                return false;
            }
            String serverResponse1 = (String) response1.get("data");

            // 6. 第二步：生成认证信息
            String authInfo = sdkAdapter.buildAuthInfo(handle, serverResponse1);
            if (authInfo == null) {
                log.error("生成认证信息失败");
                return false;
            }
            log.info("认证信息: {}", authInfo);

            // 7. 发送认证信息
            Map<String, Object> response2 = sendAuthVerify(authInfo);
            if ((Integer) response2.get("code") != 200) {
                log.error("服务器拒绝认证: {}", response2.get("msg"));
                return false;
            }
            String serverResponse2 = (String) response2.get("data");

            // 8. 第三步：检查认证结果
            VAuthSDKAdapter.AuthCheckResult result = sdkAdapter.checkAuthResult(handle, serverResponse2);
            if (!result.success) {
                log.error("认证失败: {}", result.error);
                return false;
            }

            log.info("✅ 与管控平台认证成功！");
            authenticated = true;
            return true;

        } catch (Exception e) {
            log.error("认证过程异常", e);
            authenticated = false;
            return false;
        } finally {
            if (handle >= 0) {
                sdkAdapter.closeHandle(handle);
            }
        }
    }

    @Override
    public byte[] signEnvelopeWithControlPlatform(String ukeyPath, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("sign data is empty");
        }

        String validationError = validateRequiredConfig();
        if (validationError != null) {
            throw new IllegalStateException("client auth config is incomplete: " + validationError);
        }

        if (mockMode) {
            int handle = sdkAdapter.openUkey(isBlank(ukeyPath) ? "/mock/ukey/client" : ukeyPath, password, authId);
            if (handle < 0) {
                throw new IllegalStateException("open mock ukey failed: " + handle);
            }
            try {
                return sdkAdapter.encryptData(handle, true, data);
            } finally {
                sdkAdapter.closeHandle(handle);
            }
        }

        if (isBlank(ukeyPath)) {
            throw new IllegalStateException("ukey path is empty");
        }

        int handle = -1;
        try {
            handle = sdkAdapter.openUkey(ukeyPath, password, authId);
            if (handle < 0) {
                throw new IllegalStateException("open ukey failed: " + handle);
            }

            String serverCert = loadServerCertificate();
            boolean setSuccess = sdkAdapter.setAuthServerInfo(handle, serverId, serverCert);
            if (!setSuccess) {
                throw new IllegalStateException("set auth server info failed");
            }

            String authReq = sdkAdapter.buildAuthReq(handle);
            if (authReq == null) {
                throw new IllegalStateException("build auth request failed");
            }

            Map<String, Object> response1 = sendAuthRequest(authReq);
            if (!isSuccess(response1)) {
                throw new IllegalStateException("auth request rejected: " + safeMessage(response1));
            }
            String serverResponse1 = (String) response1.get("data");

            String authInfo = sdkAdapter.buildAuthInfo(handle, serverResponse1);
            if (authInfo == null) {
                throw new IllegalStateException("build auth info failed");
            }

            Map<String, Object> response2 = sendAuthVerify(authInfo);
            if (!isSuccess(response2)) {
                throw new IllegalStateException("auth verify rejected: " + safeMessage(response2));
            }
            String serverResponse2 = (String) response2.get("data");

            VAuthSDKAdapter.AuthCheckResult result = sdkAdapter.checkAuthResult(handle, serverResponse2);
            if (!result.success) {
                throw new IllegalStateException("auth check failed: " + result.error);
            }

            return sdkAdapter.encryptData(handle, true, data);
        } finally {
            if (handle >= 0) {
                sdkAdapter.closeHandle(handle);
            }
        }
    }

    @Override
    public byte[] verifyEnvelopeWithControlPlatform(String ukeyPath, byte[] signedEnvelope) {
        if (signedEnvelope == null || signedEnvelope.length == 0) {
            throw new IllegalArgumentException("signed envelope is empty");
        }

        String validationError = validateRequiredConfig();
        if (validationError != null) {
            throw new IllegalStateException("client auth config is incomplete: " + validationError);
        }

        if (mockMode) {
            int handle = sdkAdapter.openUkey(isBlank(ukeyPath) ? "/mock/ukey/client" : ukeyPath, password, authId);
            if (handle < 0) {
                throw new IllegalStateException("open mock ukey failed: " + handle);
            }
            try {
                return sdkAdapter.decryptData(handle, true, signedEnvelope);
            } finally {
                sdkAdapter.closeHandle(handle);
            }
        }

        if (isBlank(ukeyPath)) {
            throw new IllegalStateException("ukey path is empty");
        }

        int handle = -1;
        try {
            handle = sdkAdapter.openUkey(ukeyPath, password, authId);
            if (handle < 0) {
                throw new IllegalStateException("open ukey failed: " + handle);
            }

            String serverCert = loadServerCertificate();
            boolean setSuccess = sdkAdapter.setAuthServerInfo(handle, serverId, serverCert);
            if (!setSuccess) {
                throw new IllegalStateException("set auth server info failed");
            }

            authenticateOpenedHandle(handle);
            return sdkAdapter.decryptData(handle, true, signedEnvelope);
        } finally {
            if (handle >= 0) {
                sdkAdapter.closeHandle(handle);
            }
        }
    }

    /**
     * VAuth signed envelopes require the same server-side auth context before verify/decrypt.
     */
    private void authenticateOpenedHandle(int handle) {
        String authReq = sdkAdapter.buildAuthReq(handle);
        if (authReq == null) {
            throw new IllegalStateException("build auth request failed");
        }

        Map<String, Object> response1 = sendAuthRequest(authReq);
        if (!isSuccess(response1)) {
            throw new IllegalStateException("auth request rejected: " + safeMessage(response1));
        }
        String serverResponse1 = (String) response1.get("data");

        String authInfo = sdkAdapter.buildAuthInfo(handle, serverResponse1);
        if (authInfo == null) {
            throw new IllegalStateException("build auth info failed");
        }

        Map<String, Object> response2 = sendAuthVerify(authInfo);
        if (!isSuccess(response2)) {
            throw new IllegalStateException("auth verify rejected: " + safeMessage(response2));
        }
        String serverResponse2 = (String) response2.get("data");

        VAuthSDKAdapter.AuthCheckResult result = sdkAdapter.checkAuthResult(handle, serverResponse2);
        if (!result.success) {
            throw new IllegalStateException("auth check failed: " + result.error);
        }
    }

    private boolean isSuccess(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object code = response.get("code");
        if (code instanceof Number) {
            return ((Number) code).intValue() == 200;
        }
        return "200".equals(String.valueOf(code));
    }

    private String safeMessage(Map<String, Object> response) {
        if (response == null) {
            return "response is null";
        }
        Object message = response.get("msg");
        if (message == null) {
            message = response.get("message");
        }
        return message == null ? String.valueOf(response) : String.valueOf(message);
    }

    private String loadServerCertificate() {
        try {
            String certContent = certificateFileService.loadCertificateContent(serverCerPath);
            log.debug("成功加载服务端证书: {}", serverCerPath);
            return certContent;
        } catch (RuntimeException e) {
            log.error("读取服务端证书失败: {}", serverCerPath, e);
            throw new RuntimeException("无法读取服务端证书", e);
        }
    }

    private Map<String, Object> sendAuthRequest(String authReq) {
        Map<String, String> request = new HashMap<>();
        request.put("authId", authId);
        request.put("requestData", authReq);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        return restTemplate.postForObject(
                monitorPlatformUrl + "/auth/server/request",
                entity,
                Map.class
        );
    }

    private Map<String, Object> sendAuthVerify(String authInfo) {
        Map<String, String> request = new HashMap<>();
        request.put("authId", authId);
        request.put("requestData", authInfo);

        // 向服务端提交客户端证书（服务端 VAuth_ParseAuthInfo 必须）
        try {
            String clientCert = loadClientCertificate();
            request.put("clientCert", clientCert);
        } catch (Exception e) {
            log.warn("读取客户端证书失败，服务端 ParseAuthInfo 可能报错: {}", e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        return restTemplate.postForObject(
                monitorPlatformUrl + "/auth/server/verify",
                entity,
                Map.class
        );
    }

    private String loadClientCertificate() {
        try {
            String certContent = certificateFileService.loadCertificateContent(clientCerPath);
            log.debug("成功加载客户端证书: {}", clientCerPath);
            return certContent;
        } catch (RuntimeException e) {
            log.error("读取客户端证书失败: {}", clientCerPath, e);
            throw new RuntimeException("无法读取客户端证书", e);
        }
    }

    /**
     * 检查是否已认证
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean isConfigReady() {
        return validateRequiredConfig() == null;
    }

    /**
     * 清除认证状态
     */
    public void clearAuthentication() {
        this.authenticated = false;
        log.info("认证状态已清除");
    }

    // ========== 动态配置 setter（供 PUT /api/config/vauth 接口调用） ==========

    public void setMonitorPlatformUrl(String url) {
        this.monitorPlatformUrl = url;
        log.info("[动态配置] monitorPlatformUrl 已更新为: {}", url);
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
        log.info("[动态配置] serverId 已更新为: {}", serverId);
    }

    public void setServerCerPath(String path) {
        this.serverCerPath = path;
        log.info("[动态配置] serverCerPath 已更新为: {}", path);
    }

    public void setClientCerPath(String path) {
        this.clientCerPath = path;
        log.info("[动态配置] clientCerPath 已更新为: {}", path);
    }

    public void setAuthId(String authId) {
        this.authId = authId;
        log.info("[动态配置] authId 已更新为: {}", authId);
    }

    public void setPassword(String password) {
        this.password = password;
        log.info("[动态配置] password 已更新");
    }

    public void setMockMode(boolean mockMode) {
        this.mockMode = mockMode;
        log.info("[动态配置] mockMode 已更新为: {}", mockMode);
    }

    // ========== getter（供 GET /api/config/vauth 接口读取当前内存值） ==========

    public String getMonitorPlatformUrl() { return monitorPlatformUrl; }
    public String getServerId()           { return serverId; }
    public String getServerCerPath()      { return serverCerPath; }
    public String getClientCerPath()      { return clientCerPath; }
    public String getAuthId()             { return authId; }
    public String getPassword()           { return password; }
    public boolean isMockMode()           { return mockMode; }

    private String validateRequiredConfig() {
        if (isBlank(monitorPlatformUrl)) {
            return "monitorPlatformUrl is required";
        }
        if (!monitorPlatformUrl.startsWith("http://") && !monitorPlatformUrl.startsWith("https://")) {
            return "monitorPlatformUrl must start with http:// or https://";
        }
        if (mockMode) {
            return null;
        }
        if (isBlank(serverId)) {
            return "serverId is required";
        }
        if (isBlank(authId)) {
            return "authId is required";
        }
        if (isBlank(password)) {
            return "password is required";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
