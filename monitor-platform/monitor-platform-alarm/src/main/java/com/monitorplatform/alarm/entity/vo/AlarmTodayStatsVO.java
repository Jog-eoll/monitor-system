package com.monitorplatform.alarm.entity.vo;

import lombok.Data;

@Data
public class AlarmTodayStatsVO {
    private Long total;
    private Long handled;
    private Long falseAlarm;
    private Long pending;
}
