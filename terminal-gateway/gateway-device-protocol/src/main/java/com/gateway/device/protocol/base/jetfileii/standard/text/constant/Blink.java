package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.1 闪烁 (0x07)。
 */
@Getter
@AllArgsConstructor
public enum Blink {
    OFF('0', "关闭"),
    ON('1', "打开");

    private final char code;
    private final String label;
}
