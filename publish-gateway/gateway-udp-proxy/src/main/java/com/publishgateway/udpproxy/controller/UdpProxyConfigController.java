package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.common.Result;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.entity.dto.ConfigDTO;
import com.publishgateway.udpproxy.manager.UdpProxyRuleManager;
import com.publishgateway.udpproxy.relay.ClientRelayReceiver;
import com.publishgateway.udpproxy.service.CryptoPacketStore;
import com.publishgateway.udpproxy.service.ImagePayloadStore;
import com.publishgateway.udpproxy.service.RawPacketStore;
import com.publishgateway.udpproxy.service.TrafficReconciliationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;

/**
 * UDP代理配置控制器
 * 接收管控平台下发的UDP代理配置
 */
@Slf4j
@RestController
@RequestMapping("/udp-proxy")
@RequiredArgsConstructor
public class UdpProxyConfigController {

    @Resource
    private UdpProxyRuleManager proxyRuleManager;

    @Resource
    private RawPacketStore rawPacketStore;

    @Resource
    private CryptoPacketStore cryptoPacketStore;

    @Resource
    private ImagePayloadStore imagePayloadStore;

    @Resource
    private TrafficReconciliationService trafficReconciliationService;

    @Resource
    private ClientRelayReceiver clientRelayReceiver;


    /**
     * 查询最近收到的原始UDP包
     *
     */
    @GetMapping("/raw-packets/latest")
    public Result<?> getLatestRawPackets(
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        java.util.List<RawPacketStore.RawPacketRecord> records = rawPacketStore.getLatest(safeLimit);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("total", rawPacketStore.size());
        data.put("returned", records.size());
        data.put("records", records);
        log.info("【原始包查询】缓存总量={}, 返回={}", rawPacketStore.size(), records.size());
        return Result.success("查询成功", data);
    }

    /**
     * 清空原始包缓存
     */
    @PostMapping("/raw-packets/clear")
    public Result<String> clearRawPackets() {
        rawPacketStore.clear();
        log.info("【原始包缓存】已手动清空");
        return Result.success("缓存已清空", null);
    }
    @GetMapping("/crypto-packets/latest")
    public Result<?> getLatestCryptoPackets(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 3000);
        java.util.List<CryptoPacketStore.CryptoPacketRecord> records = cryptoPacketStore.getLatest(safeLimit);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("total", cryptoPacketStore.size());
        data.put("returned", records.size());
        data.put("records", records);
        log.info("[crypto-packet-query] total={}, returned={}", cryptoPacketStore.size(), records.size());
        return Result.success("query success", data);
    }

    @PostMapping("/crypto-packets/clear")
    public Result<String> clearCryptoPackets() {
        cryptoPacketStore.clear();
        log.info("[crypto-packet-cache] cleared");
        return Result.success("cache cleared", null);
    }

    @GetMapping("/image-payloads/latest")
    public Result<?> getLatestImagePayloads(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        java.util.List<ImagePayloadStore.ImagePayloadRecord> records = imagePayloadStore.getLatest(safeLimit);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("total", imagePayloadStore.size());
        data.put("returned", records.size());
        data.put("records", records);
        log.info("[image-payload-query] total={}, returned={}", imagePayloadStore.size(), records.size());
        return Result.success("query success", data);
    }

    @PostMapping("/image-payloads/clear")
    public Result<String> clearImagePayloads() {
        imagePayloadStore.clear();
        log.info("[image-payload-cache] cleared");
        return Result.success("cache cleared", null);
    }

    @GetMapping("/security/traffic-reconcile")
    public Result<?> getTrafficReconcileStats(@RequestParam(defaultValue = "100") int limit) {
        return Result.success("查询成功", trafficReconciliationService.querySnapshots(limit));
    }

    @PostMapping("/security/traffic-reconcile/clear")
    public Result<String> clearTrafficReconcileStats() {
        trafficReconciliationService.clear();
        return Result.success("流量对账统计已清空", null);
    }

    @GetMapping("/client-relay/status")
    public Result<?> getClientRelayStatus() {
        return Result.success("查询成功", clientRelayReceiver.getStatus());
    }

    @PostMapping("/config")
    public Result<String> receiveConfig(@RequestBody @Valid ConfigDTO configDTO) {
        log.info("==========================================");
        log.info("【发布网关】收到管控平台配置下发");
        log.info("==========================================");
        log.info("  链路ID: {}", configDTO.getChainId());
        log.info("  分支代码: {}", configDTO.getBranchCode() != null ? configDTO.getBranchCode() : "无");
        log.info("  监听地址（虚拟IP）: {}:{}", configDTO.getInfoBoardIp(), configDTO.getInfoBoardPort());
        log.info("  发布网关本机: {}:{}", configDTO.getPublishGatewayIp(), configDTO.getPublishGatewayPort());
        log.info("  加密开关: {}", configDTO.getEncryptEnabled() ? "启用" : "禁用");
        log.info("  终端网关: {}:{}", configDTO.getTerminalGatewayIp(), configDTO.getTerminalGatewayPort());
        log.info("  备注: {}", configDTO.getRemark() != null ? configDTO.getRemark() : "无");
        log.info("==========================================");

        // 转换为代理规则
        UdpProxyRule rule = convertToRule(configDTO);

        // 添加或更新规则
        boolean success = proxyRuleManager.addOrUpdateRule(rule);

        if (success) {
            log.info("✅ 配置应用成功，规则ID: {}", rule.getRuleId());
            log.info("==========================================");

            // 中转：如果平台下发了 clientIp，说明需要转发配置给 PC 客户端
            relayToClient(configDTO);

            return Result.success("配置应用成功", rule.getRuleId());
        } else {
            log.error("❌ 配置应用失败");
            log.info("==========================================");
            return Result.error("配置应用失败");
        }
    }


    /**
     * 启用/停用链路规则（管控平台启停用链路时调用）
     * 不逻辑删除，仅切换 ENABLED / DISABLED 状态
     *
     * PUT /udp-proxy/chain/{chainId}/status?status=ENABLE或DISABLE
     */
    @PutMapping("/chain/{chainId}/status")
    public Result<String> setChainStatus(@PathVariable Long chainId,
                                         @RequestParam String status) {
        log.info("==========================================");
        log.info("【发布网关】收到链路状态切换请求");
        log.info("  链路ID: {}, 目标状态: {}", chainId, status);
        log.info("==========================================");
        try {
            // 将 ENABLE/DISABLE 转换为 DB 内用的 ENABLED/DISABLED
            String dbStatus = "DISABLE".equalsIgnoreCase(status) ? "DISABLED" : "ENABLED";
            int count = proxyRuleManager.setChainRulesStatus(chainId, dbStatus);
            log.info("\u2705 链路 {} 规则已切换为 {}\uff0c共 {} 条", chainId, dbStatus, count);
            return Result.success("规则状态已更新", String.valueOf(count));
        } catch (Exception e) {
            log.error("链路状态切换异常: chainId={}", chainId, e);
            return Result.error("切换失败: " + e.getMessage());
        }
    }

    /**
     * 删除链路规则（管控平台删除链路时调用）
     * 将该链路关联的所有规则逻辑删除，并停止对应代理服务
     *
     * DELETE /udp-proxy/chain/{chainId}
     */
    @DeleteMapping("/chain/{chainId}")
    public Result<String> deleteChainRules(@PathVariable Long chainId) {
        log.info("==========================================");
        log.info("【发布网关】收到删除链路规则请求");
        log.info("  链路ID: {}", chainId);
        log.info("==========================================");
        try {
            int count = proxyRuleManager.disableRuleByChainId(chainId);
            log.info("✅ 链路 {} 规则已处理，共 {} 条", chainId, count);
            return Result.success("规则已停止并标记删除", String.valueOf(count));
        } catch (Exception e) {
            log.error("删除链路规则异常: chainId={}", chainId, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 转换配置DTO为规则实体
     */
    private UdpProxyRule convertToRule(ConfigDTO dto) {
        UdpProxyRule rule = new UdpProxyRule();

        // 生成规则ID（branchCode可选）
        String ruleId;
        String ruleName;
        if (dto.getBranchCode() != null && !dto.getBranchCode().trim().isEmpty()) {
            ruleId = dto.getChainId() + "_" + dto.getBranchCode();
            ruleName = "链路" + dto.getChainId() + "分支" + dto.getBranchCode();
        } else {
            ruleId = String.valueOf(dto.getChainId());
            ruleName = "链路" + dto.getChainId();
        }
        
        rule.setRuleId(ruleId);
        rule.setRuleName(ruleName);

        // 监听配置（虚拟IP策略：监听情报板地址，通过ARP欺骗截获Sigma发往情报板的数据）
        rule.setListenIp(dto.getInfoBoardIp());
        rule.setListenPort(dto.getInfoBoardPort());

        // 设置源IP白名单：仅允许授权的Sigma发布服务器IP通过
        // 若平台未下发 sourceIp，则为空，表示不限制来源（向后兼容）
        rule.setSourceIp(dto.getSourceIp());

        // 判断是否启用加密转发
        if (Boolean.TRUE.equals(dto.getEncryptEnabled()) &&
                dto.getTerminalGatewayIp() != null &&
                dto.getTerminalGatewayPort() != null) {
            // 加密转发模式
            rule.setEncryptEnabled(true);
            rule.setTerminalGatewayIp(dto.getTerminalGatewayIp());
            rule.setTerminalGatewayPort(dto.getTerminalGatewayPort());
            // 最终目标是情报板
            rule.setTargetIp(dto.getInfoBoardIp());
            rule.setTargetPort(dto.getInfoBoardPort());
        } else {
            // 直接转发模式
            rule.setEncryptEnabled(false);
            rule.setTargetIp(dto.getInfoBoardIp());
            rule.setTargetPort(dto.getInfoBoardPort());
        }

        // 其他配置
        rule.setStatus("ENABLED");
        rule.setConfigSource("platform");
        rule.setChainId(dto.getChainId());
        rule.setBranchCode(dto.getBranchCode());
        // 传输协议（默认 UDP，向后兼容）
        rule.setProtocol(dto.getProtocol() != null ? dto.getProtocol().toUpperCase() : "UDP");

        // 情报板厂家标识（影响协议解析策略和附加端口克隆）
        rule.setManufacturer(dto.getManufacturer());

        // 附加TCP端口（诺瓦多端口通信场景）
        rule.setAdditionalTcpPorts(dto.getAdditionalTcpPorts());
        rule.setAdditionalUdpPorts(dto.getAdditionalUdpPorts());

        // 动态端口透明代理（仅 Nova 大屏启用，Sigma 等厂商此字段为 null 不受影响）
        rule.setDynamicPortProxyEnabled(dto.getDynamicPortProxyEnabled());
        rule.setCatchAllProxyPort(dto.getCatchAllProxyPort());
            
        return rule;
    }

    /**
     * 中转配置给 PC 客户端
     *
     * 发布网关与 PC 机在同一局域网（如 192.168.113.x），而管控平台无法直接到达 PC 机。
     * 发布网关作为中转站，将配置原样转发给 PC 客户端的 /udp-proxy/config 接口。
     */
    private void relayToClient(ConfigDTO configDTO) {
        String clientIp = configDTO.getClientIp();
        if (clientIp == null || clientIp.trim().isEmpty()) {
            return; // 未配置 clientIp，无需中转
        }

        int clientPort = configDTO.getClientPort() != null ? configDTO.getClientPort() : 7080;
        String clientUrl = "http://" + clientIp.trim() + ":" + clientPort + "/udp-proxy/config";

        log.info("==========================================" );
        log.info("【发布网关→客户端中转】转发配置到 PC 客户端: {}", clientUrl);

        try {
            // 构建转发请求体（与 PC 客户端的 ConfigDTO 格式一致）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chainId", configDTO.getChainId());
            if (configDTO.getBranchCode() != null) {
                requestBody.put("branchCode", configDTO.getBranchCode());
            }
            requestBody.put("publishGatewayIp", configDTO.getPublishGatewayIp());
            requestBody.put("publishGatewayPort", configDTO.getPublishGatewayPort());
            requestBody.put("infoBoardIp", configDTO.getInfoBoardIp());
            requestBody.put("infoBoardPort", configDTO.getInfoBoardPort());
            requestBody.put("encryptEnabled", configDTO.getEncryptEnabled());
            requestBody.put("terminalGatewayIp", configDTO.getTerminalGatewayIp());
            requestBody.put("terminalGatewayPort", configDTO.getTerminalGatewayPort());
            if (configDTO.getSourceIp() != null) {
                requestBody.put("sourceIp", configDTO.getSourceIp());
            }
            if (configDTO.getProtocol() != null) {
                requestBody.put("protocol", configDTO.getProtocol());
            }
            if (configDTO.getManufacturer() != null) {
                requestBody.put("manufacturer", configDTO.getManufacturer());
            }
            if (configDTO.getAdditionalTcpPorts() != null) {
                requestBody.put("additionalTcpPorts", configDTO.getAdditionalTcpPorts());
            }
            if (configDTO.getAdditionalUdpPorts() != null) {
                requestBody.put("additionalUdpPorts", configDTO.getAdditionalUdpPorts());
            }
            if (configDTO.getDynamicPortProxyEnabled() != null) {
                requestBody.put("dynamicPortProxyEnabled", configDTO.getDynamicPortProxyEnabled());
            }
            if (configDTO.getCatchAllProxyPort() != null) {
                requestBody.put("catchAllProxyPort", configDTO.getCatchAllProxyPort());
            }
            // 客户端特有字段：发布网关MAC地址（用于 ARP 绑定）
            if (configDTO.getGatewayMac() != null) {
                requestBody.put("gatewayMac", configDTO.getGatewayMac());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(clientUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                if (code != null && code == 200) {
                    log.info("【发布网关→客户端中转】✅ 转发成功");
                } else {
                    log.error("【发布网关→客户端中转】❌ 转发失败: {}", response.getBody());
                }
            } else {
                log.error("【发布网关→客户端中转】❌ HTTP状态码: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("【发布网关→客户端中转】❌ 转发异常: clientIp={}", clientIp, e);
        }
        log.info("==========================================");
    }

}
