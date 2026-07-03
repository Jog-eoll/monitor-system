package com.monitorplatform.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.ForwardChannelConfigDTO;
import com.monitorplatform.device.mapper.UnifiedDeviceMapper;
import com.monitorplatform.device.service.GatewayForwardConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 网关转发配置服务实现类
 * 通过HTTP请求向加密网关下发配置
 */
@Slf4j
@Service
public class GatewayForwardConfigServiceImpl implements GatewayForwardConfigService {

    @Resource
    private UnifiedDeviceMapper unifiedDeviceMapper;

    @Resource
    private RestTemplate restTemplate;

    @Value("${monitor.gateway.url:http://127.0.0.1:8081}")
    private String defaultGatewayUrl;

    /**
     * 下发转发配置到指定网关
     */
    @Override
    public boolean deployConfigToGateway(ForwardChannelConfigDTO dto) {
        // 1. 验证参数
        if (dto == null || dto.getGatewaySn() == null) {
            throw new IllegalArgumentException("配置信息或网关序列号不能为空");
        }

        // 2. 查询网关信息
        UnifiedDevice gateway = getGatewayBySn(dto.getGatewaySn());
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + dto.getGatewaySn());
        }

        // 3. 设置配置来源为管控平台
        dto.setConfigSource("platform");

        // 4. 构建网关接收配置的URL
        String gatewayUrl = buildGatewayUrl(gateway);
        String apiUrl = gatewayUrl + "/forward/channel/receive";

        // 5. 发送HTTP请求到网关
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ForwardChannelConfigDTO> request = new HttpEntity<>(dto, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                if (code != null && code == 200) {
                    log.info("配置下发成功: 网关={}, 通道={}", dto.getGatewaySn(), dto.getChannelName());
                    return true;
                } else {
                    String message = (String) response.getBody().get("message");
                    log.error("配置下发失败: {}", message);
                    return false;
                }
            }
            
            log.error("配置下发失败: HTTP状态码={}", response.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("下发配置到网关失败: 网关={}, 错误={}", dto.getGatewaySn(), e.getMessage(), e);
            throw new RuntimeException("网关通信失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量下发配置到指定网关
     */
    @Override
    public int batchDeployConfigs(String gatewaySn, ForwardChannelConfigDTO[] configs) {
        if (configs == null || configs.length == 0) {
            return 0;
        }

        // 验证网关
        UnifiedDevice gateway = getGatewayBySn(gatewaySn);
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + gatewaySn);
        }

        int successCount = 0;
        for (ForwardChannelConfigDTO config : configs) {
            config.setGatewaySn(gatewaySn);
            try {
                if (deployConfigToGateway(config)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量下发配置失败: 通道={}, 错误={}", config.getChannelName(), e.getMessage());
            }
        }

        log.info("批量下发配置完成: 网关={}, 总数={}, 成功={}, 失败={}", 
                gatewaySn, configs.length, successCount, configs.length - successCount);
        return successCount;
    }

    /**
     * 更新网关转发配置
     */
    @Override
    public boolean updateConfigToGateway(ForwardChannelConfigDTO dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("配置ID不能为空");
        }

        UnifiedDevice gateway = getGatewayBySn(dto.getGatewaySn());
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + dto.getGatewaySn());
        }

        String gatewayUrl = buildGatewayUrl(gateway);
        String apiUrl = gatewayUrl + "/forward/channel/update";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ForwardChannelConfigDTO> request = new HttpEntity<>(dto, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                return code != null && code == 200;
            }
            return false;
        } catch (Exception e) {
            log.error("更新网关配置失败: 网关={}, 配置ID={}, 错误={}", 
                    dto.getGatewaySn(), dto.getId(), e.getMessage(), e);
            throw new RuntimeException("网关通信失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除网关转发配置
     */
    @Override
    public boolean deleteConfigFromGateway(String gatewaySn, Long configId) {
        UnifiedDevice gateway = getGatewayBySn(gatewaySn);
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + gatewaySn);
        }

        String gatewayUrl = buildGatewayUrl(gateway);
        String apiUrl = gatewayUrl + "/forward/channel/" + configId;

        try {
            restTemplate.delete(apiUrl);
            log.info("删除网关配置成功: 网关={}, 配置ID={}", gatewaySn, configId);
            return true;
        } catch (Exception e) {
            log.error("删除网关配置失败: 网关={}, 配置ID={}, 错误={}", 
                    gatewaySn, configId, e.getMessage(), e);
            throw new RuntimeException("网关通信失败: " + e.getMessage(), e);
        }
    }

    /**
     * 控制网关转发通道
     */
    @Override
    public boolean controlChannel(String gatewaySn, Long configId, String action) {
        if (!action.matches("start|stop|restart")) {
            throw new IllegalArgumentException("不支持的操作类型: " + action);
        }

        UnifiedDevice gateway = getGatewayBySn(gatewaySn);
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + gatewaySn);
        }

        String gatewayUrl = buildGatewayUrl(gateway);
        String apiUrl = gatewayUrl + "/forward/channel/" + action + "/" + configId;

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, null, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                return code != null && code == 200;
            }
            return false;
        } catch (Exception e) {
            log.error("控制网关通道失败: 网关={}, 配置ID={}, 操作={}, 错误={}", 
                    gatewaySn, configId, action, e.getMessage(), e);
            throw new RuntimeException("网关通信失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询网关转发配置列表
     */
    @Override
    public Object getConfigsFromGateway(String gatewaySn) {
        UnifiedDevice gateway = getGatewayBySn(gatewaySn);
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + gatewaySn);
        }

        String gatewayUrl = buildGatewayUrl(gateway);
        String apiUrl = gatewayUrl + "/forward/channel/by-sn/" + gatewaySn;

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().get("data");
            }
            return null;
        } catch (Exception e) {
            log.error("查询网关配置列表失败: 网关={}, 错误={}", gatewaySn, e.getMessage(), e);
            throw new RuntimeException("网关通信失败: " + e.getMessage(), e);
        }
    }

    /**
     * 测试网关转发通道连通性
     */
    @Override
    public boolean testConnectionFromGateway(String gatewaySn, Long configId) {
        UnifiedDevice gateway = getGatewayBySn(gatewaySn);
        if (gateway == null) {
            throw new IllegalArgumentException("网关不存在: " + gatewaySn);
        }

        String gatewayUrl = buildGatewayUrl(gateway);
        String apiUrl = gatewayUrl + "/forward/channel/test/" + configId;

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, null, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                if (code != null && code == 200) {
                    Object data = response.getBody().get("data");
                    return data != null && (Boolean) data;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("测试网关连通性失败: 网关={}, 配置ID={}, 错误={}", 
                    gatewaySn, configId, e.getMessage(), e);
            throw new RuntimeException("网关通信失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据序列号查询网关
     */
    private UnifiedDevice getGatewayBySn(String gatewaySn) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, gatewaySn);
        return unifiedDeviceMapper.selectOne(wrapper);
    }

    /**
     * 构建网关URL
     */
    private String buildGatewayUrl(UnifiedDevice gateway) {
        if (gateway.getIpAddress() != null && gateway.getPort() != null) {
            return "http://" + gateway.getIpAddress() + ":" + gateway.getPort();
        }
        // 使用配置的默认网关地址
        return defaultGatewayUrl;
    }
}
