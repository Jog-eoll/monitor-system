package com.gateway.device.protocol.common.capability;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.params.*;

/**
 * 通用设备能力（协议无关）—— 适用于所有厂商协议的基础操作。
 *
 * <p>新增协议时，优先从此枚举中选择能力；协议独有的格式操作应在协议私有常量类中定义。</p>
 */
public final class CommonDeviceCapability {
    /**
     * 获取设备信息
     */
    public static final DeviceCapability<EmptyParams> DEVICE_INFO_GET
            = DeviceCapability.of("DEVICE_INFO_GET");
    // ── 图片 ──
    public static final DeviceCapability<ListQueryParams> IMAGE_LIST_QUERY
            = DeviceCapability.of("IMAGE_LIST_QUERY", ListQueryParams.class);
    public static final DeviceCapability<MediaUploadParams> IMAGE_UPLOAD
            = DeviceCapability.of("IMAGE_UPLOAD", MediaUploadParams.class);
    public static final DeviceCapability<FileDownloadParams> IMAGE_DOWNLOAD
            = DeviceCapability.of("IMAGE_DOWNLOAD", FileDownloadParams.class);
    public static final DeviceCapability<FileDeleteParams> IMAGE_DELETE
            = DeviceCapability.of("IMAGE_DELETE", FileDeleteParams.class);
    // ── 文字 ──
    public static final DeviceCapability<ListQueryParams> TEXT_LIST_QUERY
            = DeviceCapability.of("TEXT_LIST_QUERY", ListQueryParams.class);
    public static final DeviceCapability<TextUploadParams> TEXT_UPLOAD
            = DeviceCapability.of("TEXT_UPLOAD", TextUploadParams.class);
    public static final DeviceCapability<FileDownloadParams> TEXT_DOWNLOAD
            = DeviceCapability.of("TEXT_DOWNLOAD", FileDownloadParams.class);
    public static final DeviceCapability<FileDeleteParams> TEXT_DELETE
            = DeviceCapability.of("TEXT_DELETE", FileDeleteParams.class);
    // ── 视频 ──
    public static final DeviceCapability<ListQueryParams> VIDEO_LIST_QUERY
            = DeviceCapability.of("VIDEO_LIST_QUERY", ListQueryParams.class);
    public static final DeviceCapability<MediaUploadParams> VIDEO_UPLOAD
            = DeviceCapability.of("VIDEO_UPLOAD", MediaUploadParams.class);
    public static final DeviceCapability<FileDownloadParams> VIDEO_DOWNLOAD
            = DeviceCapability.of("VIDEO_DOWNLOAD", FileDownloadParams.class);
    public static final DeviceCapability<FileDeleteParams> VIDEO_DELETE
            = DeviceCapability.of("VIDEO_DELETE", FileDeleteParams.class);
    /**
     * 混合媒体上传（图片+视频混合，独立容器独立 zOrder）
     */
    public static final DeviceCapability<MediaMultiUploadParams> MEDIA_MULTI_UPLOAD
            = DeviceCapability.of("MEDIA_MULTI_UPLOAD", MediaMultiUploadParams.class);
    /**
     * 清除终端全部媒体文件（不可逆）
     */
    public static final DeviceCapability<EmptyParams> MEDIA_CLEAR
            = DeviceCapability.of("MEDIA_CLEAR");
    /**
     * 按名称删除指定媒体节目
     */
    public static final DeviceCapability<FileDeleteParams> MEDIA_DELETE
            = DeviceCapability.of("MEDIA_DELETE", FileDeleteParams.class);
    // ── 播放列表操作 ──
    /**
     * 获取播放列表
     */
    public static final DeviceCapability<PlaylistGetParams> PLAYLIST_GET
            = DeviceCapability.of("PLAYLIST_GET", PlaylistGetParams.class);
    /**
     * 设定播放列表
     */
    public static final DeviceCapability<PlaylistSetParams> PLAYLIST_SET
            = DeviceCapability.of("PLAYLIST_SET", PlaylistSetParams.class);
    /**
     * 清理播放列表
     */
    public static final DeviceCapability<EmptyParams> PLAYLIST_CLEAR
            = DeviceCapability.of("PLAYLIST_CLEAR");
    // ── 字体 ──
    /**
     * 查询设备当前字体列表。
     */
    public static final DeviceCapability<EmptyParams> FONTS_GET
            = DeviceCapability.of("FONTS_GET");
    /**
     * 字体同步 —— 同步指定字体到设备
     */
    public static final DeviceCapability<FontSyncParams> FONTS_SYNC
            = DeviceCapability.of("FONTS_SYNC", FontSyncParams.class);
    /**
     * 清除设备全部字体（不可逆）
     */
    public static final DeviceCapability<EmptyParams> FONTS_CLEAR
            = DeviceCapability.of("FONTS_CLEAR");
    // ── 音量 ──
    /**
     * 查询音量
     */
    public static final DeviceCapability<EmptyParams> VOLUME_GET
            = DeviceCapability.of("VOLUME_GET");
    /**
     * 设置音量
     */
    public static final DeviceCapability<VolumeSetParams> VOLUME_SET
            = DeviceCapability.of("VOLUME_SET", VolumeSetParams.class);
    // ── 时间 ──
    /**
     * 时间同步
     */
    public static final DeviceCapability<TimeSyncParams> TIME_SYNC
            = DeviceCapability.of("TIME_SYNC", TimeSyncParams.class);
    /**
     * NTP 服务器配置
     */
    public static final DeviceCapability<NtpSetParams> NTP_SET
            = DeviceCapability.of("NTP_SET", NtpSetParams.class);
    /**
     * 查询 NTP 配置
     */
    public static final DeviceCapability<EmptyParams> NTP_GET
            = DeviceCapability.of("NTP_GET");
    // ── 网络配置 ──
    /**
     * 修改设备网络配置（IP/掩码/网关/DNS）
     */
    public static final DeviceCapability<IpConfigParams> DEVICE_NETWORK_IP_SET
            = DeviceCapability.of("DEVICE_IP_SET", IpConfigParams.class);
    /**
     * AP 热点开关
     */
    public static final DeviceCapability<ApNetworkSwitchParams> DEVICE_NETWORK_AP_SWITCH
            = DeviceCapability.of("DEVICE_NETWORK_AP_SWITCH", ApNetworkSwitchParams.class);
    // ── 显示屏 ──
    /**
     * LED 点阵配屏（设置接收卡带载宽高）
     */
    public static final DeviceCapability<ScreenAttributeParams> SCREEN_ATTRIBUTE_SET
            = DeviceCapability.of("SCREEN_ATTRIBUTE_SET", ScreenAttributeParams.class);
    /**
     * 亮度调节
     */
    public static final DeviceCapability<BrightnessSetParams> BRIGHTNESS_SET
            = DeviceCapability.of("BRIGHTNESS_SET", BrightnessSetParams.class);
    // ── 屏控操作 ──
    /**
     * 重启
     */
    public static final DeviceCapability<EmptyParams> POWER_CONTROL_REBOOT
            = DeviceCapability.of("POWER_CONTROL_REBOOT");
    /**
     * 休眠
     */
    public static final DeviceCapability<EmptyParams> POWER_CONTROL_SLEEP
            = DeviceCapability.of("POWER_CONTROL_SLEEP");
    /**
     * 唤醒
     */
    public static final DeviceCapability<EmptyParams> POWER_CONTROL_WAKEUP
            = DeviceCapability.of("POWER_CONTROL_WAKEUP");
    /**
     * 黑屏
     */
    public static final DeviceCapability<ScreenBlackoutParams> SCREEN_BLACKOUT
            = DeviceCapability.of("SCREEN_BLACKOUT", ScreenBlackoutParams.class);

    private CommonDeviceCapability() {
    }
}
