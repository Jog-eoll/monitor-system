package com.infopublish.client.enums;

/**
 * 预检查失败阶段枚举
 * <p>
 * 标识 precheck 流程中具体在哪个阶段失败，
 * 对应 Sigma 对接文档 Section 10.3。
 * </p>
 */
public enum CheckStage {

    /** UKey、认证、进程、网关等基础检查 */
    BASIC_CHECK,

    /** 获取当前待播放列表 */
    CURRENT_PLAYLIST,

    /** 锁定待播放列表 */
    LOCK,

    /** 获取内部文件清单 */
    LIST_INNER_FILES,

    /** 下载内部文件 */
    DOWNLOAD,

    /** 文件校验 */
    FILE_CHECK,

    /** 解锁待播放列表 */
    UNLOCK;
}
