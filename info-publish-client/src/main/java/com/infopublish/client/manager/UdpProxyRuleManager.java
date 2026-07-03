package com.infopublish.client.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.infopublish.client.entity.UdpProxyRule;
import com.infopublish.client.mapper.UdpProxyRuleMapper;
import com.infopublish.client.service.ArpBindService;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP代理规则管理器（照搬 publish-gateway 的 UdpProxyRuleManager 框架）
 *
 * 与网关的差异：
 *   网关：startProxyServer() / stopProxyServer() → 启停 UDP/TCP 代理服务
 *   客户端：startArpBind() / stopArpBind()       → 执行/解除 ARP 静态绑定
 *
 * 客户端特有能力：
 *   - UKey 拔出 → disableAllRulesOnUkeyRemoval()  → 全部 DISABLED + ARP unbind
 *   - UKey 认证 → reactivateRulesOnUkeyAuthenticated() → 全部恢复 ENABLED + ARP bind
 */
@Component
@Slf4j
public class UdpProxyRuleManager {

    @Resource
    private UdpProxyRuleMapper ruleMapper;

    @Resource
    private ArpBindService arpBindService;

    @Resource
    private UkeyLifecycleManager lifecycleManager;

    /** 运行中的 ARP 绑定（Key: ruleId, Value: listenIp） */
    private final Map<String, String> runningBindings = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadAndStartRules();
    }

    /**
     * 启动时加载并启动所有 ENABLED 规则（照搬网关逻辑）
     * 区别：网关启动 UDP/TCP 代理服务，客户端执行 ARP 绑定
     * 注意：启动时 UKey 可能尚未认证，此时仅打印规则列表，不执行 ARP 绑定
     */
    private void loadAndStartRules() {
        try {
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getStatus, "ENABLED");
            List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

            if (rules.isEmpty()) {
                log.info("==========================================");
                log.info("【信息发布客户端】启动检测：无 ENABLED 状态的规则");
                log.info("==========================================");
                return;
            }

            log.info("==========================================");
            log.info("【信息发布客户端】启动检测：发现 {} 条 ENABLED 规则", rules.size());
            log.info("==========================================");

            for (UdpProxyRule rule : rules) {
                log.info("  规则 [{}]: 监听 {}:{} → 目标 {}:{}, 网关MAC: {}",
                        rule.getRuleId(),
                        rule.getListenIp(), rule.getListenPort(),
                        rule.getTargetIp(), rule.getTargetPort(),
                        rule.getGatewayMac() != null ? rule.getGatewayMac() : "未配置");

                try {
                    startArpBind(rule);
                } catch (Exception e) {
                    log.error("❌ ARP绑定失败: {}，原因: {}，将规则标记为DISABLED",
                            rule.getRuleId(), e.getMessage());
                    disableRuleInDb(rule.getId(), "启动失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("加载代理规则失败", e);
        }
    }

    /**
     * 添加或更新规则（照搬网关 addOrUpdateRule）
     */
    public boolean addOrUpdateRule(UdpProxyRule rule) {
        try {
            // 停止旧的 ARP 绑定
            stopArpBind(rule.getRuleId());

            // 保存到数据库
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getRuleId, rule.getRuleId());
            UdpProxyRule existing = ruleMapper.selectOne(wrapper);

            if (existing != null) {
                rule.setId(existing.getId());
                ruleMapper.updateById(rule);
            } else {
                ruleMapper.insert(rule);
            }

            // 如果规则是 ENABLED 状态，尝试执行 ARP 绑定
            if ("ENABLED".equals(rule.getStatus())) {
                try {
                    startArpBind(rule);
                } catch (Exception e) {
                    log.warn("规则 [{}] 已保存，但 ARP 绑定失败: {}", rule.getRuleId(), e.getMessage());
                }
            }

            return true;
        } catch (Exception e) {
            log.error("添加/更新规则失败", e);
            return false;
        }
    }

    /**
     * 按链路ID切换所有关联规则的状态（照搬网关 setChainRulesStatus）
     *
     * @param chainId 链路ID
     * @param status  目标状态："ENABLED" 或 "DISABLED"
     * @return 处理的规则数量
     */
    public int setChainRulesStatus(Long chainId, String status) {
        log.info("==========================================");
        log.info("  【信息发布客户端】收到链路状态切换通知");
        log.info("  链路ID: {}, 目标状态: {}", chainId, status);
        log.info("==========================================");

        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getChainId, chainId)
               .eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("【信息发布客户端】链路 {} 下无有效规则，无需处理", chainId);
            return 0;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                if ("DISABLED".equals(status)) {
                    // 停用：解除 ARP 绑定
                    stopArpBind(rule.getRuleId());
                } else if ("ENABLED".equals(status)) {
                    // 启用：先清理旧状态，再执行 ARP 绑定
                    stopArpBind(rule.getRuleId());
                    try {
                        startArpBind(rule);
                    } catch (Exception e) {
                        log.warn("规则 [{}] ARP 绑定失败: {}", rule.getRuleId(), e.getMessage());
                    }
                }
                // 更新数据库状态
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                      .set(UdpProxyRule::getStatus, status)
                      .set(UdpProxyRule::getUpdateTime, LocalDateTime.now());
                ruleMapper.update(null, update);

                log.info("✅ 规则状态已更新: ruleId={}, status={}", rule.getRuleId(), status);
                count++;
            } catch (Exception e) {
                log.error("❌ 处理规则失败: ruleId={}", rule.getRuleId(), e);
            }
        }

        log.info("【信息发布客户端】链路 {} 规则状态切换完毕，共处理 {} 条", chainId, count);
        return count;
    }

    /**
     * 按链路ID逻辑删除所有关联规则（照搬网关 disableRuleByChainId）
     *
     * @param chainId 链路ID
     * @return 处理的规则数量
     */
    public int disableRuleByChainId(Long chainId) {
        log.info("==========================================");
        log.info("  【信息发布客户端】收到链路删除通知，停止并逻辑删除规则");
        log.info("  链路ID: {}", chainId);
        log.info("==========================================");

        // 查询该链路下所有未删除的规则
        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getChainId, chainId)
               .eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("【信息发布客户端】链路 {} 下无有效规则，无需处理", chainId);
            return 0;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                // 1. 解除 ARP 绑定
                stopArpBind(rule.getRuleId());

                // 2. 逻辑删除数据库记录（deleted=1）
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                      .set(UdpProxyRule::getDeleted, 1)
                      .set(UdpProxyRule::getStatus, "DISABLED")
                      .set(UdpProxyRule::getRemark, "链路已删除")
                      .set(UdpProxyRule::getUpdateTime, LocalDateTime.now());
                ruleMapper.update(null, update);

                log.info("✅ 规则已逻辑删除: ruleId={}, chainId={}", rule.getRuleId(), chainId);
                count++;
            } catch (Exception e) {
                log.error("❌ 处理规则失败: ruleId={}", rule.getRuleId(), e);
            }
        }

        log.info("【信息发布客户端】链路 {} 规则处理完毕，共处理 {} 条", chainId, count);
        return count;
    }

    // ========== 客户端特有：UKey 联动方法 ==========

    /**
     * UKey 拔出时统一处理：遍历所有 ENABLED 规则，批量 DISABLED + 批量 ARP unbind
     * 原因：UKey 不在 = 不允许数据发送，必须立即切断所有 ARP 绑定
     */
    public void disableAllRulesOnUkeyRemoval() {
        log.info("==========================================");
        log.info("【信息发布客户端】UKey 拔出，禁用所有链路规则");
        log.info("==========================================");

        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getStatus, "ENABLED");
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("当前无 ENABLED 规则，无需处理");
            return;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                // 解除 ARP 绑定
                stopArpBind(rule.getRuleId());

                // 更新状态为 DISABLED
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                      .set(UdpProxyRule::getStatus, "DISABLED")
                      .set(UdpProxyRule::getRemark, "UKey拔出自动禁用")
                      .set(UdpProxyRule::getUpdateTime, LocalDateTime.now());
                ruleMapper.update(null, update);

                log.info("✅ 规则已禁用: ruleId={}", rule.getRuleId());
                count++;
            } catch (Exception e) {
                // 单条失败不中断整体解绑
                log.error("❌ 禁用规则失败: ruleId={}", rule.getRuleId(), e);
            }
        }

        log.info("【信息发布客户端】UKey 拔出处理完毕，共禁用 {} 条规则", count);
    }

    /**
     * UKey 认证成功后恢复：遍历所有未删除规则，重新 ENABLED + 重新 ARP bind
     * 原因：UKey 重新插入认证后，需恢复之前被禁用的链路规则
     */
    public void reactivateRulesOnUkeyAuthenticated() {
        log.info("==========================================");
        log.info("【信息发布客户端】UKey 认证成功，恢复链路规则");
        log.info("==========================================");

        // 查询所有未删除的规则（包括被 UKey 拔出禁用的）
        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getDeleted, 0);
        List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            log.info("当前无待恢复规则");
            return;
        }

        int count = 0;
        for (UdpProxyRule rule : rules) {
            try {
                // 执行 ARP 绑定
                startArpBind(rule);

                // 更新状态为 ENABLED
                LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
                update.eq(UdpProxyRule::getId, rule.getId())
                      .set(UdpProxyRule::getStatus, "ENABLED")
                      .set(UdpProxyRule::getRemark, "UKey认证恢复")
                      .set(UdpProxyRule::getUpdateTime, LocalDateTime.now());
                ruleMapper.update(null, update);

                log.info("✅ 规则已恢复: ruleId={}", rule.getRuleId());
                count++;
            } catch (Exception e) {
                log.error("❌ 恢复规则失败: ruleId={}", rule.getRuleId(), e);
            }
        }

        log.info("【信息发布客户端】链路规则恢复完毕，共恢复 {} 条", count);
    }

    /**
     * 判断是否有活跃规则
     */
    public boolean hasActiveRules() {
        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getStatus, "ENABLED");
        return ruleMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查询所有未删除规则
     */
    public List<UdpProxyRule> listAllRules() {
        LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UdpProxyRule::getDeleted, 0)
               .orderByDesc(UdpProxyRule::getUpdateTime);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 停止所有 ARP 绑定
     */
    public void stopAll() {
        for (Map.Entry<String, String> entry : runningBindings.entrySet()) {
            try {
                arpBindService.unbind(entry.getValue());
                log.info("ARP绑定已解除: ruleId={}, ip={}", entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("解除ARP绑定失败: ruleId={}", entry.getKey(), e);
            }
        }
        runningBindings.clear();
    }

    // ========== ARP 替代 Proxy 的 start/stop ==========

    /**
     * 执行 ARP 静态绑定（替代网关的 startProxyServer）
     * 原因：客户端通过 ARP 欺骗将发往情报板的流量重定向到发布网关
     *
     * @param rule 代理规则（listenIp=情报板IP, gatewayMac=发布网关MAC）
     */
    private void startArpBind(UdpProxyRule rule) {
        // 仅在 UKey 已认证时才执行 ARP 绑定
        if (!lifecycleManager.isAuthenticated()) {
            log.info("UKey 未认证，规则 [{}] 已保存但暂不执行 ARP 绑定", rule.getRuleId());
            return;
        }

        String targetIp = rule.getListenIp();
        String mac = rule.getGatewayMac();

        if (targetIp == null || targetIp.isEmpty()) {
            log.warn("规则 [{}] 的 listenIp（情报板IP）为空，跳过 ARP 绑定", rule.getRuleId());
            return;
        }

        if (mac == null || mac.isEmpty()) {
            log.warn("规则 [{}] 的 gatewayMac（发布网关MAC）为空，跳过 ARP 绑定", rule.getRuleId());
            return;
        }

        String result = arpBindService.bind(targetIp, mac);
        runningBindings.put(rule.getRuleId(), targetIp);
        log.info("✅ ARP绑定成功: ruleId={}, {} -> {}, 结果: {}",
                rule.getRuleId(), targetIp, mac, result);
    }

    /**
     * 解除 ARP 静态绑定（替代网关的 stopProxyServer）
     *
     * @param ruleId 规则ID
     */
    private void stopArpBind(String ruleId) {
        String ip = runningBindings.remove(ruleId);
        if (ip != null) {
            String result = arpBindService.unbind(ip);
            if (result.contains("失败") || result.contains("仍存在")) {
                log.error("❌ ARP解绑失败: ruleId={}, ip={}, 结果: {}", ruleId, ip, result);
            } else {
                log.info("✅ ARP绑定已解除: ruleId={}, ip={}, 结果: {}", ruleId, ip, result);
            }
        }
    }

    /**
     * 将规则在数据库中标记为 DISABLED，并记录失败原因（照搬网关 disableRuleInDb）
     */
    private void disableRuleInDb(Long id, String reason) {
        if (id == null) return;
        try {
            LambdaUpdateWrapper<UdpProxyRule> update = new LambdaUpdateWrapper<>();
            update.eq(UdpProxyRule::getId, id)
                    .set(UdpProxyRule::getStatus, "DISABLED")
                    .set(UdpProxyRule::getRemark, reason);
            ruleMapper.update(null, update);
            log.info("✅ 已将规则[id={}]状态更新为DISABLED，原因: {}", id, reason);
        } catch (Exception ex) {
            log.error("更新规则状态失败[id={}]", id, ex);
        }
    }
}

