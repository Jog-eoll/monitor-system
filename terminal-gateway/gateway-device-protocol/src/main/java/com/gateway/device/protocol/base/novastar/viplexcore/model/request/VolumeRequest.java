package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 音量控制请求 —— {@code {"sn":"...","volumeInfo":{"ratio":...}}}。
 *
 * <p>对应 SDK {@code nvSetVolumeAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 音量信息
     */
    private VolumeInfo volumeInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeInfo {

        /**
         * 音量百分比 (0.0-100.0)
         */
        private double ratio;
    }
}
