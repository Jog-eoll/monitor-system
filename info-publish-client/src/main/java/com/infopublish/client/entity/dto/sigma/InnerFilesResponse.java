package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Sigma 获取内部文件清单响应
 * <p>
 * 对应 Sigma 对接文档 Section 8.3。
 * </p>
 */
@Data
public class InnerFilesResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    /** 待播放列表 ID */
    private String playlistId;

    /** 待播放列表版本 */
    private Integer playlistVersion;

    /** 内部文件列表 */
    private List<InnerFile> innerFiles;
}
