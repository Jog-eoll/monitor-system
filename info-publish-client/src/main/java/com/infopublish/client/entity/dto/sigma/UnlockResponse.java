package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * Sigma 解锁待播放列表响应
 */
@Data
public class UnlockResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    /** 是否已解锁 */
    private boolean unlocked;
}
