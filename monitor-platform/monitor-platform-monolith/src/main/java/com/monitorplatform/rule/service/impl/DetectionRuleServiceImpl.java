package com.monitorplatform.rule.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.rule.entity.DetectionRule;
import com.monitorplatform.rule.entity.dto.RuleQueryDTO;
import com.monitorplatform.rule.mapper.DetectionRuleMapper;
import com.monitorplatform.rule.service.DetectionRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检测规则服务实现
 */
@Service
public class DetectionRuleServiceImpl implements DetectionRuleService {
    
    @Autowired
    private DetectionRuleMapper ruleMapper;
    
    @Override
    public Page<DetectionRule> page(RuleQueryDTO dto) {
        Page<DetectionRule> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        
        LambdaQueryWrapper<DetectionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(dto.getRuleName()), DetectionRule::getRuleName, dto.getRuleName())
                .eq(StrUtil.isNotBlank(dto.getRuleType()), DetectionRule::getRuleType, dto.getRuleType())
                .eq(StrUtil.isNotBlank(dto.getStatus()), DetectionRule::getStatus, dto.getStatus())
                .orderByDesc(DetectionRule::getPriority)
                .orderByDesc(DetectionRule::getCreateTime);
        
        return ruleMapper.selectPage(page, wrapper);
    }
    
    @Override
    public boolean add(DetectionRule rule) {
        return ruleMapper.insert(rule) > 0;
    }
    
    @Override
    public boolean update(DetectionRule rule) {
        return ruleMapper.updateById(rule) > 0;
    }
    
    @Override
    public boolean delete(Long id) {
        return ruleMapper.deleteById(id) > 0;
    }
    
    @Override
    public boolean updateStatus(Long id, String status) {
        DetectionRule rule = new DetectionRule();
        rule.setId(id);
        rule.setStatus(status);
        return ruleMapper.updateById(rule) > 0;
    }
    
    @Override
    public List<DetectionRule> listEnabled() {
        LambdaQueryWrapper<DetectionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DetectionRule::getStatus, "enabled")
                .orderByDesc(DetectionRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }
}
