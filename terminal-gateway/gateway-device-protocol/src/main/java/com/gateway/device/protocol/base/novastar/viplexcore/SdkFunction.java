package com.gateway.device.protocol.base.novastar.viplexcore;

/**
 * ViplexCore SDK 函数枚举 —— 命名直接映射 JNA 接口方法名。
 *
 * <p>{@code nvGetDisplayInfoAsync} → {@link #NV_GET_DISPLAY_INFO_ASYNC}
 * <br>通过 {@link ViplexCoreChannel#execute} 调用，由 transport 层实现映射到 JNA。</p>
 */
public enum SdkFunction {

    // ═══ 设备信息 ═══
    NV_GET_FIRMWARE_INFOS_ASYNC,
    NV_GET_DISPLAY_INFO_ASYNC,
    NV_GET_CONFIGURATION_ASYNC,
    NV_GET_PRODUCT_INFO_ASYNC,

    // ═══ 音量 ═══
    NV_GET_VOLUME_ASYNC,
    NV_SET_VOLUME_ASYNC,

    // ═══ 亮度 ═══
    NV_GET_SCREEN_BRIGHTNESS_ASYNC,
    NV_SET_SCREEN_BRIGHTNESS_ASYNC,

    // ═══ 电源 ═══
    NV_SET_SCREEN_POWER_STATE_ASYNC,
    NV_GET_SCREEN_POWER_STATE_ASYNC,

    // ═══ 时间 ═══
    NV_CALIBRATE_TIME_ASYNC,

    // ═══ NTP ═══
    NV_SET_NET_TIMING_INFO_ASYNC,

    // ═══ 网络 ═══
    NV_GET_ETHERNET_INFO_ASYNC,
    NV_SET_ETHERNET_INFO_ASYNC,
    NV_GET_AP_NETWORK_OPEN_STATUS_ASYNC,
    NV_SET_AP_NETWORK_OPEN_STATUS_ASYNC,

    // ═══ 显示 ═══
    NV_SET_CUSTOM_RESOLUTION_ASYNC,
    NV_GET_CURRENT_RESOLUTION_ASYNC,
    NV_GET_SUPPORTED_RESOLUTION_ASYNC,
    NV_SET_SCREEN_ATTRIBUTE_ASYNC,

    // ═══ 节目管线 ═══
    NV_CREATE_PROGRAM_ASYNC,
    NV_SET_PAGE_PROGRAM_ASYNC,
    NV_SET_PAGE_PROGRAMS_ASYNC,
    NV_MAKE_PROGRAM_ASYNC,
    NV_START_TRANSFER_PROGRAM_ASYNC,
    NV_GET_PROGRAM_INFO_ASYNC,
    NV_DELETE_PLAYLIST_ASYNC,
    NV_START_PLAY_ASYNC,

    // ═══ 媒体文件 ═══
    NV_CLEAR_ALL_MEDIAS_ASYNC,
    NV_DOWNLOAD_FILES_ASYNC,
    NV_QUERY_FILE_BY_TYPE_ASYNC,
    NV_GET_FILE_MD5_ASYNC,

    // ═══ 字体 ═══
    NV_GET_TERMINAL_FONT_ASYNC,
    NV_DELETE_FONT_ASYNC,
    NV_UPDATE_FONT_ASYNC,

    // ═══ 重启 ═══
    NV_SET_REBOOT_TASK_ASYNC,

    // ═══ 搜索 ═══
    NV_SEARCH_TERMINAL_ASYNC
}
