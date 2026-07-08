package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 配屏（点阵像素）请求 —— {@code {"sn":"...","screenAttribute":{"screenAttributes":[...]}}}。
 *
 * <p>对应 SDK {@code nvSetScreenAttributeAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenAttributeRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 屏幕属性包装
     */
    private ScreenAttributeWrapper screenAttribute;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenAttributeWrapper {

        /**
         * 屏幕属性列表
         */
        private List<ScreenAttr> screenAttributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenAttr {

        /**
         * 显示屏 ID
         */
        private int id;

        /**
         * 配屏参数来源
         */
        private int screenSource;

        /**
         * X 方向接收卡个数
         */
        private int xCount;

        /**
         * Y 方向接收卡个数
         */
        private int yCount;

        /**
         * X 方向显示偏移
         */
        private int xOffset;

        /**
         * Y 方向显示偏移
         */
        private int yOffset;

        /**
         * 网口数量
         */
        private int portNumber;

        /**
         * 走线顺序
         */
        private List<Integer> orders;

        /**
         * 扫描信息列表
         */
        private List<ScanInfo> scanInfos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanInfo {

        /**
         * 接收卡带载宽度
         */
        private int width;

        /**
         * 接收卡带载高度
         */
        private int height;

        /**
         * X 坐标
         */
        @Builder.Default
        private int x = 0;

        /**
         * Y 坐标
         */
        @Builder.Default
        private int y = 0;

        /**
         * 网口内 X 偏移
         */
        @Builder.Default
        private int xInPort = 0;

        /**
         * 网口内 Y 偏移
         */
        @Builder.Default
        private int yInPort = 0;

        /**
         * 网口索引
         */
        @Builder.Default
        private int portIndex = 0;

        /**
         * 连接索引
         */
        @Builder.Default
        private int connectIndex = 0;
    }
}
