package com.gateway.device.protocol.base.colorlight.standard.codec;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * ColorLight HTTP 请求模型 —— protocol 层使用自定义 {@link ColorLightHttpMethod} 枚举。
 */
@Data
@Builder
public class ColorLightHttpRequest {
    private ColorLightHttpMethod method;
    private String uri;
    private String host;
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
    private byte[] body;
    /**
     * 完整 Authorization 头值（含方案前缀，如 "Basic xxx" / "Bearer xxx"）
     */
    private String authorization;
}
