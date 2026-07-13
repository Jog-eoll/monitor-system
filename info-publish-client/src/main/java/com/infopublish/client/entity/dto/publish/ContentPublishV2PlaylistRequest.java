package com.infopublish.client.entity.dto.publish;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ContentPublishV2PlaylistRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String playlistId;

    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer orderNo;

        private String fileName;

        private String fileType;

        private Integer durationSeconds;

        private String fileHash;
    }
}
