package com.monitorplatform.rule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.rule.entity.AlarmThreshold;
import com.monitorplatform.rule.entity.DetectionRule;
import com.monitorplatform.rule.entity.SensitiveKeyword;
import com.monitorplatform.rule.entity.dto.RuleStatisticsDTO;
import com.monitorplatform.rule.mapper.AlarmThresholdMapper;
import com.monitorplatform.rule.mapper.DetectionRuleMapper;
import com.monitorplatform.rule.mapper.SensitiveKeywordMapper;
import com.monitorplatform.rule.service.RuleStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 规则统计服务实现
 */
@Service
public class RuleStatisticsServiceImpl implements RuleStatisticsService {
    
    @Autowired
    private SensitiveKeywordMapper keywordMapper;
    
    @Autowired
    private DetectionRuleMapper ruleMapper;
    
    @Autowired
    private AlarmThresholdMapper thresholdMapper;
    
    @Override
    public RuleStatisticsDTO getStatistics() {
        RuleStatisticsDTO dto = new RuleStatisticsDTO();
        
        // 关键词统计
        Long totalKeywords = keywordMapper.selectCount(null);
        dto.setTotalKeywords(totalKeywords);
        
        LambdaQueryWrapper<SensitiveKeyword> enabledKeywordWrapper = new LambdaQueryWrapper<>();
        enabledKeywordWrapper.eq(SensitiveKeyword::getStatus, "enabled");
        Long enabledKeywords = keywordMapper.selectCount(enabledKeywordWrapper);
        dto.setEnabledKeywords(enabledKeywords);
        dto.setDisabledKeywords(totalKeywords - enabledKeywords);
        
        LambdaQueryWrapper<SensitiveKeyword> highKeywordWrapper = new LambdaQueryWrapper<>();
        highKeywordWrapper.eq(SensitiveKeyword::getSeverity, "high");
        Long highKeywords = keywordMapper.selectCount(highKeywordWrapper);
        dto.setHighSeverityKeywords(highKeywords);
        
        // 规则统计
        Long totalRules = ruleMapper.selectCount(null);
        dto.setTotalRules(totalRules);
        
        LambdaQueryWrapper<DetectionRule> enabledRuleWrapper = new LambdaQueryWrapper<>();
        enabledRuleWrapper.eq(DetectionRule::getStatus, "enabled");
        Long enabledRules = ruleMapper.selectCount(enabledRuleWrapper);
        dto.setEnabledRules(enabledRules);
        
        // 阈值统计
        Long totalThresholds = thresholdMapper.selectCount(null);
        dto.setTotalThresholds(totalThresholds);
        
        LambdaQueryWrapper<AlarmThreshold> enabledThresholdWrapper = new LambdaQueryWrapper<>();
        enabledThresholdWrapper.eq(AlarmThreshold::getStatus, "enabled");
        Long enabledThresholds = thresholdMapper.selectCount(enabledThresholdWrapper);
        dto.setEnabledThresholds(enabledThresholds);
        
        return dto;
    }
}
