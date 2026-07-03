package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.13 处理模式 (0x1B)。
 */
@Getter
@AllArgsConstructor
public enum ProcessMode {
    SCROLL('a', "强制不换行不排版(连续左移)"),
    AUTO('b', "自动换行换帧排版"),
    RESERVED('c', "保留");

    private final char code;
    private final String label;
}
