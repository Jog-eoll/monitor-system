package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AP 热点开关请求 —— {@code {"sn":"...","enable":true/false}}。
 *
 * <p>对应 SDK {@code nvSetAPNetworkOpenStatusAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApSwitchRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 是否开启 AP 热点
     */
    private boolean enable;
}
