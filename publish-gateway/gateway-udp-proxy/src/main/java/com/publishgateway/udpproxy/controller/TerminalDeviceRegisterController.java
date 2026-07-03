package com.publishgateway.udpproxy.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 终端设备注册代理控制器 —— 接收平台请求并转发到解密网关。
 * <p>
 * POST /api/terminal-devices/register
 * </p>
 */
@Slf4j
@RestController
public class TerminalDeviceRegisterController {

    @Value("${terminal-device-register.default-terminal-url:http://127.0.0.1:8093}")
    private String defaultTerminalUrl;

    @Value("${terminal-device-register.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${terminal-device-register.read-timeout-ms:10000}")
    private int readTimeoutMs;

    /**
     * 代理终端设备注册 —— 接收平台请求，转发到解密网关 POST /api/devices/register。
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "deviceId": "IB-xxx",
     *   "ip": "192.168.1.100",
     *   "port": 8989,
     *   "vendor": "COLORLIGHT",
     *   "terminalGatewayUrl": "http://192.168.1.50:8093",
     *   "sn": "..."
     * }
     * </pre>
     * </p>
     */
    @PostMapping("/api/terminal-devices/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> requestBody) {
        String deviceId = (String) requestBody.getOrDefault("deviceId", "");
        String ip = (String) requestBody.getOrDefault("ip", "");
        Integer port = requestBody.get("port") instanceof Integer
                ? (Integer) requestBody.get("port") : null;
        String vendor = (String) requestBody.getOrDefault("vendor", "COLORLIGHT");
        String sn = (String) requestBody.getOrDefault("sn", "");
        String terminalGatewayUrl = (String) requestBody.getOrDefault("terminalGatewayUrl", defaultTerminalUrl);
        String macAddr = (String) requestBody.getOrDefault("macAddr", null);

        log.info("[终端设备注册代理] 收到注册请求: deviceId={}, ip={}, port={}, vendor={}, terminalUrl={}",
                deviceId, ip, port, vendor, terminalGatewayUrl);

        if (isBlank(ip)) {
            return Result.error("ip 不能为空");
        }
        if (isBlank(deviceId)) {
            return Result.error("deviceId 不能为空");
        }

        // 构建解密网关注册请求体
        Map<String, Object> terminalBody = new LinkedHashMap<>();
        terminalBody.put("deviceId", deviceId);
        terminalBody.put("ip", ip);
        terminalBody.put("port", port != null ? port : 8989);
        terminalBody.put("vendor", vendor);
        terminalBody.put("sn", sn);
        terminalBody.put("productType", "info_board");

        if (macAddr != null && !macAddr.isEmpty()) {
            terminalBody.put("macAddr", macAddr);
        }

        // 转发到解密网关
        String targetUrl = terminalGatewayUrl + "/api/devices/register";

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(terminalBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(targetUrl, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[终端设备注册代理] 解密网关返回非2xx: deviceId={}, httpStatus={}, body={}",
                        deviceId, response.getStatusCodeValue(), response.getBody());
                return Result.error("解密网关返回失败: HTTP " + response.getStatusCodeValue());
            }

            JSONObject respJson = JSON.parseObject(response.getBody());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("terminalResponse", respJson);
            result.put("deviceId", deviceId);
            result.put("targetUrl", targetUrl);

            // 解密网关 Result<T> 即使 HTTP 200 也可能携带业务错误码，必须解析 code 字段
            if (respJson == null || !Integer.valueOf(200).equals(respJson.getInteger("code"))) {
                String terminalMsg = respJson != null ? respJson.getString("msg") : "空响应";
                log.warn("[终端设备注册代理] 解密网关业务失败: deviceId={}, terminalCode={}, terminalMsg={}",
                        deviceId, respJson != null ? respJson.getInteger("code") : null, terminalMsg);
                return Result.error(500, "解密网关注册失败: " + terminalMsg, result);
            }
            log.info("[终端设备注册代理] 注册成功: deviceId={}, terminalUrl={}", deviceId, targetUrl);
            return Result.success("解密网关注册成功", result);
        } catch (Exception e) {
            log.error("[终端设备注册代理] 解密网关调用异常: deviceId={}, targetUrl={}, error={}",
                    deviceId, targetUrl, e.getMessage(), e);
            return Result.error("解密网关不可达: " + e.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
