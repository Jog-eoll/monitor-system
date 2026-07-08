package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 SN 请求 —— {@code {"sn":"xxx"}}。
 *
 * <p>同时服务于 get 类接口：获取亮度、获取字体、获取 AP 状态等（仅需 SN 参数）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnRequest {

    /**
     * 设备序列号
     */
    private String sn;
}
