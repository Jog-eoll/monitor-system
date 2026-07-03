package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目信息（屏幕宽高）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Information {
    private Integer width;
    private Integer height;
}
