package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目窗口区域，定义素材在屏幕上的位置和大小。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayRect {
    /**
     * 起始 X 坐标
     */
    private Integer x;
    /**
     * 起始 Y 坐标
     */
    private Integer y;
    /**
     * 窗口宽度
     */
    private Integer width;
    /**
     * 窗口高度
     */
    private Integer height;
    /**
     * 边框宽度
     */
    private Integer borderWidth;
    /**
     * 边框颜色（8位十六进制整数，如 0xFF000000）
     */
    private String borderColor;
}
