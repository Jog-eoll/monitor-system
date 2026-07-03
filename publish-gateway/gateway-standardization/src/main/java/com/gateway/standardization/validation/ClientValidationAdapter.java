package com.gateway.standardization.validation;

import com.gateway.standardization.config.StandardizationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 客户端来源校验适配器
 * <p>
 * 封装对 info-publish-client POST /security/validate-source 的 HTTP 调用。
 * 替代原有 ClientValidationService 中的桩方法，实现真实的来源合法性校验。
 * </p>
 * <p>
 * 本适配器独立于原有 ClientValidationService，不修改其代码。
 * 由调用方按需选择使用本适配器或原有的桩方法。
 * </p>
 */
@Slf4j
@Component
public class  ClientValidationAdapter {

    @Resource
    private StandardizationProperties properties;

    /**
     * 调用 info-publish-client 校验来源合法性
     *
     * @param sourceIp   来源 IP
     * @param sourcePort 来源端口
     * @return true=授权通过，false=授权拒绝
     */
    public boolean validateSource(String sourceIp, int sourcePort) {
        // 配置关闭时直接放行
        if (!properties.isPerPacketClientValidationEnabled()) {
            return true;
        }

        String url = "http://127.0.0.1:" + properties.getClientPort() + "/security/validate-source";
        int timeout = properties.getClientValidateTimeoutMs();

        try {
            String body = JSON.toJSONString(new java.util.HashMap<String, Object>() {{
                put("ip", sourceIp);
                put("port", sourcePort);
            }});

            HttpResponse response = HttpRequest.post(url)
                    .body(body)
                    .contentType("application/json")
                    .timeout(timeout)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());
            if (json == null) {
                log.warn("[来源校验适配器] 客户端响应为空: source={}:{}", sourceIp, sourcePort);
                return false;
            }

            // 解析 Result<Map> 格式: { code: 200, data: { authorized: true, ... } }
            Integer code = json.getInteger("code");
            if (code != null && code == 200) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    Boolean authorized = data.getBoolean("authorized");
                    boolean result = authorized != null && authorized;
                    if (!result) {
                        String reason = data.getString("reason");
                        log.warn("[来源校验适配器] 来源校验拒绝: source={}:{}, reason={}",
                                sourceIp, sourcePort, reason);
                    }
                    return result;
                }
            }

            log.warn("[来源校验适配器] 客户端返回非成功码: source={}:{}, code={}", sourceIp, sourcePort, code);
            return false;

        } catch (Exception e) {
            log.error("[来源校验适配器] 调用客户端异常: source={}:{}, error={}",
                    sourceIp, sourcePort, e.getMessage());
            // 客户端不可达时降级放行（避免阻断正常业务）
            return true;
        }
    }
}
