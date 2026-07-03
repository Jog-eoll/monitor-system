package com.gateway.device.protocol.common.capability.expand;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.params.EmptyParams;
import com.gateway.device.protocol.model.params.ResolutionSetParams;

/**
 * NovaStar ViplexCore SDK 协议能力全集（ViplexCore 3.6.3.0101）。
 * <p>
 * 本类仅定义 ViplexCore SDK 私有能力。</p>
 */
public final class NovaViplexCoreCapability {
    // ════════════════════════════════════════════════════════════
    // 设备信息
    // ════════════════════════════════════════════════════════════
    public static final DeviceCapability<EmptyParams> DEVICE_PRODUCT_INFO_GET
            = DeviceCapability.of("DEVICE_PRODUCT_INFO_GET");
    public static final DeviceCapability<EmptyParams> DEVICE_CONFIGURATION
            = DeviceCapability.of("DEVICE_CONFIGURATION");
    // ════════════════════════════════════════════════════════════
    // 显示设置
    // ════════════════════════════════════════════════════════════
    public static final DeviceCapability<EmptyParams> DISPLAY_INFO_GET
            = DeviceCapability.of("DISPLAY_INFO_GET");
    // ════════════════════════════════════════════════════════════
    // 显示设置
    // ════════════════════════════════════════════════════════════
    public static final DeviceCapability<ResolutionSetParams> DISPLAY_RESOLUTION_SET
            = DeviceCapability.of("DISPLAY_RESOLUTION_SET", ResolutionSetParams.class);

    private NovaViplexCoreCapability() {
    }

    // ════════════════════════════════════════════════════════════
    // 以下能力已定义但暂未启用（含已下线能力），保留供后续按需激活
    // 格式: 中文说明 — SDK 函数名
    // ════════════════════════════════════════════════════════════

    // ── 登录认证 ──
    // 退出登录 — nvLogoutAsync
    // 修改密码 — nvChangePassWordAsync

    // ── 设备信息 ──
    // 获取已安装软件版本 — nvGetInstalledPackageVersionsAsync

    // ── 屏体电源 ──
    // 获取屏体电源状态 — nvGetScreenPowerStateAsync
    // 获取屏体电源模式 — nvGetScreenPowerModeAsync
    // 设置屏体电源模式（手动/定时） — nvSetScreenPowerModeAsync
    // 获取屏体定时电源策略 — nvGetScreenPowerPolicyAsync
    // 设置屏体定时电源策略 — nvSetScreenPowerPolicyAsync

    // ── 多功能卡电源 ──
    // 获取定时电源开关状态 — nvGetTimingPowerSwitchStatusAsync
    // 设置定时电源开关状态 — nvSetTimingPowerSwitchStatusAsync
    // 获取手动电源开关状态 — nvGetManualPowerSwitchStatusAsync
    // 设置手动电源开关状态 — nvSetManualPowerSwitchStatusAsync
    // 获取实时电源开关状态 — nvGetRealtimePowerSwitchStatusAsync
    // 手动多功能卡电源控制 — nvSetPowerInfoManualAsync
    // 获取定时多功能卡电源任务 — nvGetPowerInfoPolicyAsync
    // 设置定时多功能卡电源任务 — nvSetPowerInfoPolicyAsync
    // 获取多功能卡电源状态 — nvGetPowerInfoStatusAsync
    // 获取终端电源模式 — nvGetPowerModeAsync
    // 设置终端电源模式 — nvSetPowerModeAsync
    // 本板电源手动控制 — nvSetRelayPowerManualAsync
    // 获取本板电源定时任务 — nvGetRelayPowerPolicyAsync
    // 设置本板电源定时任务 — nvSetRelayPowerPolicyAsync
    // 获取本板电源状态 — nvGetRelayPowerStatusAsync

    // ── 亮度调节 ──
    // 获取环境亮度 — nvGetEnvironmentBrightnessAsync
    // 获取亮度调节模式 — nvGetBrightnessAdjustModeAsync
    // 设置亮度调节模式（手动/自动/定时） — nvSetBrightnessAdjustModeAsync
    // 获取亮度调节策略 — nvGetBrightnessPolicyAsync
    // 设置亮度调节策略 — nvSetBrightnessPolicyAsync

    // ── 音量调节 ──
    // 获取定时音量 — nvGetTimingVolumeAsync
    // 设置定时音量 — nvSetTimingVolumeAsync

    // ── 色温调节 ──
    // 获取色温 — nvGetColorTemperatureAsync
    // 设置色温 — nvSetColorTemperatureAsync

    // ── 视频源控制 ──
    // 获取视频源配置 — nvGetVideoControlInfoAsync
    // 设置视频源配置（手动/定时） — nvSetVideoControlInfoAsync
    // 获取EDID — nvGetVideoEDIDAsync
    // 设置EDID — nvSetVideoEDIDAsync
    // 获取手动视频源状态(0x99) — nvGetVideoSourceManualFor0x99Async
    // 设置手动视频源状态(0x99) — nvSetVideoSourceManualFor0x99Async
    // 获取定时视频源状态(0x99) — nvGetVideoSourcePolicyFor0x99Async
    // 设置定时视频源状态(0x99) — nvSetVideoSourcePolicyFor0x99Async

    // ── 节目管理 ──
    // 创建节目 — nvCreateProgramAsync
    // 编辑节目页 — nvSetPageProgramAsync / nvSetPageProgramsAsync
    // 生成节目 — nvMakeProgramAsync
    // 删除节目 — nvDeleteProgramAsync
    // 发送节目到终端 — nvStartTransferProgramAsync
    // 开始播放 — nvStartPlayAsync
    // 暂停播放 — nvPausePlayAsync
    // 恢复播放 — nvResumePlayAsync
    // 停止播放 — nvStopPlayAsync
    // 创建插播节目 — nvCreateInterProgramAsync
    // 获取插播节目信息 — nvGetInterProgramInfoAsync
    // 停止插播节目 — nvStopInterProgramAsync

    // ── 字体管理 ──
    // [已激活] FONTS_SYNC — nvUpdateFontAsync（使用 CommonDeviceCapability.FONTS_SYNC）
    // [已激活] FONTS_GET — nvGetTerminalFontAsync（使用 CommonDeviceCapability.FONTS_GET）
    // 删除终端字体 — nvDeleteFontAsync（预留）

    // ── 显示设置 ──
    // 获取旋转角度 — nvGetRotationAsync
    // 设置旋转角度 — nvSetRotationAsync
    // 获取当前分辨率 — nvGetCurrentResolutionAsync
    // 获取支持分辨率列表 — nvGetSupportedResolutionAsync
    // 设置自定义分辨率 — nvSetCustomResolutionAsync
    // 获取HDMI输出状态 — nvGetHdmiOutputStatusAsync
    // 设置HDMI输出状态 — nvSetHdmiOutputStatusAsync

    // ── 网络配置 ──
    // 获取WiFi列表 — nvGetWifiListAsync
    // 连接WiFi — nvConnectWifiNetworkAsync
    // 断开WiFi — nvDisconnectWifiNetworkAsync
    // 获取WiFi当前状态 — nvGetWifiCurrentStatusAsync
    // 获取WiFi开关状态 — nvGetWifiEnabledAsync
    // 设置WiFi开关 — nvSetWifiEnabledAsync
    // 忘记WiFi — nvSendForgetWifiCommandAsync
    // 获取以太网信息 — nvGetEthernetInfoAsync
    // 设置以太网信息 — nvSetEthernetInfoAsync
    // 获取AP网络信息 — nvGetAPNetworkAsync
    // 设置AP网络信息 — nvSetAPNetworkAsync
    // [已激活] AP_NETWORK_SWITCH — nvGetAPNetworkOpenStatusAsync / nvSetAPNetworkOpenStatusAsync
    // 获取APN信息 — nvGetAPNInfoAsync
    // 设置APN信息 — nvSetAPNInfoAsync
    // 获取移动网络信息 — nvGetMobileNetworkAsync
    // 设置移动网络信息 — nvSetMobileNetworkAsync
    // 检测移动模块是否存在 — nvIsMobileModuleExistedAsync
    // 获取4G网络状态 — nvGet4GNetworkStatusAsync
    // 获取飞行模式 — nvGetFlightModeAsync
    // 设置飞行模式 — nvSetFlightModeAsync
    // WiFi/AP模式切换 — nvSetWifiApStationSwitchAsync
    // 获取网络模块信息 — nvGetModuleInfoAsync

    // ── VPN连接 ──
    // 获取VPN连接信息 — nvGetVPNConnectInfoAsync
    // 设置VPN连接信息 — nvSetVPNConnectInfoAsync

    // ── 系统维护 ──
    // 获取同步播放配置 — nvGetSyncPlayAsync
    // 设置同步播放开关 — nvSetSyncPlayAsync
    // 获取OTG USB状态 — nvGetOTGUSBModeAsync
    // 设置OTG USB模式 — nvSetOTGUSBModeAsync
    // 获取屏体设备信息 — nvGetScreenDeviceInfoAsync

    // ── 时间管理 ──
    // 获取当前时间 — nvGetCurrentTimeAndZoneAsync
    // 设置时间与时区 — nvSetTimeAndZoneAsync
    // 获取网络授时 — nvGetNetTimingInfoAsync
    // 设置网络授时 — nvSetNetTimingInfoAsync
    // 获取夏令时 — nvGetIsUseDayLightTimeAsync

    // ── 升级管理 ──
    // 搜索本地升级包 — nvQueryUpdateFileByTypeAsync
    // 获取线上升级包 — nvGetOnlineUpgradeFileAsync
    // 下载升级包 — nvDownloadUpgradeFileAsync
    // 停止下载升级包 — nvStopDownloadUpgradeFileAsync
    // 升级APP — nvUpdateAppAsync
    // 升级OS — nvUpdateOSAsync
    // 停止当前升级任务 — nvStopCurrentUpdateTaskAsync
    // 升级校验 — nvUpdateVerifyAsync

    // ── APP管理 ──
    // 获取已安装APP — nvGetInstalledPackageInfoAsync
    // 获取运行中APP — nvGetRunningPackageInfoAsync
    // 强制停止APP — nvForceStopAppAsync
    // 卸载APP — nvUninstallPackageAsync
    // 上传安装APK — nvStartUploadApkAsync / nvStartUploadApkCoreAsync
    // 运行APP — nvRunAppAsync

    // ── 传感器 ──
    // 获取传感器信息 — nvGetSupportSensorInfoAsync
    // 配置传感器连接选项 — nvSetSupportSensorInfoAsync

    // ── 监控诊断 ──
    // 获取指定接收卡监控 — nvGetMonitorInfoByReceiverIndexAsync
    // 获取箱体温度 — nvGetScreenUnitTempAsync

    // ── 日志管理 ──
    // 获取播放日志路径 — nvGetPlaylogPathAsync
    // 下载终端日志 — nvDownloadTerminalLogAsync
    // 下载播放日志 — nvDownloadTerminalPlayLogAsync
    // 下载全部日志 — nvDownloadTerminalAllLogAsync
    // 上传终端日志 — nvUploadTerminalLogAsync

    // ── 云平台 ──
    // 绑定播放器到云 — nvSetBindPlayerAsync / nvNewSetBindPlayerAsync
    // 获取云播放器列表 — nvGetCloudPlayerListAsync
    // 获取已绑定播放器 — nvGetBindPlayerAsync
    // VNNOX登录 — nvLoginVnnox
    // VNNOX注册 — nvRegisterVnnox

    // ── 多屏拼接 ──
    // 获取拼接参数 — nvGetSpliceInfoAsync
    // 设置拼接参数 — nvSetSpliceInfoAsync

    // ── 用户信息 ──
    // 获取显示屏用户信息 — nvGetUserInfoAsync
    // 设置显示屏用户信息 — nvSetUserInfoAsync

    // ── 播放控制 ──
    // 获取播放窗口位置 — nvGetPlayWindowSiteAsync
    // 设置播放窗口位置 — nvSetPlayWindowSiteAsync

    // ── 屏幕配置 ──
    // 获取屏体属性 — nvGetScreenAttributeAsync
    // 设置屏体属性 — nvSetScreenAttributeAsync
    // 发送接收卡配置文件 — nvSetRecvCardFileAsync

    // ── 其他 ──
    // RSA加密 — nvRsaEncodeAsync
    // RSA解密 — nvRsaDecodeAsync
    // 启动终端服务 — nvStartServiceAsync
    // 停止终端服务 — nvStopServiceAsync
    // 获取终端服务信息 — nvGetTerminalServiceAsync
    // 数据库迁移 — nvDataBaseMigrationAsync
    // 获取Lora信息 — nvGetLoraInfoAsync
}
