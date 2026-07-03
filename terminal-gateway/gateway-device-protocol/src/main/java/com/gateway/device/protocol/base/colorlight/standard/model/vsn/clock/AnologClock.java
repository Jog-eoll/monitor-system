package com.gateway.device.protocol.base.colorlight.standard.model.vsn.clock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模拟时钟配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnologClock {
    private ClockFont clockFont;
    private Integer flags;
    private String hourPinClr;
    private String minutePinClr;
    private String secondPinClr;
}
