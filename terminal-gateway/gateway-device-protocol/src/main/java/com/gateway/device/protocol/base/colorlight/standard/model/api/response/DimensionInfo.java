package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight /api/dimension.json 响应 POJO。
 *
 * <p>返回屏幕参数（像素时钟、帧率、宽高等）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionInfo {

    private Long dclk;
    private Integer fps;
    private Integer height;
    private Integer hsync;
    private Integer width;
}
