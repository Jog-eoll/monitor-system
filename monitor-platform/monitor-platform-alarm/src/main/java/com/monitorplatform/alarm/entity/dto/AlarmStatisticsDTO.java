package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警统计响应DTO
 */
@Data
public class AlarmStatisticsDTO {

    private Long totalCount;

    private Long pendingCount;

    private Long processedCount;

    private Long criticalCount;

    private Long seriousCount;

    private Map<String, Long> typeCount = new HashMap<>();

    private Map<String, Long> levelCount = new HashMap<>();
}
