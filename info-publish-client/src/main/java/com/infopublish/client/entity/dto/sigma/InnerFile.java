package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * Sigma 内部文件项
 */
@Data
public class InnerFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内部文件 ID */
    private String innerFileId;

    /** 所属播放文件 ID */
    private String playFileId;

    /** 内部文件名 */
    private String fileName;

    /** 播放文件内路径 */
    private String entryPath;

    /** MIME 类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private long size;

    /** 是否需要发送前校验 */
    private boolean securityCheckRequired;

    /** 校验策略，securityCheckRequired=true 时必填 */
    private String securityCheckProfile;
}
