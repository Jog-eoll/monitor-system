package com.gateway.device.protocol.model.discovery;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 合规分组配置 —— 产品类型对应的规则集合与启用状态。
 *
 * <p>{@code enabled} 仅对 "default" 分组有意义：显式分组始终视为启用，
 * default 分组可通过 {@code enabled: false} 关闭 fallback。</p>
 *
 * <pre>
 * compliance:
 *   NOVA_STAR_VIPLEX_CORE:
 *     TB4-series:              # 显式分组（始终启用）
 *       rules:
 *         - field: productName
 *           expected: ["TB4"]
 *     default:                 # fallback 分组
 *       enabled: true          # 默认 true，可省略
 *       rules: []
 * </pre>
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceGroupConfig {

    /**
     * 是否启用，仅 default 分组使用（null = 默认 true）
     */
    private Boolean enabled;

    /**
     * 合规规则列表
     */
    private List<ComplianceRule> rules = new ArrayList<>();
}
