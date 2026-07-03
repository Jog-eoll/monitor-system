package com.infopublish.client.service.impl;


import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.entity.GatewayConfig;
import com.infopublish.client.service.GatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GatewayServiceImpl implements GatewayService {

    @Value("${control-command.gateway-url:http://127.0.0.1:8092}")
    private String secureDeliveryGatewayUrl;
    
    /** 当前配置（仅手动调试模式使用） */
    private GatewayConfig currentConfig;

    /** 当前通道ID（仅手动调试模式使用） */
    private Long currentChannelId;

    /**
     * 创建转发通道
     */
    public Map<String, Object> createForwardChannel(GatewayConfig config) {
        Map<String, Object> result = new HashMap<>();

        String url = buildUrl(config, "/forward/channel/save");
        log.info("请求创建转发通道: {}", url);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("channelName", config.getChannelName());
            params.put("gatewaySn", config.getGatewaySn());
            params.put("listenPort", config.getForwardListenPort());
            params.put("forwardIp", config.getDisplayGatewayIp());
            params.put("forwardPort", config.getDisplayGatewayPort());
            params.put("enabled", true);
            params.put("effectTime", "immediate");
            params.put("configSource", "client");

            // 添加白名单
            if (config.getLocalIp() != null && !config.getLocalIp().isEmpty()) {
                params.put("sourceWhitelist", new String[]{config.getLocalIp()});
            }

            HttpResponse response = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(params))
                    .timeout(10000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());
            log.info("创建转发通道响应: {}", json);

            if (json.getInteger("code") == 200) {
                this.currentConfig = config;
                this.currentChannelId = json.getLong("data");
                            
                result.put("success", true);
                result.put("channelId", currentChannelId);
                result.put("message", "转发通道创建成功");
            } else {
                result.put("success", false);
                result.put("message", json.getString("message"));
            }
        } catch (Exception e) {
            log.error("创建转发通道失败", e);
            result.put("success", false);
            result.put("message", "连接加密网关失败: " + e.getMessage());
        }

        return result;
    }


    /**
     * 停止转发通道
     */
    public Map<String, Object> stopForwardChannel() {
        Map<String, Object> result = new HashMap<>();

        if (currentChannelId == null || currentConfig == null) {
            result.put("success", false);
            result.put("message", "没有运行中的通道");
            return result;
        }

        String url = buildUrl(currentConfig, "/forward/channel/stop/" + currentChannelId);
        log.info("请求停止转发通道: {}", url);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .timeout(10000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());

            if (json.getInteger("code") == 200) {
                // 清除配置
                this.currentConfig = null;
                this.currentChannelId = null;
                
                result.put("success", true);
                result.put("message", "转发通道已停止");
            } else {
                result.put("success", false);
                result.put("message", json.getString("message"));
            }
        } catch (Exception e) {
            log.error("停止转发通道失败", e);
            result.put("success", false);
            result.put("message", "操作失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 查询通道状态
     */
    public Map<String, Object> getChannelStatus() {
        Map<String, Object> result = new HashMap<>();

        if (currentChannelId == null || currentConfig == null) {
            if (isSecureDeliveryReady()) {
                result.put("status", 1);
                result.put("statusText", "secure delivery ready");
                result.put("mode", "secure-delivery");
                return result;
            }
            result.put("status", -1);
            result.put("statusText", "未配置");
            return result;
        }

        String url = buildUrl(currentConfig, "/forward/channel/status/" + currentChannelId);

        try {
            HttpResponse response = HttpRequest.get(url)
                    .timeout(5000)
                    .execute();

            JSONObject json = JSON.parseObject(response.body());

            if (json.getInteger("code") == 200) {
                Integer status = json.getInteger("data");
                result.put("status", status);
                result.put("statusText", getStatusText(status));
                result.put("config", currentConfig);
                result.put("channelId", currentChannelId);
            } else {
                result.put("status", -1);
                result.put("statusText", "查询失败");
            }
        } catch (Exception e) {
            log.warn("查询通道状态失败: {}", e.getMessage());
            result.put("status", -1);
            result.put("statusText", "网关连接失败");
        }

        return result;
    }
    /**
     * 测试加密网关连通性
     */
    @Override
    public boolean isSecureDeliveryReady() {
        if (secureDeliveryGatewayUrl == null || secureDeliveryGatewayUrl.trim().isEmpty()) {
            return false;
        }
        String url = secureDeliveryGatewayUrl.replaceAll("/+$", "") + "/api/secure-delivery/health";
        try {
            HttpResponse response = HttpRequest.get(url)
                    .timeout(5000)
                    .execute();
            boolean ready = response.getStatus() >= 200 && response.getStatus() < 300;
            if (!ready) {
                log.warn("secure delivery gateway health check failed: url={}, status={}",
                        url, response.getStatus());
            }
            return ready;
        } catch (Exception e) {
            log.warn("secure delivery gateway health check failed: url={}, error={}", url, e.getMessage());
            return false;
        }
    }

    public Map<String, Object> testGatewayConnection(GatewayConfig config) {
        Map<String, Object> result = new HashMap<>();
        
        // 参数验证
        if (config.getGatewayIp() == null || config.getGatewayIp().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "加密网关IP不能为空");
            return result;
        }
        
        if (config.getGatewayApiPort() == null) {
            result.put("success", false);
            result.put("message", "加密网关端口不能为空");
            return result;
        }
        
        if (config.getGatewayApiPort() < 1 || config.getGatewayApiPort() > 65535) {
            result.put("success", false);
            result.put("message", "端口范围必须在 1-65535 之间，当前值: " + config.getGatewayApiPort());
            return result;
        }
        
        if (config.getGatewaySn() == null || config.getGatewaySn().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "网关序列号不能为空");
            return result;
        }
        
        String url = buildUrl(config, "/forward/channel/by-sn/" + config.getGatewaySn());
        log.info("测试网关连通性: url={}, ip={}, port={}", url, config.getGatewayIp(), config.getGatewayApiPort());

        try {
            long startTime = System.currentTimeMillis();
            HttpResponse response = HttpRequest.get(url)
                    .timeout(5000)
                    .execute();
            long endTime = System.currentTimeMillis();
            
            int statusCode = response.getStatus();
            log.info("网关响应: statusCode={}, body={}", statusCode, response.body());

            if (statusCode == 200) {
                // 额外验证响应体是否合法
                try {
                    JSONObject json = JSON.parseObject(response.body());
                    Integer code = json.getInteger("code");
                    if (code != null && code == 200) {
                        result.put("success", true);
                        result.put("message", "连接成功");
                        result.put("latency", (endTime - startTime) + "ms");
                    } else {
                        result.put("success", false);
                        result.put("message", "网关返回错误: " + json.getString("message"));
                    }
                } catch (Exception e) {
                    result.put("success", false);
                    result.put("message", "响应格式错误: " + e.getMessage());
                }
            } else if (statusCode == 404) {
                result.put("success", false);
                result.put("message", "网关接口不存在 (404)，请检查网关地址和端口");
            } else {
                result.put("success", false);
                result.put("message", "HTTP状态异常: " + statusCode);
            }
        } catch (cn.hutool.http.HttpException e) {
            // HTTP连接异常（端口不通、超时等）
            log.warn("网关连接失败: {}", e.getMessage());
            result.put("success", false);
            if (e.getMessage().contains("Connection refused")) {
                result.put("message", "连接被拒绝，请检查:");
                result.put("details", "1. 网关服务是否启动\n2. IP地址是否正确: " + config.getGatewayIp() + "\n3. 端口是否正确: " + config.getGatewayApiPort());
            } else if (e.getMessage().contains("timeout")) {
                result.put("message", "连接超时，网关可能未响应");
            } else {
                result.put("message", "连接失败: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("测试连通性失败", e);
            result.put("success", false);
            result.put("message", "连接异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取当前配置
     */
    public GatewayConfig getCurrentConfig() {
        return currentConfig;
    }

    /**
     * 获取当前通道ID
     */
    public Long getCurrentChannelId() {
        return currentChannelId;
    }

    private String buildUrl(GatewayConfig config, String path) {
        return "http://" + config.getGatewayIp() + ":" + config.getGatewayApiPort() + path;
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "已停止";
            case 1: return "运行中";
            case 2: return "异常";
            default: return "未知";
        }
    }
    
}
