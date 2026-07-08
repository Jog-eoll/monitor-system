package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校时请求 —— {@code {"sn":"...","currentTime":"...","timeZoneInfo":{...}}}。
 *
 * <p>对应 SDK {@code nvCalibrateTimeAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalibrateTimeRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 当前时间（ISO-8601 格式，如 "2025-01-01T12:00:00+0800"）
     */
    private String currentTime;

    /**
     * 时区信息
     */
    private TimeZoneInfo timeZoneInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeZoneInfo {

        /**
         * UTC 毫秒时间戳
         */
        private long utcTimeMillis;

        /**
         * 时区 ID，如 "Asia/Shanghai"
         */
        private String timeZone;

        /**
         * GMT 偏移，如 "GMT+08:00"
         */
        private String gmt;

        /**
         * 是否启用时间偏移
         */
        @Builder.Default
        private boolean isTimeOffsetEnable = false;

        /**
         * 时间偏移开始时间（空字符串，非 null）
         */
        @Builder.Default
        private String beginTime = "";

        /**
         * 时间偏移结束时间（空字符串，非 null）
         */
        @Builder.Default
        private String endTime = "";

        /**
         * 时间偏移值
         */
        @Builder.Default
        private int timeOffsetValue = 0;
    }
}
