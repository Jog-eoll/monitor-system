package com.monitorplatform.alarm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.mapper.AlarmMapper;
import com.monitorplatform.alarm.service.AlarmRecordService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 告警记录表(AlarmRecord)表服务实现类
 */
@Service
public class AlarmRecordServiceImpl implements AlarmRecordService {

    @Resource
    private AlarmMapper alarmMapper;

    @Override
    public AlarmRecord queryById(Long id) {
        if (id == null) {
            return null;
        }
        return alarmMapper.selectById(id);
    }

    @Override
    public org.springframework.data.domain.Page<AlarmRecord> queryByPage(AlarmRecord alarmRecord, PageRequest pageRequest) {
        if (alarmRecord == null) {
            alarmRecord = new AlarmRecord();
        }
        if (pageRequest == null) {
            pageRequest = PageRequest.of(0, 10);
        }
        Page<AlarmRecord> page = new Page<>(pageRequest.getPageNumber() + 1, pageRequest.getPageSize());
        QueryWrapper<AlarmRecord> queryWrapper = new QueryWrapper<>();
        if (alarmRecord.getAlarmType() != null) {
            queryWrapper.eq("alarm_type", alarmRecord.getAlarmType());
        }
        if (alarmRecord.getAlarmLevel() != null) {
            queryWrapper.eq("alarm_level", alarmRecord.getAlarmLevel());
        }
        if (alarmRecord.getHandleStatus() != null) {
            queryWrapper.eq("handle_status", alarmRecord.getHandleStatus());
        }
        if (alarmRecord.getDeviceId() != null) {
            queryWrapper.eq("device_id", alarmRecord.getDeviceId());
        }
        queryWrapper.orderByDesc("alarm_time");
        Page<AlarmRecord> resultPage = alarmMapper.selectPage(page, queryWrapper);
        List<AlarmRecord> records = resultPage.getRecords();
        return new PageImpl<>(records, pageRequest, resultPage.getTotal());
    }

    @Override
    public AlarmRecord insert(AlarmRecord alarmRecord) {
        if (alarmRecord == null) {
            throw new IllegalArgumentException("alarmRecord must not be null");
        }
        alarmMapper.insert(alarmRecord);
        return alarmRecord;
    }

    @Override
    public AlarmRecord update(AlarmRecord alarmRecord) {
        if (alarmRecord == null || alarmRecord.getId() == null) {
            throw new IllegalArgumentException("alarmRecord id must not be null");
        }
        alarmMapper.updateById(alarmRecord);
        return queryById(alarmRecord.getId());
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) {
            return false;
        }
        return alarmMapper.deleteById(id) > 0;
    }
}
