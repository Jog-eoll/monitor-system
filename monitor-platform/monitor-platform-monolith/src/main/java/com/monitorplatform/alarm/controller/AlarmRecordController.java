package com.monitorplatform.alarm.controller;

import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.service.AlarmRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Alarm record controller.
 */
@RestController
@RequestMapping("alarmRecord")
@Slf4j
public class AlarmRecordController {
    private static final int DEFAULT_PAGE_INDEX = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private AlarmRecordService alarmRecordService;

    @GetMapping
    public ResponseEntity<Page<AlarmRecord>> queryByPage(AlarmRecord alarmRecord,
                                                         @RequestParam(value = "page", required = false) String page,
                                                         @RequestParam(value = "size", required = false) String size,
                                                         @RequestParam(value = "pageNum", required = false) String pageNum,
                                                         @RequestParam(value = "pageSize", required = false) String pageSize) {
        try {
            PageRequest pageRequest = buildPageRequest(page, size, pageNum, pageSize);
            return ResponseEntity.ok(this.alarmRecordService.queryByPage(alarmRecord, pageRequest));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid alarm record page parameters: page={}, size={}, pageNum={}, pageSize={}",
                    page, size, pageNum, pageSize, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("{id}")
    public ResponseEntity<AlarmRecord> queryById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(this.alarmRecordService.queryById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AlarmRecord> addJson(@RequestBody(required = false) AlarmRecord alarmRecord) {
        return doAdd(alarmRecord);
    }

    @PostMapping
    public ResponseEntity<AlarmRecord> add(AlarmRecord alarmRecord) {
        return doAdd(alarmRecord);
    }

    private ResponseEntity<AlarmRecord> doAdd(AlarmRecord alarmRecord) {
        if (isInvalidCreateRecord(alarmRecord)) {
            log.warn("Invalid alarm record create request: alarmType={}, alarmTime={}",
                    alarmRecord == null ? null : alarmRecord.getAlarmType(),
                    alarmRecord == null ? null : alarmRecord.getAlarmTime());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(this.alarmRecordService.insert(alarmRecord));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AlarmRecord> editJson(@RequestBody(required = false) AlarmRecord alarmRecord) {
        return doEdit(alarmRecord);
    }

    @PutMapping
    public ResponseEntity<AlarmRecord> edit(AlarmRecord alarmRecord) {
        return doEdit(alarmRecord);
    }

    private ResponseEntity<AlarmRecord> doEdit(AlarmRecord alarmRecord) {
        if (isInvalidUpdateRecord(alarmRecord)) {
            log.warn("Invalid alarm record update request: id={}", alarmRecord == null ? null : alarmRecord.getId());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(this.alarmRecordService.update(alarmRecord));
    }

    @DeleteMapping
    public ResponseEntity<Boolean> deleteById(Long id) {
        if (id == null) {
            log.warn("Invalid alarm record delete request: id is null");
            return ResponseEntity.badRequest().body(false);
        }
        return ResponseEntity.ok(this.alarmRecordService.deleteById(id));
    }

    private PageRequest buildPageRequest(String page, String size, String pageNum, String pageSize) {
        int pageIndex;
        if (hasText(page)) {
            pageIndex = parseNonNegativeInt(page, "page");
        } else if (hasText(pageNum)) {
            pageIndex = parsePositiveInt(pageNum, "pageNum") - 1;
        } else {
            pageIndex = DEFAULT_PAGE_INDEX;
        }

        int safePageSize;
        if (hasText(size)) {
            safePageSize = parsePositiveInt(size, "size");
        } else if (hasText(pageSize)) {
            safePageSize = parsePositiveInt(pageSize, "pageSize");
        } else {
            safePageSize = DEFAULT_PAGE_SIZE;
        }
        safePageSize = Math.min(safePageSize, MAX_PAGE_SIZE);
        return PageRequest.of(pageIndex, safePageSize);
    }

    private int parseNonNegativeInt(String value, String name) {
        int parsed = parseInt(value, name);
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0");
        }
        return parsed;
    }

    private int parsePositiveInt(String value, String name) {
        int parsed = parseInt(value, name);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        return parsed;
    }

    private int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private boolean isInvalidCreateRecord(AlarmRecord alarmRecord) {
        return alarmRecord == null || !hasText(alarmRecord.getAlarmType()) || alarmRecord.getAlarmTime() == null;
    }

    private boolean isInvalidUpdateRecord(AlarmRecord alarmRecord) {
        return alarmRecord == null || alarmRecord.getId() == null || !hasAnyUpdateField(alarmRecord);
    }

    private boolean hasAnyUpdateField(AlarmRecord alarmRecord) {
        return hasText(alarmRecord.getAlarmType())
                || hasText(alarmRecord.getAlarmLevel())
                || alarmRecord.getChainId() != null
                || hasText(alarmRecord.getBoardIp())
                || alarmRecord.getBoardPort() != null
                || hasText(alarmRecord.getDeviceId())
                || hasText(alarmRecord.getDeviceName())
                || hasText(alarmRecord.getContentId())
                || hasText(alarmRecord.getViolationType())
                || hasText(alarmRecord.getViolationDetail())
                || alarmRecord.getAlarmTime() != null
                || hasText(alarmRecord.getHandleStatus())
                || hasText(alarmRecord.getHandleOperator())
                || alarmRecord.getHandleTime() != null
                || hasText(alarmRecord.getHandleRemark())
                || alarmRecord.getCreateTime() != null
                || hasText(alarmRecord.getContentType())
                || hasText(alarmRecord.getContentData())
                || hasText(alarmRecord.getContentFileUrl());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
