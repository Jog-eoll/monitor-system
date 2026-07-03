package com.infopublish.client.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.service.MonitorPlatformClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管控平台客户端
 * 用于监管程序与管控平台通信
 */
@Slf4j
@Service
public class MonitorPlatformClientImpl implements MonitorPlatformClient {

    @Value("${monitor-platform.url:http://127.0.0.1:8063}")
    private String platformUrl;

    @Value("${monitor-platform.client-id:info-publish-client-001}")
    private String clientId;

    // ========== 证书校验接口 ==========

    /**
     * 向管控平台请求校验UKey证书合法性
     * @param certSerialNo 证书唯一编号
     * @param certificateContent 证书内容
     * @return 校验结果 {valid, errorCode, message}
     */
    public Map<String, Object> validateCertificate(String certSerialNo, String certificateContent) {
        Map<String, Object> result = new HashMap<>();

        log.info("向管控平台请求证书校验: certSerialNo={}", certSerialNo);

        String url = platformUrl + "/cert/validate";

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("certSerialNo", certSerialNo);
            params.put("certificateContent", certificateContent);
            params.put("clientId", clientId);

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(params))
                    .timeout(10000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());
            log.info("证书校验响应: {}", json);

            Integer code = json.getInteger("code");
            JSONObject data = json.getJSONObject("data");

            if (code != null && code == 200 && data != null) {
                result.put("valid", data.getBooleanValue("valid"));
                result.put("errorCode", data.getIntValue("errorCode"));
                result.put("message", data.getString("message"));
            } else {
                result.put("valid", false);
                result.put("errorCode", -1);
                result.put("message", json.getString("message"));
            }

        } catch (Exception e) {
            log.error("证书校验请求失败", e);
            result.put("valid", false);
            result.put("errorCode", -1);
            result.put("message", "管控平台连接失败: " + e.getMessage());
        }

        return result;
    }

    // ========== 认证通知接口 ==========

    /**
     * 通知管控平台：监管程序认证成功
     * @param certSerialNo 证书编号
     * @param authToken 认证令牌
     * @return 是否通知成功
     */
    public boolean notifyClientAuthenticated(String certSerialNo, String authToken) {
        log.info("通知管控平台：认证成功上线 - ClientId: {}, CertSerialNo: {}", clientId, certSerialNo);

        String url = platformUrl + "/cert/online";
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("certSerialNo", certSerialNo);
            params.put("clientId", clientId);
            params.put("onlineStatus", "ONLINE");

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(params))
                    .timeout(10000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());
            log.info("管控平台响应: {}", json);

            if (json.getInteger("code") == 200) {
                log.info("管控平台已收到认证成功通知");
                return true;
            } else {
                log.warn("管控平台返回非200: {}", json.getString("message"));
                return false;
            }

        } catch (Exception e) {
            log.warn("通知管控平台失败（不影响本地认证状态）: {}", e.getMessage());
            return false;
        }
    }

    // ========== 断开通知接口 ==========

    /**
     * 通知管控平台：客户端断开连接
     * UKey拔出或主动退出时调用
     * @param certSerialNo 证书编号
     * @param reason 断开原因（ukey_removed/logout/shutdown）
     */
    public boolean notifyClientDisconnected(String certSerialNo, String reason) {
        log.info("通知管控平台：客户端离线 - ClientId: {}, CertSerialNo: {}, Reason: {}", clientId, certSerialNo, reason);

        String url = platformUrl + "/cert/online";

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("certSerialNo", certSerialNo);
            params.put("clientId", clientId);
            params.put("onlineStatus", "OFFLINE");
            params.put("reason", reason);

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(params))
                    .timeout(5000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());
            return json.getInteger("code") == 200;

        } catch (Exception e) {
            log.warn("通知管控平台离线失败（不影响本地状态）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 向管控平台注册Ukey
     * @param ukeyId UkeyID
     * @param certificate Ukey证书
     * @return 注册结果
     */
    public Map<String, Object> registerUkey(String ukeyId, String certificate) {
        Map<String, Object> result = new HashMap<>();

        log.info("向管控平台注册Ukey - UkeyId: {}", ukeyId);

        String url = platformUrl + "/api/client/ukey/register";

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("clientId", clientId);
            params.put("ukeyId", ukeyId);
            params.put("certificate", certificate);
            params.put("timestamp", System.currentTimeMillis());

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(params))
                    .timeout(10000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());
            log.info("注册响应: {}", json);

            if (json.getInteger("code") == 200) {
                result.put("success", true);
                result.put("message", "Ukey注册成功");
            } else {
                result.put("success", false);
                result.put("message", json.getString("message"));
            }

        } catch (Exception e) {
            log.error("注册Ukey失败", e);
            result.put("success", false);
            result.put("message", "注册失败: " + e.getMessage());
        }

        return result;
    }

    public String getPlatformUrl() {
        return platformUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setPlatformUrl(String platformUrl) {
        this.platformUrl = platformUrl;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    // ========== 心跳接口 ==========

    /**
     * 向管控平台上报心跳
     * 每 5 秒由 ClientHeartbeatTask 调用一次，使管控平台得知本服务进程存活
     *
     * @param certSerialNo 当前在线的证书编号
     */
    public void sendHeartbeat(String certSerialNo) {
        String url = platformUrl + "/cert/heartbeat";
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("certSerialNo", certSerialNo);
            params.put("clientId", clientId);
            // clientIp 由服务端从 HTTP 请求中自动获取，无需客户端填写

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(params))
                    .timeout(3000)
                    .execute();

            if (response.getStatus() != 200) {
                log.warn("[心跳] 上报失败, status={}", response.getStatus());
            }
        } catch (Exception e) {
            // 心跳失败不影响业务，静默记录
            log.debug("[心跳] 上报异常（管控平台可能暂时不可达）: {}", e.getMessage());
        }
    }
}
