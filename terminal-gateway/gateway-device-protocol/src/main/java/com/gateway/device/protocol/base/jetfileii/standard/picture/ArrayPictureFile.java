package com.gateway.device.protocol.base.jetfileii.standard.picture;

import lombok.Builder;
import lombok.Data;

/**
 * ArrayPictureFile 完整结构（多帧点阵图片文件）。
 * <p>
 * 对应文档 Table7.1.1 完整布局:
 * PictureFileHeader(31B)
 * | FrameHead[1] ... FrameHead[N]  (N = header.totalFrame)
 * | PixelData[1] ... PixelData[N]  (每帧 = header.frameDataSize)
 * | ExtFrameStruct[1] ... ExtFrameStruct[N]  (仅 extRows>0 的帧)
 */
@Data
@Builder
public class ArrayPictureFile {

    private ArrayPictureFileHeader header;
    private FrameHead[] frameHeads;
    private byte[][] framePixelData;
    private byte[][] extFrameStructs;
}
