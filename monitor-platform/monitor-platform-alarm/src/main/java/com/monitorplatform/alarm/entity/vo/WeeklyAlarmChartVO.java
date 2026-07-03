package com.monitorplatform.alarm.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class WeeklyAlarmChartVO {
    private List<String> days;
    private List<WeeklySeriesVO> series;
}
