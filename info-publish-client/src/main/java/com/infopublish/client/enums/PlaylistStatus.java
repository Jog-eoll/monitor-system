package com.infopublish.client.enums;

/**
 * 待播放列表状态枚举
 * <p>
 * 对应 Sigma 对接文档 Section 10.1。
 * </p>
 */
public enum PlaylistStatus {

    /** 待发布，允许预检查 */
    READY,

    /** 已锁定 */
    LOCKED,

    /** 正在发布 */
    PUBLISHING,

    /** 发布完成 */
    DONE,

    /** 发布失败 */
    FAILED;
}
