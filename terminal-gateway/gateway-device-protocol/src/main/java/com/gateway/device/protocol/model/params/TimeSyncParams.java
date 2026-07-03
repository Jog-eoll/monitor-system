package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间同步参数 — TIME_SYNC。
 *
 * <p>提供 targetTime 时用指定时间校时，否则用当前系统时间。</p>
 */
@Data
@Builder
public class TimeSyncParams implements CommandParams {

    /**
     * 目标时间，优先级最高；null 时走 NTP 或当前系统时间
     */
    private LocalDateTime targetTime;

    /**
     * 时区，默认系统时区
     */
    private ZoneId timeZone;

}
