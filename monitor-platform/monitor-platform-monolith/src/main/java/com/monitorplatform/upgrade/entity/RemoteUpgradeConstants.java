package com.monitorplatform.upgrade.entity;

/**
 * 远程升级常量。
 */
public final class RemoteUpgradeConstants {

    private RemoteUpgradeConstants() {
    }

    public static final String PACKAGE_STATUS_ACTIVE = "ACTIVE";

    public static final String TASK_STATUS_CREATED = "CREATED";
    public static final String TASK_STATUS_RUNNING = "RUNNING";
    public static final String TASK_STATUS_SUCCESS = "SUCCESS";
    public static final String TASK_STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    public static final String TASK_STATUS_FAILED = "FAILED";

    public static final String DEVICE_STATUS_CREATED = "CREATED";
    public static final String DEVICE_STATUS_PUBLISHED = "PUBLISHED";
    public static final String DEVICE_STATUS_PROCESSING = "PROCESSING";
    public static final String DEVICE_STATUS_SUCCESS = "SUCCESS";
    public static final String DEVICE_STATUS_FAILED = "FAILED";
    public static final String DEVICE_STATUS_TIMEOUT = "TIMEOUT";

    public static final String COMMAND_REMOTE_UPGRADE = "REMOTE_UPGRADE";
}
