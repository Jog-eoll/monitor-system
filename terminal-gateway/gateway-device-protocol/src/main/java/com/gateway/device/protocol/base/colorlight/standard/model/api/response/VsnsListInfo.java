package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/vsns.json 完整响应 POJO（含 playing 和 contents 分组）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VsnsListInfo {

    private PlayingInfo playing;
    private List<ContentGroup> contents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayingInfo {
        private String type;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentGroup {
        private String type;
        private List<MediaItem> content;
        private Long ressize;
        private Long unused;
    }
}
