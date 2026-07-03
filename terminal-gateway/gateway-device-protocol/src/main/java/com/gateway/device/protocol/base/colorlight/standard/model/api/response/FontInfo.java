package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight /api/fonts.json 单条字体信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FontInfo {

    private String name;
    private Long size;
}
