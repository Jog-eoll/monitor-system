package com.monitorplatform.rule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.rule.entity.AlarmThreshold;

import java.util.List;

/**
 * 告警阈值服务
 */
public interface AlarmThresholdService {
    
    /**
     * 分页查询阈值
     */
    Page<AlarmThreshold> page(Integer pageNum, Integer pageSize);
    
    /**
     * 新增阈值
     */
    boolean add(AlarmThreshold threshold);
    
    /**
     * 修改阈值
     */
    boolean update(AlarmThreshold threshold);
    
    /**
     * 删除阈值
     */
    boolean delete(Long id);
    
    /**
     * 启用/禁用阈值
     */
    boolean updateStatus(Long id, String status);
    
    /**
     * 获取所有阈值配置
     */
    List<AlarmThreshold> listAll();
}
