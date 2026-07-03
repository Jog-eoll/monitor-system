package com.gateway.device.protocol.base.jetfileii.standard.picture;

import lombok.Builder;
import lombok.Data;

/**
 * ArrayPictureFile 文件头 — 固定 31 字节。
 *
 * <p>对应文档第 16.3 节:
 * <pre>
 *   Head(9B) | Type(1B) | Flag(1B) | Width(2B) | Height(2B)
 *   | BitPerPoint(2B) | TotalFrame(2B) | DataSize(4B) | FrameDataSize(4B)
 *   | LDW(2B) | Rev(2B)
 * </pre>
 * </p>
 */
@Data
@Builder
public class ArrayPictureFileHeader {

    /**
     * 固定 9 字节头部魔术字
     */
    private byte[] head;
    /**
     * 像素类型: 0=RGRG, 1=RG, 2=BRG, 4=BGRN, 5=RGYW
     */
    private byte type;
    /**
     * 0=正常, 1=跳最后一帧（头尾接龙模式）
     */
    private byte flag;
    /**
     * 每帧像素宽度
     */
    private short width;
    /**
     * 每帧像素高度
     */
    private short height;
    /**
     * 每像素 bit 数
     */
    private short bitPerPoint;
    /**
     * 总帧数 (UWORD)
     */
    private short totalFrame;
    /**
     * 帧数据总大小
     */
    private int dataSize;
    /**
     * 每帧数据大小
     */
    private int frameDataSize;
    /**
     * 最后一帧有效宽度（头尾接龙模式）
     */
    private short lastDataWidth;
    /**
     * 保留
     */
    private short reserved;
}
