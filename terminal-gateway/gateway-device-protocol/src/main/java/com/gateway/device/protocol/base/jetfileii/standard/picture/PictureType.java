package com.gateway.device.protocol.base.jetfileii.standard.picture;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * PictureFile/ArrayPictureFile 像素类型。
 * <p>
 * 对应文档 Table7.1.1 字段 B (Type) 及 Table7.1.3 数据排列:
 * 0=RGRGRGRG(2bit) 1=RG(16bit,8:8) 2=BRG(16bit,565)
 * 4=BGRN(32bit)    5=RGYW(4bit)
 */
@Getter
@AllArgsConstructor
public enum PictureType {

    RGRGRGRG(0, 2, 4, "2bit 1:1"),
    RG(1, 16, 1, "16bit 8:8"),
    BRG(2, 16, 1, "16bit 5:6:5"),
    BGRN(4, 32, 1, "32bit 8:8:8:8"),
    RGYW(5, 4, 2, "4bit");

    private final int typeCode;
    private final int bitsPerPoint;
    private final int pointsPerByte;
    private final String description;

    /**
     * 单帧点阵数据字节数 = ceil(bitsPerPoint * width * height / 8)
     */
    public int calcFrameDataSize(int width, int height) {
        return (bitsPerPoint * width * height + 7) / 8;
    }
}
