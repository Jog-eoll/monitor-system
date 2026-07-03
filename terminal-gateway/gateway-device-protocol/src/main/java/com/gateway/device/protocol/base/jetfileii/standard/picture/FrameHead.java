package com.gateway.device.protocol.base.jetfileii.standard.picture;

import lombok.Builder;
import lombok.Data;

/**
 * ArrayPictureFile 帧头控制结构。
 *
 * <p>对应文档第 16.3 节 FrameHeadStruct:
 * <pre>
 *   Head(2B, 固定 'F''H') | Rows(1B) | ExtRows(1B)
 *   | RowState[1..16](各4B)
 *   | TimeType(1B) | Speed(1B) | Dir(2B) | StayTime(4B) | REV(4B)
 * </pre>
 * </p>
 */
@Data
@Builder
public class FrameHead {

    public static final short HEAD_MAGIC = (short) 0x4846;  // 'F''H' LE

    /**
     * 帧头标识，固定 'F''H' (0x4846)
     */
    @Builder.Default
    private short headFlag = HEAD_MAGIC;
    /**
     * 该帧行数 (1-16)
     */
    private byte rows;
    /**
     * 总行数 - rows (扩展行数，>16行时有效)
     */
    private byte extRows;
    /**
     * 每行的控制状态
     */
    private RowState[] rowStates;
    /**
     * 0=毫秒, 1=秒
     */
    private byte timeType;
    /**
     * 播放速度: '0'最快 ~ '6'最慢
     */
    private byte speed;
    /**
     * 移动方向: 0x30=左移, 0x31=右移（头尾接龙模式）
     */
    private short direction;
    /**
     * 停留时间
     */
    private int stayTime;
    /**
     * 保留
     */
    private int reserved;

    public boolean isMilliseconds() {
        return timeType == 0;
    }
}
