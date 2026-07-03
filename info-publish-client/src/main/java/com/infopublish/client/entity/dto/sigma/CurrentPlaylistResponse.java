package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Sigma 获取当前待播放列表响应
 * <p>
 * 对应 Sigma 对接文档 Section 8.1。
 * </p>
 */
@Data
public class CurrentPlaylistResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    /** 待播放列表 ID */
    private String playlistId;

    /** 待播放列表版本 */
    private Integer playlistVersion;

    /** 待播放列表状态: READY / LOCKED / PUBLISHING */
    private String status;

    /** 播放文件列表 */
    private List<PlayFile> playFiles;
}
