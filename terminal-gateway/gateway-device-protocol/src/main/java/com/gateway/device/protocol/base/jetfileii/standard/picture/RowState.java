package com.gateway.device.protocol.base.jetfileii.standard.picture;

import lombok.Builder;
import lombok.Data;

/**
 * ArrayPictureFile 行控制结构。
 * <p>
 * 对应文档 Table7.1.2 FrameHeadStruct 中 RowState 字段:
 * [2B] 行高度
 * [1B] 入花样, bit7=1 表示该行闪烁
 * [1B] 出花样
 */
@Data
@Builder
public class RowState {

    private short rowHeight;
    private byte inEffect;
    private byte outEffect;

    public boolean isBlink() {
        return (inEffect & 0x80) != 0;
    }

    public byte getInEffectRaw() {
        return (byte) (inEffect & 0x7F);
    }
}
