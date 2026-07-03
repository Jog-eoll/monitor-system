package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * Sigma 锁定待播放列表响应
 */
@Data
public class LockResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    /** 待播放列表 ID */
    private String playlistId;

    /** 锁 ID */
    private String lockId;

    /** 锁过期时间 */
    private String expireAt;
}
