package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight /api/info.json 响应内层 POJO。
 *
 * <p>API 返回 JSON 结构为 {@code {"info": {...}}}，使用 {@link Wrapper} 解析外层包裹。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {

    private String vername;
    private String serialno;
    private String model;
    private Long up;

    @JsonProperty("mem")
    private MemoryInfo mem;

    private StorageInfo storage;
    private PlayingInfo playing;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Wrapper {
        private DeviceInfo info;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryInfo {
        private Long total;
        private Long free;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageInfo {
        private Long total;
        private Long free;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayingInfo {
        private String name;
        private String source;
    }
}
