package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * Sigma 解锁待播放列表请求
 */
@Data
public class UnlockRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求 ID */
    private String requestId;

    /** 锁 ID */
    private String lockId;

    /** 解锁原因: DONE / FAILED / TIMEOUT */
    private String reason;
}
