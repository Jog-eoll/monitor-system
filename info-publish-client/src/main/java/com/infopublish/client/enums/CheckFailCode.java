package com.infopublish.client.enums;

/**
 * 预检查失败码枚举
 * <p>
 * 对应 Sigma 对接文档 Section 10.4。
 * </p>
 */
public enum CheckFailCode {

    /** UKey 未认证 */
    UKEY_NOT_AUTHENTICATED,

    /** 客户端未认证 */
    CLIENT_NOT_AUTHENTICATED,

    /** Sigma 进程未绑定 */
    SIGMA_PROCESS_NOT_BOUND,

    /** 网关链路不可用 */
    GATEWAY_NOT_READY,

    /** 待播放列表状态不允许发布 */
    PLAYLIST_NOT_READY,

    /** 待播放列表版本变化 */
    PLAYLIST_VERSION_CHANGED,

    /** 文件校验失败 */
    FILE_CHECK_FAILED,

    /** 文件上下文不匹配 */
    FILE_CONTEXT_MISMATCH,

    /** 本地校验策略拒绝 */
    CHECK_POLICY_REJECTED,

    /** 处理超时 */
    TIMEOUT;
}
