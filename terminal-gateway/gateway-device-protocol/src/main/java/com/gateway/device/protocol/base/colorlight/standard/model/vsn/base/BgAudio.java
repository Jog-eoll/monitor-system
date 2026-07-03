package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目页背景音频。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgAudio {
    private FileSource fileSource;
    private Integer volume;
}
