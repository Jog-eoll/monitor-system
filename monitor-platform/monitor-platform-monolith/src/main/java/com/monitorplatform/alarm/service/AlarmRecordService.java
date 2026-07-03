package com.monitorplatform.alarm.service;

import com.monitorplatform.alarm.entity.AlarmRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 告警记录表(AlarmRecord)表服务接口
 *
 * @author makejava
 * @since 2026-03-25 18:43:56
 */
public interface AlarmRecordService {

    /**
     * 通过ID查询单条数据
     *
     * @param id 主键
     * @return 实例对象
     */
    AlarmRecord queryById(Long id);

    /**
     * 分页查询
     *
     * @param alarmRecord 筛选条件
     * @param pageRequest      分页对象
     * @return 查询结果
     */
    Page<AlarmRecord> queryByPage(AlarmRecord alarmRecord, PageRequest pageRequest);

    /**
     * 新增数据
     *
     * @param alarmRecord 实例对象
     * @return 实例对象
     */
    AlarmRecord insert(AlarmRecord alarmRecord);

    /**
     * 修改数据
     *
     * @param alarmRecord 实例对象
     * @return 实例对象
     */
    AlarmRecord update(AlarmRecord alarmRecord);

    /**
     * 通过主键删除数据
     *
     * @param id 主键
     * @return 是否成功
     */
    boolean deleteById(Long id);

}
