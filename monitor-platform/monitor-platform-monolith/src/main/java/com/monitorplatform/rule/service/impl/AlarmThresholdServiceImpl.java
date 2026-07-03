package com.monitorplatform.rule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.rule.entity.AlarmThreshold;
import com.monitorplatform.rule.mapper.AlarmThresholdMapper;
import com.monitorplatform.rule.service.AlarmThresholdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警阈值服务实现
 */
@Service
public class AlarmThresholdServiceImpl implements AlarmThresholdService {
    
    @Autowired
    private AlarmThresholdMapper thresholdMapper;
    
    @Override
    public Page<AlarmThreshold> page(Integer pageNum, Integer pageSize) {
        Page<AlarmThreshold> page = new Page<>(pageNum, pageSize);
        return thresholdMapper.selectPage(page, null);
    }
    
    @Override
    public boolean add(AlarmThreshold threshold) {
        return thresholdMapper.insert(threshold) > 0;
    }
    
    @Override
    public boolean update(AlarmThreshold threshold) {
        return thresholdMapper.updateById(threshold) > 0;
    }
    
    @Override
    public boolean delete(Long id) {
        return thresholdMapper.deleteById(id) > 0;
    }
    
    @Override
    public boolean updateStatus(Long id, String status) {
        AlarmThreshold threshold = new AlarmThreshold();
        threshold.setId(id);
        threshold.setStatus(status);
        return thresholdMapper.updateById(threshold) > 0;
    }
    
    @Override
    public List<AlarmThreshold> listAll() {
        LambdaQueryWrapper<AlarmThreshold> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AlarmThreshold::getAlertLevel);
        return thresholdMapper.selectList(wrapper);
    }
}
