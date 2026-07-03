package com.monitorplatform.rule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.rule.entity.DetectionRule;
import com.monitorplatform.rule.entity.dto.RuleQueryDTO;

import java.util.List;

/**
 * 检测规则服务
 */
public interface DetectionRuleService {
    
    /**
     * 分页查询规则
     */
    Page<DetectionRule> page(RuleQueryDTO dto);
    
    /**
     * 新增规则
     */
    boolean add(DetectionRule rule);
    
    /**
     * 修改规则
     */
    boolean update(DetectionRule rule);
    
    /**
     * 删除规则
     */
    boolean delete(Long id);
    
    /**
     * 启用/禁用规则
     */
    boolean updateStatus(Long id, String status);
    
    /**
     * 获取所有启用的规则
     */
    List<DetectionRule> listEnabled();
}
