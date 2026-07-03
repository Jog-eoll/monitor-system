package com.publishgateway.udpproxy.mqtt;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 HTTP 转发服务 —— 将 MQTT 下发的命令以 HTTP 请求形式转发到
 * 局域网内目标设备（或本机）的现有 REST 接口。
 * <p>
 * 使用 Hutool HttpRequest 实现，FastJSON2 序列化请求体与解析响应体。
 * </p>
 */
@Slf4j
@Component
public class LocalHttpForwardService {

    /** 默认 HTTP 调用超时时间（毫秒） */
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    /**
     * 允许的 HTTP 转发路径前缀列表。
     * <p>
     * 通过配置项 mqtt-agent.http-forward.allowed-paths 覆盖，
     * 默认允许 /udp-proxy/config 和 /api/ 前缀。
     * </p>
     */
    @Value("${mqtt-agent.http-forward.allowed-paths:/udp-proxy/config,/udp-proxy/chain/,/udp-proxy/stop/,/udp-proxy/status/,/udp-proxy/rules,/api/}")
    private List<String> allowedPaths;

    /**
     * HTTP 转发超时时间（毫秒）。
     * <p>
     * 通过配置项 mqtt-agent.http-forward.timeout-ms 覆盖，默认 5000ms。
     * </p>
     */
    @Value("${mqtt-agent.http-forward.timeout-ms:5000}")
    private int forwardTimeoutMs;

    /**
     * 转发 HTTP 请求到目标地址。
     *
     * @param targetIp   目标 IP（必填）
     * @param targetPort 目标端口
     * @param path       请求路径，如 /udp-proxy/config（为空时默认 /）
     * @param httpMethod HTTP 方法（为空时默认 POST）
     * @param body       请求体（JSON 对象，可为空）
     * @return 响应体解析后的 Map；异常时返回 {success:false, message:...}
     * @throws IllegalArgumentException 当 targetIp 为空时抛出
     */
    public Map<String, Object> forward(String targetIp, int targetPort, String path,
                                       String httpMethod, Map<String, Object> body) {
        if (targetIp == null || targetIp.trim().isEmpty()) {
            throw new IllegalArgumentException("targetIp 不能为空");
        }

        String resolvedPath = (path == null || path.trim().isEmpty()) ? "/" : path.trim();
        String methodStr = (httpMethod == null || httpMethod.trim().isEmpty())
                ? "POST" : httpMethod.trim().toUpperCase();
        validateForwardTarget(resolvedPath, methodStr);
        Object bodyToSerialize = (body == null) ? Collections.emptyMap() : body;
        String jsonBody = JSON.toJSONString(bodyToSerialize);
        String url = "http://" + targetIp.trim() + ":" + targetPort + resolvedPath;

        try {
            Method method = Method.valueOf(methodStr);
            HttpResponse response = HttpRequest.of(url)
                    .method(method)
                    .header(Header.CONTENT_TYPE, "application/json")
                    .body(jsonBody)
                    .timeout(forwardTimeoutMs)
                    .execute();

            String respBody = response.body();
            log.info("[HTTP转发] {} {} -> status={}, respLen={}",
                    methodStr, url, response.getStatus(),
                    respBody == null ? 0 : respBody.length());

            if (respBody == null || respBody.trim().isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("success", true);
                empty.put("httpStatus", response.getStatus());
                empty.put("message", "响应体为空");
                return empty;
            }

            Map<String, Object> result = JSON.parseObject(respBody);
            if (result == null) {
                // 响应非 JSON 对象（如纯文本），包装后返回
                result = new HashMap<>();
                result.put("rawBody", respBody);
            }
            result.putIfAbsent("httpStatus", response.getStatus());
            result.putIfAbsent("success", response.isOk());
            return result;
        } catch (IllegalArgumentException iae) {
            // targetIp 为空，直接抛出，不在此吞掉
            throw iae;
        } catch (Exception e) {
            log.error("[HTTP转发] 调用异常: {} {} {}", methodStr, url, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "HTTP 转发异常: " + e.getMessage());
            error.put("url", url);
            error.put("method", methodStr);
            return error;
        }
    }

    private void validateForwardTarget(String path, String method) {
        boolean allowed = allowedPaths.stream()
                .anyMatch(prefix -> path.startsWith(prefix));
        if (!allowed) {
            throw new IllegalArgumentException("不允许的 MQTT HTTP 转发路径: " + path);
        }
        if (!"POST".equals(method) && !"PUT".equals(method) 
                && !"DELETE".equals(method) && !"GET".equals(method)) {
            throw new IllegalArgumentException("不允许的 MQTT HTTP 转发方法: " + method);
        }
    }
}
