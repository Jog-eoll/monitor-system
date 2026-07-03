package com.gateway.device.protocol.model;

/**
 * 批量任务状态。
 */
public enum BatchTaskStatus {

    /**
     * 等待执行
     */
    PENDING,

    /**
     * 执行中
     */
    RUNNING,

    /**
     * 全部成功
     */
    SUCCESS,

    /**
     * 部分成功
     */
    PARTIAL_SUCCESS,

    /**
     * 全部失败
     */
    FAILED,

    /**
     * 整体超时
     */
    TIMEOUT
}
