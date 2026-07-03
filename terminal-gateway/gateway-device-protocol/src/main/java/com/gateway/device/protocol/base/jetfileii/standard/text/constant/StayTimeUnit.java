package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.4 停留时间单位 (0x0E)。
 */
@Getter
@AllArgsConstructor
public enum StayTimeUnit {
    SECOND_2D((byte) '0', 2),
    MS_2D((byte) '1', 2),
    SECOND_4D((byte) '2', 4),
    MS_4D((byte) '3', 4);

    private final byte code;
    private final int digits;
}
