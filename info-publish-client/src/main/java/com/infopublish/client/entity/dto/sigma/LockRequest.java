package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * Sigma 锁定待播放列表请求
 */
@Data
public class LockRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求 ID */
    private String requestId;

    /** 调用方 ID，固定建议 secure-publish-client */
    private String clientId;

    /** 客户端看到的待播放列表版本 */
    private Integer playlistVersion;

    /** 锁超时（毫秒），默认 300000 */
    private Integer lockTimeoutMs;
}
