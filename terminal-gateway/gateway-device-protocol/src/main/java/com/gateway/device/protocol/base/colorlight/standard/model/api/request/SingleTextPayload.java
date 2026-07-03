package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/program/singletext 请求体。
 *
 * @deprecated 请使用 VSN 构建器
 * {@link com.gateway.device.protocol.base.colorlight.standard.helper.ColorLightProgramBuilder#buildSingleText}
 * 构建 VSN JSON，配合 {@link ColorLightApi#MEDIA_UPLOAD} 发布
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingleTextPayload {

    private String text;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private FontInfo font;
    private String bgcolor;
    private ScrollInfo scroll;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FontInfo {
        private String name;
        private Integer size;
        private FontStyle style;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FontStyle {
        private Boolean i;
        private Boolean b;
        private Boolean u;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrollInfo {
        private Integer dir;
        private Boolean isconnected;
        private Integer speed;
    }
}
