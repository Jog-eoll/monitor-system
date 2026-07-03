package com.infopublish.client.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.infopublish.client.entity.OperationLog;
import com.infopublish.client.repository.OperationLogMapper;
import com.infopublish.client.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    /** 事件类型常量 */
    /**
     * 记录操作日志
     */
    public void log(String eventType, String detail, String status) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setEventType(eventType);
            logEntry.setDetail(detail);
            logEntry.setStatus(status);
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("记录操作日志失败: eventType={}, detail={}", eventType, detail, e);
        }
    }

    /**
     * 记录成功日志
     */
    public void logSuccess(String eventType, String detail) {
        log(eventType, detail, "SUCCESS");
    }

    /**
     * 记录失败日志
     */
    public void logFail(String eventType, String detail) {
        log(eventType, detail, "FAIL");
    }

    /**
     * 查询最近的操作日志
     */
    public List<OperationLog> getRecentLogs(int limit) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OperationLog::getCreateTime)
                .last("LIMIT " + limit);
        return operationLogMapper.selectList(wrapper);
    }

    /**
     * 分页查询操作日志
     */
    public Page<OperationLog> getLogsByPage(int pageNum, int pageSize) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return operationLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 按事件类型查询日志
     */
    public List<OperationLog> getLogsByEventType(String eventType, int limit) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationLog::getEventType, eventType)
                .orderByDesc(OperationLog::getCreateTime)
                .last("LIMIT " + limit);
        return operationLogMapper.selectList(wrapper);
    }
}
