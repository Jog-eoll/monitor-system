package com.gateway.device.protocol.common;

/**
 * 网关超时常量 —— 覆盖 HTTP、TCP、设备发现、设备操作、任务管理等场景。
 *
 * <p>所有超时值集中管理，避免各模块独立硬编码。单位在后缀中标注：{@code _MS} 毫秒、{@code _SEC} 秒、{@code _MIN} 分。</p>
 *
 * @see com.gateway.device.protocol.common.discovery.DiscoveryConst
 */
public final class GatewayTimeoutConstants {
    // ── TCP 传输 ──

    /**
     * TCP 连接超时 ms
     */
    public static final int TCP_CONNECT_TIMEOUT_MS = 10_000;
    /**
     * TCP 请求默认超时 ms
     */
    public static final int TCP_REQUEST_TIMEOUT_MS = 10_000;

    // ── Channel 生命周期 ──

    /**
     * Channel 空闲超时秒
     */
    public static final int CHANNEL_IDLE_TIMEOUT_SEC = 300;
    /**
     * 心跳间隔秒
     */
    public static final int HEARTBEAT_INTERVAL_SEC = 30;
    /**
     * 重连退避初始间隔秒
     */
    public static final int RECONNECT_BASE_SEC = 1;
    /**
     * 重连退避最大间隔秒
     */
    public static final int RECONNECT_MAX_SEC = 30;

    // ── 设备发现 ──

    /**
     * 设备发现默认超时 ms
     */
    public static final int DISCOVERY_DEFAULT_TIMEOUT_MS = 10_000;
    /**
     * 设备发现默认并发数
     */
    public static final int DISCOVERY_DEFAULT_CONCURRENCY = 30;

    // ── 设备登录/登出 ──

    /**
     * 设备登录超时 ms
     */
    public static final int DEVICE_LOGIN_TIMEOUT_MS = 15_000;
    /**
     * 设备登出超时 ms
     */
    public static final int DEVICE_LOGOUT_TIMEOUT_MS = 5_000;

    // ── 设备操作（通用） ──

    /**
     * 设备操作默认超时 ms
     */
    public static final int DEVICE_OPERATION_DEFAULT_MS = 10_000;
    /**
     * 设备操作快速超时 ms（JetFileII 简单命令）
     */
    public static final int DEVICE_OPERATION_QUICK_MS = 10_000;
    /**
     * 设备操作无回复超时 ms（JetFileII fire-and-forget）
     */
    public static final int DEVICE_OPERATION_NO_REPLY_MS = 2_000;
    // ── 设备操作（长耗时） ──

    /**
     * 登录冷却时间秒
     */
    public static final int DEVICE_LOGIN_COOLDOWN_SEC = 30;
    /**
     * 字体同步超时 ms
     */
    public static final int DEVICE_FONT_SYNC_MS = 120_000;
    /**
     * 批量节目下发超时 ms
     */
    public static final int DEVICE_BATCH_PROGRAM_MS = 180_000;

    // ── 任务管理 ──

    /**
     * 任务超时标记阈值秒
     */
    public static final int TASK_TIMEOUT_MARK_SEC = 240;
    /**
     * 已完成任务清理阈值分
     */
    public static final int TASK_EXPIRY_CLEANUP_MIN = 30;

    private GatewayTimeoutConstants() {
    }
}
