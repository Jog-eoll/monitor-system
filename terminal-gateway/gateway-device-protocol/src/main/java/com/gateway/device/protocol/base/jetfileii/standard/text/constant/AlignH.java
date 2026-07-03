package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.16 水平对齐 (0x1E)。
 */
@Getter
@AllArgsConstructor
public enum AlignH {
    CENTER('0', "居中"),
    LEFT('1', "居左"),
    RIGHT('2', "居右"),
    RESERVED('3', "保留");

    private final char code;
    private final String label;
}
