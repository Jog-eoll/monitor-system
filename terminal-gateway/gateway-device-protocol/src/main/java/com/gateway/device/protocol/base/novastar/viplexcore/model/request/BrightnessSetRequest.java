package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 亮度设置请求 —— {@code {"sn":"...","screenBrightnessInfo":{"ratio":...}}}。
 *
 * <p>对应 SDK {@code nvSetScreenBrightnessAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrightnessSetRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 屏幕亮度信息
     */
    private ScreenBrightnessInfo screenBrightnessInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenBrightnessInfo {

        /**
         * 亮度百分比 (0-100)
         */
        private double ratio;
    }
}
