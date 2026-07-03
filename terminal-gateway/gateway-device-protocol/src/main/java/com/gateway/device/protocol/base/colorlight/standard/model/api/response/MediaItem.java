package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight /api/vsns.json 单条媒体项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaItem {

    private String name;
    private Long size;
    private String lastModified;
}
