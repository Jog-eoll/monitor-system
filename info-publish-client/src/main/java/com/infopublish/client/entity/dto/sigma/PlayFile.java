package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * Sigma 播放文件项
 */
@Data
public class PlayFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 播放文件 ID */
    private String playFileId;

    /** 播放文件名称 */
    private String fileName;

    /** 是否可导出内部文件 */
    private boolean canExportInnerFiles;
}
