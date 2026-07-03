package com.monitorplatform.rule.entity.dto;

import lombok.Data;

/**
 * 规则配置统计DTO
 */
@Data
public class RuleStatisticsDTO {
    
    /**
     * 关键词总数
     */
    private Long totalKeywords;
    
    /**
     * 启用的关键词数
     */
    private Long enabledKeywords;
    
    /**
     * 禁用的关键词数
     */
    private Long disabledKeywords;
    
    /**
     * 高危关键词数
     */
    private Long highSeverityKeywords;
    
    /**
     * 检测规则总数
     */
    private Long totalRules;
    
    /**
     * 启用的规则数
     */
    private Long enabledRules;
    
    /**
     * 告警阈值配置数
     */
    private Long totalThresholds;
    
    /**
     * 启用的阈值数
     */
    private Long enabledThresholds;
}
