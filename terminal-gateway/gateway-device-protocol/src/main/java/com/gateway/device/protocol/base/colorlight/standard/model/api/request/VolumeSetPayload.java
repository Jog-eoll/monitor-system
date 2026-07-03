package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight PUT /api/volume 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeSetPayload {

    private int musicvolume;
}
