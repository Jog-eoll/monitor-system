package com.monitorplatform.rule.service;

import com.monitorplatform.rule.entity.dto.RuleStatisticsDTO;

/**
 * 规则统计服务
 */
public interface RuleStatisticsService {
    
    /**
     * 获取规则配置统计数据
     */
    RuleStatisticsDTO getStatistics();
}
