package com.gateway.device.protocol.base.colorlight.standard.codec;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * ColorLight HTTP 响应模型。
 */
@Data
@Builder
public class ColorLightHttpResponse {
    private int statusCode;
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
    private byte[] body;
}
