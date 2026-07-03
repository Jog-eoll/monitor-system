package com.monitorplatform.alarm.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class WeeklySeriesVO {
    private String level;
    private List<Long> counts;
}
