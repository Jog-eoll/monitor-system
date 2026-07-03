package com.gateway.device.protocol.base.jetfileii.standard.model;

import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import lombok.Data;

/**
 * DEFAULT_SET — 设备默认显示样式 (52B, packed)。
 *
 * <p>对应 czReadDefDisplayStyle / czWriteDefDisplayStyle 操作的结构体。
 * 参考 czStructsDefine.h DEFAULT_SET 定义。</p>
 *
 * <pre>
 *   偏移  大小  字段
 *   0     2B    ID (0x55AA)
 *   2     1B    playListLoc
 *   3     1B    timePre0En
 *   4     1B    ddrive
 *   5     1B    dbackColor
 *   6     1B    dfontColor
 *   7     1B    dhorJust
 *   8     1B    dverJust
 *   9     1B    dlineSpace
 *   10    1B    dfont
 *   11    1B    dinMode
 *   12    1B    doutMode
 *   13    1B    dspeed
 *   14    1B    dstayTime
 *   15    1B    dwrap
 *   16    4B    lstayTime
 *   20    1B    timeFormat
 *   21    1B    headTailPlayMode
 *   22    1B    headTailMoveDirection
 *   23    1B    headTailMoveSpeed
 *   24    1B    headTailPauseTime
 *   25    1B    playNum
 *   26    2B    version
 *   28    2B    showDefaultBmpTime
 *   30    22B   rev
 * </pre>
 */
@Data
public class DefaultSet {

    private static final short MAGIC = (short) 0x55AA;
    private static final int SIZE = 52;
    private final byte[] rev = new byte[22];
    private short id = MAGIC;
    private byte playListLoc;
    private byte timePre0En;
    private byte ddrive;
    private byte dbackColor;
    private byte dfontColor;
    private byte dhorJust;
    private byte dverJust;
    private byte dlineSpace;
    private byte dfont;
    private byte dinMode;
    private byte doutMode;
    private byte dspeed;
    private byte dstayTime;
    private byte dwrap;
    private int lstayTime;
    private byte timeFormat;
    private byte headTailPlayMode;
    private byte headTailMoveDirection;
    private byte headTailMoveSpeed;
    private byte headTailPauseTime;
    private byte playNum;
    private short version;
    private short showDefaultBmpTime;

    public static DefaultSet fromBytes(byte[] data, int offset) {
        DefaultSet ds = new DefaultSet();
        ds.id = (short) LittleEndianByteBufUtils.readUShortLE(data, offset);
        ds.playListLoc = data[offset + 2];
        ds.timePre0En = data[offset + 3];
        ds.ddrive = data[offset + 4];
        ds.dbackColor = data[offset + 5];
        ds.dfontColor = data[offset + 6];
        ds.dhorJust = data[offset + 7];
        ds.dverJust = data[offset + 8];
        ds.dlineSpace = data[offset + 9];
        ds.dfont = data[offset + 10];
        ds.dinMode = data[offset + 11];
        ds.doutMode = data[offset + 12];
        ds.dspeed = data[offset + 13];
        ds.dstayTime = data[offset + 14];
        ds.dwrap = data[offset + 15];
        ds.lstayTime = (int) LittleEndianByteBufUtils.readUIntLE(data, offset + 16);
        ds.timeFormat = data[offset + 20];
        ds.headTailPlayMode = data[offset + 21];
        ds.headTailMoveDirection = data[offset + 22];
        ds.headTailMoveSpeed = data[offset + 23];
        ds.headTailPauseTime = data[offset + 24];
        ds.playNum = data[offset + 25];
        ds.version = (short) LittleEndianByteBufUtils.readUShortLE(data, offset + 26);
        ds.showDefaultBmpTime = (short) LittleEndianByteBufUtils.readUShortLE(data, offset + 28);
        System.arraycopy(data, offset + 30, ds.rev, 0, 22);
        return ds;
    }

    public byte[] toBytes() {
        byte[] buf = new byte[SIZE];
        LittleEndianByteBufUtils.writeUShortLE(buf, 0, id);
        buf[2] = playListLoc;
        buf[3] = timePre0En;
        buf[4] = ddrive;
        buf[5] = dbackColor;
        buf[6] = dfontColor;
        buf[7] = dhorJust;
        buf[8] = dverJust;
        buf[9] = dlineSpace;
        buf[10] = dfont;
        buf[11] = dinMode;
        buf[12] = doutMode;
        buf[13] = dspeed;
        buf[14] = dstayTime;
        buf[15] = dwrap;
        LittleEndianByteBufUtils.writeUIntLE(buf, 16, lstayTime);
        buf[20] = timeFormat;
        buf[21] = headTailPlayMode;
        buf[22] = headTailMoveDirection;
        buf[23] = headTailMoveSpeed;
        buf[24] = headTailPauseTime;
        buf[25] = playNum;
        LittleEndianByteBufUtils.writeUShortLE(buf, 26, version);
        LittleEndianByteBufUtils.writeUShortLE(buf, 28, showDefaultBmpTime);
        System.arraycopy(rev, 0, buf, 30, 22);
        return buf;
    }
}
