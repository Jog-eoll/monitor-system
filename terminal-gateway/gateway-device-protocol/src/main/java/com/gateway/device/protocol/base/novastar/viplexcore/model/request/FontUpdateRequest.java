package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 字体更新请求 —— {@code {"sn":"...","localFontPath":"...","taskInfo":{"fonts":[...]}}}。
 *
 * <p>对应 SDK {@code nvUpdateFontAsync}。
 * 注意：内嵌的 {@link FontInfo} 使用单数形式的协议字段名（"style"/"file"），
 * 与 {@link com.gateway.device.protocol.base.novastar.viplexcore.font.NovaStarFontInfo}
 * 的 Java 命名（复数 styles/files）不同。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FontUpdateRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 本地字体目录路径
     */
    @Builder.Default
    private String localFontPath = "";

    /**
     * 字体任务信息
     */
    private TaskInfo taskInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {

        /**
         * 待同步的字体列表
         */
        private List<FontInfo> fonts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FontInfo {

        /**
         * 字体名称
         */
        private String name;

        /**
         * 样式列表（协议字段名 "style"，单数）
         */
        @JsonProperty("style")
        private List<String> styles;

        /**
         * TTF 文件名列表（协议字段名 "file"，单数）
         */
        @JsonProperty("file")
        private List<String> files;
    }
}
