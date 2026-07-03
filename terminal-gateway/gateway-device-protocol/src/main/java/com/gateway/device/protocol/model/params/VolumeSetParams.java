package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 音量设置参数 — VOLUME_SET。
 *
 * <p>SDK: nvSetVolumeAsync，ratio 0-100 音量百分比，默认 60。</p>
 */
@Data
@Builder
public class VolumeSetParams implements CommandParams {

    /**
     * 音量百分比 0-100，默认 60
     */
    @Builder.Default
    private int ratio = 60;
}
