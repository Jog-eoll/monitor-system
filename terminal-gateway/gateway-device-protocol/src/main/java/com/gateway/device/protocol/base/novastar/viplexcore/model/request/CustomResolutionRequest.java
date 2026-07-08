package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义分辨率请求 —— {@code {"sn":"...","info":{"displayMode":...,"width":...,"height":...}}}。
 *
 * <p>对应 SDK {@code nvSetCustomResolutionAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomResolutionRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 分辨率信息
     */
    private Info info;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Info {

        /**
         * 显示模式
         */
        private int displayMode;

        /**
         * 宽度（像素）
         */
        private int width;

        /**
         * 高度（像素）
         */
        private int height;
    }
}
