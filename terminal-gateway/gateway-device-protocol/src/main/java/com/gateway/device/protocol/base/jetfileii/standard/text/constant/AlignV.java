package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.16 垂直对齐 (0x1F)。
 */
@Getter
@AllArgsConstructor
public enum AlignV {
    CENTER('0', "居中"),
    TOP('1', "居上"),
    BOTTOM('2', "居下"),
    RESERVED('3', "保留");

    private final char code;
    private final String label;
}
