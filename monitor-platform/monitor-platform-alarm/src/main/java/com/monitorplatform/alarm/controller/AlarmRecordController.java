package com.monitorplatform.alarm.controller;

import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.service.AlarmRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 告警记录表(AlarmRecord)表控制层
 *
 * @author makejava
 * @since 2026-03-25 18:43:28
 */
@RestController
@RequestMapping("alarmRecord")
public class AlarmRecordController {
    /**
     * 服务对象
     */
    @Resource
    private AlarmRecordService alarmRecordService;

    /**
     * 分页查询
     *
     * @param alarmRecord 筛选条件
     * @param pageRequest      分页对象
     * @return 查询结果
     */
    @GetMapping
    public ResponseEntity<Page<AlarmRecord>> queryByPage(AlarmRecord alarmRecord, PageRequest pageRequest) {
        return ResponseEntity.ok(this.alarmRecordService.queryByPage(alarmRecord, pageRequest));
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("{id}")
    public ResponseEntity<AlarmRecord> queryById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(this.alarmRecordService.queryById(id));
    }

    /**
     * 新增数据
     *
     * @param alarmRecord 实体
     * @return 新增结果
     */
    @PostMapping
    public ResponseEntity<AlarmRecord> add(AlarmRecord alarmRecord) {
        return ResponseEntity.ok(this.alarmRecordService.insert(alarmRecord));
    }

    /**
     * 编辑数据
     *
     * @param alarmRecord 实体
     * @return 编辑结果
     */
    @PutMapping
    public ResponseEntity<AlarmRecord> edit(AlarmRecord alarmRecord) {
        return ResponseEntity.ok(this.alarmRecordService.update(alarmRecord));
    }

    /**
     * 删除数据
     *
     * @param id 主键
     * @return 删除是否成功
     */
    @DeleteMapping
    public ResponseEntity<Boolean> deleteById(Long id) {
        return ResponseEntity.ok(this.alarmRecordService.deleteById(id));
    }

}

