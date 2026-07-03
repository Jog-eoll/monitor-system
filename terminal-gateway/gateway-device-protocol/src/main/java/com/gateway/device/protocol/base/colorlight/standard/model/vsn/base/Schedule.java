package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目排程，控制素材在特定时间范围内播放。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {
    /**
     * 是否限制时间: 0=否, 1=是
     */
    private Integer isLimitTime;
    /**
     * 开始时间 HH:mm:ss
     */
    private String startTime;
    /**
     * 结束时间 HH:mm:ss
     */
    private String endTime;
    /**
     * 是否限制日期: 0=否, 1=是
     */
    private Integer isLimitDate;
    /**
     * 开始日期 yyyy/MM/dd
     */
    private String startDay;
    /**
     * 开始日期时间 HH:mm:ss
     */
    private String startDayTime;
    /**
     * 结束日期 yyyy/MM/dd
     */
    private String endDay;
    /**
     * 结束日期时间 HH:mm:ss
     */
    private String endDayTime;
    /**
     * 是否限制星期: 0=否, 1=是
     */
    private Integer isLimitWeek;
    /**
     * 星期限制数组 "1,1,1,1,1,1,1"，0=关闭 1=播放
     */
    private String limitWeek;
}
