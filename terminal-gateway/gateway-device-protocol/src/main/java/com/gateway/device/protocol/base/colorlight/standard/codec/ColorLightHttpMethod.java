package com.gateway.device.protocol.base.colorlight.standard.codec;

/**
 * HTTP 请求方法枚举 —— protocol 层自定义，不依赖 Netty。
 *
 * <p>core 层 {@code ColorLightHttpCodec} 负责映射到 Netty HttpMethod。</p>
 */
public enum ColorLightHttpMethod {
    GET, POST, PUT, DELETE
}
