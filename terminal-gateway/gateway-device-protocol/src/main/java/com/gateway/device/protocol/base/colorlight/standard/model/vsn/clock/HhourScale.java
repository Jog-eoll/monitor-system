package com.gateway.device.protocol.base.colorlight.standard.model.vsn.clock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时钟时标样式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HhourScale {
    private String clr;
    @Builder.Default
    private Integer shape = 2;
    private Integer width;
    private Integer height;
}
