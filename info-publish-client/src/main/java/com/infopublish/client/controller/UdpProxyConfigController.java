package com.infopublish.client.controller;

import com.infopublish.client.common.Result;
import com.infopublish.client.entity.UdpProxyRule;
import com.infopublish.client.entity.dto.ConfigDTO;
import com.infopublish.client.manager.UdpProxyRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * UDP代理配置控制器（照搬 publish-gateway 接口结构）
 * 接收管控平台下发的链路配置
 *
 * 日志前缀：【信息发布客户端】（网关为【发布网关】）
 */
@Slf4j
@RestController
@RequestMapping("/udp-proxy")
public class UdpProxyConfigController {

    @Resource
    private UdpProxyRuleManager proxyRuleManager;

    /**
     * 接收管控平台配置下发（照搬网关 POST /udp-proxy/config）
     */
    @PostMapping("/config")
    public Result<String> receiveConfig(@RequestBody @Valid ConfigDTO configDTO) {
        log.info("==========================================");
        log.info("【信息发布客户端】收到管控平台配置下发");
        log.info("==========================================");
        log.info("  链路ID: {}", configDTO.getChainId());
        log.info("  分支代码: {}", configDTO.getBranchCode() != null ? configDTO.getBranchCode() : "无");
        log.info("  监听地址（虚拟IP）: {}:{}", configDTO.getInfoBoardIp(), configDTO.getInfoBoardPort());
        log.info("  发布网关本机: {}:{}", configDTO.getPublishGatewayIp(), configDTO.getPublishGatewayPort());
        log.info("  加密开关: {}", configDTO.getEncryptEnabled() ? "启用" : "禁用");
        log.info("  终端网关: {}:{}", configDTO.getTerminalGatewayIp(), configDTO.getTerminalGatewayPort());
        log.info("  发布网关MAC: {}", configDTO.getGatewayMac() != null ? configDTO.getGatewayMac() : "未配置");
        log.info("  备注: {}", configDTO.getRemark() != null ? configDTO.getRemark() : "无");
        log.info("==========================================");

        // 转换为代理规则
        UdpProxyRule rule = convertToRule(configDTO);

        // 添加或更新规则
        boolean success = proxyRuleManager.addOrUpdateRule(rule);

        if (success) {
            log.info("✅ 配置应用成功，规则ID: {}", rule.getRuleId());
            log.info("==========================================");
            return Result.ok("配置应用成功", rule.getRuleId());
        } else {
            log.error("❌ 配置应用失败");
            log.info("==========================================");
            return Result.error("配置应用失败");
        }
    }

    /**
     * 启用/停用链路规则（照搬网关 PUT /udp-proxy/chain/{chainId}/status）
     * 不逻辑删除，仅切换 ENABLED / DISABLED 状态
     */
    @PutMapping("/chain/{chainId}/status")
    public Result<String> setChainStatus(@PathVariable Long chainId,
                                         @RequestParam String status) {
        log.info("==========================================");
        log.info("【信息发布客户端】收到链路状态切换请求");
        log.info("  链路ID: {}, 目标状态: {}", chainId, status);
        log.info("==========================================");
        try {
            // 将 ENABLE/DISABLE 转换为 DB 内用的 ENABLED/DISABLED
            String dbStatus = "DISABLE".equalsIgnoreCase(status) ? "DISABLED" : "ENABLED";
            int count = proxyRuleManager.setChainRulesStatus(chainId, dbStatus);
            log.info("✅ 链路 {} 规则已切换为 {}，共 {} 条", chainId, dbStatus, count);
            return Result.ok("规则状态已更新", String.valueOf(count));
        } catch (Exception e) {
            log.error("链路状态切换异常: chainId={}", chainId, e);
            return Result.error("切换失败: " + e.getMessage());
        }
    }

    /**
     * 删除链路规则（照搬网关 DELETE /udp-proxy/chain/{chainId}）
     * 将该链路关联的所有规则逻辑删除，并解除对应 ARP 绑定
     */
    @DeleteMapping("/chain/{chainId}")
    public Result<String> deleteChainRules(@PathVariable Long chainId) {
        log.info("==========================================");
        log.info("【信息发布客户端】收到删除链路规则请求");
        log.info("  链路ID: {}", chainId);
        log.info("==========================================");
        try {
            int count = proxyRuleManager.disableRuleByChainId(chainId);
            log.info("✅ 链路 {} 规则已处理，共 {} 条", chainId, count);
            return Result.ok("规则已停止并标记删除", String.valueOf(count));
        } catch (Exception e) {
            log.error("删除链路规则异常: chainId={}", chainId, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 查询本地规则列表（调试用，网关无此接口）
     */
    @GetMapping("/rules")
    public Result<List<UdpProxyRule>> listRules() {
        List<UdpProxyRule> rules = proxyRuleManager.listAllRules();
        return Result.ok(rules);
    }

    /**
     * 转换配置DTO为规则实体（照搬网关 convertToRule 逻辑）
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

        // 设置源IP白名单
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
        // 客户端特有：发布网关MAC地址（ARP绑定使用）
        rule.setGatewayMac(dto.getGatewayMac());

        return rule;
    }
}
