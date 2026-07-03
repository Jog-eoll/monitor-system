package com.gateway.device.transport.sdk.novastar.viplexcore;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

import java.io.File;
import java.util.Locale;

/**
 * ViplexCore 原生 SDK JNA 接口映射（ViplexCore 3.6.3.0101）。
 *
 * <p>通过 {@link #load(String)} 加载，自动按平台解析库名并注册依赖搜索路径。</p>
 *
 * <h3>目录约定</h3>
 * SDK 根目录下需包含 {@code bin/} 子目录：
 * <ul>
 *   <li>Windows: {@code {sdkDir}/bin/viplexcore.dll}</li>
 *   <li>Linux:   {@code {sdkDir}/bin/libviplexcore.so}</li>
 * </ul>
 */
public interface ViplexCoreLibrary extends Library {

    /**
     * 加载 SDK 库。
     *
     * @param sdkDir SDK 根目录（包含 bin/ 子目录的路径）
     */
    public static ViplexCoreLibrary load(String sdkDir) {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win");

        String binDir = sdkDir + File.separator + "bin";
        String libName = "viplexcore";
        String libFile = binDir + File.separator
                + (isWindows ? "viplexcore.dll" : "libviplexcore.so");

        // 将 bin 目录注册到 JNA 搜索路径，确保依赖库（Qt5 等）可被解析
        NativeLibrary.addSearchPath(libName, binDir);
        // 同时追加到 java.library.path 属性（用于 LoadLibrary 间接依赖查找）
        appendLibraryPath(binDir);

        return Native.load(libFile, ViplexCoreLibrary.class);
    }

    // ════════════════════════════════════════════════════════════
    // 生命周期
    // ════════════════════════════════════════════════════════════

    /**
     * 将目录追加到 java.library.path 系统属性（JNA 依赖解析辅助）
     */
    static void appendLibraryPath(String dir) {
        String current = System.getProperty("java.library.path", "");
        if (current.contains(dir)) return;
        System.setProperty("java.library.path",
                current + File.pathSeparator + dir);
        // 同步设置 jna.library.path（JNA 5.x 优先读取此属性）
        String jnaCurrent = System.getProperty("jna.library.path", "");
        if (!jnaCurrent.contains(dir)) {
            System.setProperty("jna.library.path",
                    jnaCurrent.isEmpty() ? dir : jnaCurrent + File.pathSeparator + dir);
        }
    }

    /**
     * 设置 SDK 开发语言标识
     */
    void nvSetDevLang(String devLang);

    // ════════════════════════════════════════════════════════════
    // 设备发现
    // ════════════════════════════════════════════════════════════

    /**
     * 初始化 SDK，返回 0 成功
     */
    int nvInit(String sdkRootDir, String credentials);

    /**
     * 广播搜索终端（UDP），4s 内逐个回调发现的设备
     */
    void nvSearchTerminalAsync(ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 登录认证
    // ════════════════════════════════════════════════════════════

    /**
     * 从数据库获取所有已知终端
     */
    void nvFindAllTerminalsAsync(ViplexCallback callback);

    /**
     * 登录终端（合并 TCP 连接）
     */
    void nvLoginAsync(String data, ViplexCallback callback);

    /**
     * 退出登录
     */
    void nvLogoutAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 设备信息
    // ════════════════════════════════════════════════════════════

    /**
     * 修改密码
     */
    void nvChangePassWordAsync(String data, ViplexCallback callback);

    /**
     * 获取产品信息
     */
    void nvGetProductInfoAsync(String data, ViplexCallback callback);

    /**
     * 获取固件版本信息（全平台通用）
     */
    void nvGetFirmwareInfosAsync(String data, ViplexCallback callback);

    /**
     * 获取已安装软件版本
     */
    void nvGetInstalledPackageVersionsAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 屏体电源
    // ════════════════════════════════════════════════════════════

    /**
     * 获取设备配置信息
     */
    void nvGetconfigurationAsync(String data, ViplexCallback callback);

    /**
     * 设置屏体电源状态：open 开屏 / close 关屏（黑屏）
     */
    void nvSetScreenPowerStateAsync(String data, ViplexCallback callback);

    /**
     * 获取屏体电源状态
     */
    void nvGetScreenPowerStateAsync(String data, ViplexCallback callback);

    /**
     * 设置屏体电源模式
     */
    void nvSetScreenPowerModeAsync(String data, ViplexCallback callback);

    /**
     * 获取屏体电源模式
     */
    void nvGetScreenPowerModeAsync(String data, ViplexCallback callback);

    /**
     * 获取屏体电源定时策略
     */
    void nvGetScreenPowerPolicyAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 多功能卡电源
    // ════════════════════════════════════════════════════════════

    /**
     * 设置屏体电源定时策略
     */
    void nvSetScreenPowerPolicyAsync(String data, ViplexCallback callback);

    void nvSetTimingPowerSwitchStatusAsync(String data, ViplexCallback callback);

    void nvGetTimingPowerSwitchStatusAsync(String data, ViplexCallback callback);

    void nvGetRealtimePowerSwitchStatusAsync(String data, ViplexCallback callback);

    void nvSetManualPowerSwitchStatusAsync(String data, ViplexCallback callback);

    void nvGetManualPowerSwitchStatusAsync(String data, ViplexCallback callback);

    void nvSetPowerInfoManualAsync(String data, ViplexCallback callback);

    void nvSetPowerInfoPolicyAsync(String data, ViplexCallback callback);

    void nvGetPowerInfoPolicyAsync(String data, ViplexCallback callback);

    void nvGetPowerInfoStatusAsync(String data, ViplexCallback callback);

    void nvSetPowerModeAsync(String data, ViplexCallback callback);

    void nvGetPowerModeAsync(String data, ViplexCallback callback);

    void nvSetRelayPowerManualAsync(String data, ViplexCallback callback);

    void nvSetRelayPowerPolicyAsync(String data, ViplexCallback callback);

    void nvGetRelayPowerPolicyAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 亮度调节
    // ════════════════════════════════════════════════════════════

    void nvGetRelayPowerStatusAsync(String data, ViplexCallback callback);

    /**
     * 获取屏幕亮度
     */
    void nvGetScreenBrightnessAsync(String data, ViplexCallback callback);

    /**
     * 设置屏幕亮度
     */
    void nvSetScreenBrightnessAsync(String data, ViplexCallback callback);

    void nvSetBrightnessAdjustModeAsync(String data, ViplexCallback callback);

    void nvGetBrightnessAdjustModeAsync(String data, ViplexCallback callback);

    void nvGetBrightnessPolicyAsync(String data, ViplexCallback callback);

    void nvSetBrightnessPolicyAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 音量调节
    // ════════════════════════════════════════════════════════════

    void nvGetEnvironmentBrightnessAsync(String data, ViplexCallback callback);

    /**
     * 获取音量
     */
    void nvGetVolumeAsync(String data, ViplexCallback callback);

    /**
     * 设置音量
     */
    void nvSetVolumeAsync(String data, ViplexCallback callback);

    void nvSetTimingVolumeAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 色温调节
    // ════════════════════════════════════════════════════════════

    void nvGetTimingVolumeAsync(String data, ViplexCallback callback);

    void nvGetColorTemperatureAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 视频源控制
    // ════════════════════════════════════════════════════════════

    void nvSetColorTemperatureAsync(String data, ViplexCallback callback);

    void nvGetVideoControlInfoAsync(String data, ViplexCallback callback);

    void nvSetVideoControlInfoAsync(String data, ViplexCallback callback);

    void nvSetVideoEDIDAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 节目管理
    // ════════════════════════════════════════════════════════════

    void nvGetVideoEDIDAsync(String data, ViplexCallback callback);

    /**
     * 获取节目列表
     */
    void nvGetProgramInfoAsync(String data, ViplexCallback callback);

    void nvDeletePlayListAsync(String data, ViplexCallback callback);

    void nvDeleteProgramAsync(String data, ViplexCallback callback);

    void nvStartPlayAsync(String data, ViplexCallback callback);

    void nvPausePlayAsync(String data, ViplexCallback callback);

    void nvResumePlayAsync(String data, ViplexCallback callback);

    void nvStopPlayAsync(String data, ViplexCallback callback);

    void nvCreateProgramAsync(String data, ViplexCallback callback);

    void nvSetPageProgramAsync(String data, ViplexCallback callback);

    void nvMakeProgramAsync(String data, ViplexCallback callback);

    void nvSetPageProgramsAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 字体管理
    // ════════════════════════════════════════════════════════════

    void nvStartTransferProgramAsync(String data, ViplexCallback callback);

    void nvGetTerminalFontAsync(String data, ViplexCallback callback);

    void nvDeleteFontAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 显示设置
    // ════════════════════════════════════════════════════════════

    void nvUpdateFontAsync(String data, ViplexCallback callback);

    void nvGetRotationAsync(String data, ViplexCallback callback);

    void nvSetRotationAsync(String data, ViplexCallback callback);

    void nvGetDisplayInfoAsync(String data, ViplexCallback callback);

    void nvGetCurrentResolutionAsync(String data, ViplexCallback callback);

    void nvGetSupportedResolutionAsync(String data, ViplexCallback callback);

    void nvSetCustomResolutionAsync(String data, ViplexCallback callback);

    void nvGetScreenAttributeAsync(String data, ViplexCallback callback);

    void nvSetScreenAttributeAsync(String data, ViplexCallback callback);

    void nvGetHdmiOutputStatusAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 网络配置
    // ════════════════════════════════════════════════════════════

    void nvSetHdmiOutputStatusAsync(String data, ViplexCallback callback);

    void nvGetWifiListAsync(String data, ViplexCallback callback);

    void nvConnectWifiNetworkAsync(String data, ViplexCallback callback);

    void nvGetWifiCurrentStatusAsync(String data, ViplexCallback callback);

    void nvDisconnectWifiNetworkAsync(String data, ViplexCallback callback);

    void nvGetWifiEnabledAsync(String data, ViplexCallback callback);

    void nvSetWifiEnabledAsync(String data, ViplexCallback callback);

    void nvSendForgetWifiCommandAsync(String data, ViplexCallback callback);

    void nvGetEthernetInfoAsync(String data, ViplexCallback callback);

    void nvSetEthernetInfoAsync(String data, ViplexCallback callback);

    /**
     * 获取 AP 热点开启状态
     */
    void nvGetAPNetworkOpenStatusAsync(String data, ViplexCallback callback);

    /**
     * 设置 AP 热点开启状态（开/关）
     */
    void nvSetAPNetworkOpenStatusAsync(String data, ViplexCallback callback);

    void nvGetMobileNetworkAsync(String data, ViplexCallback callback);

    void nvSetMobileNetworkAsync(String data, ViplexCallback callback);

    void nvIsMobileModuleExistedAsync(String data, ViplexCallback callback);

    void nvGetFlightModeAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // VPN连接
    // ════════════════════════════════════════════════════════════

    void nvSetFlightModeAsync(String data, ViplexCallback callback);

    void nvGetVPNConnectInfoAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 系统维护
    // ════════════════════════════════════════════════════════════

    void nvSetVPNConnectInfoAsync(String data, ViplexCallback callback);

    /**
     * 设置重启任务（立即重启）。
     *
     * <p>参数: {@code {"sn":"...","taskInfo":{"type":"REBOOT","source":{"type":0,"platform":2},"executionType":"IMMEDIATELY","reason":"..."}}}</p>
     */
    void nvSetReBootTaskAsync(String data, ViplexCallback callback);

    /**
     * 恢复出厂设置
     */
    void nvSetReBootWipeUserDataAsync(String data, ViplexCallback callback);

    void nvClearAllMediasAsync(String data, ViplexCallback callback);

    void nvDeleteFileAsync(String data, ViplexCallback callback);

    void nvDownLoadFilesAsync(String data, ViplexCallback callback);

    void nvQueryFileByTypeAsync(String data, ViplexCallback callback);

    void nvIsFileExistedAsync(String data, ViplexCallback callback);

    void nvGetFileMD5Async(String data, ViplexCallback callback);

    void nvSetSyncPlayAsync(String data, ViplexCallback callback);

    void nvGetSyncPlayAsync(String data, ViplexCallback callback);

    void nvGetOTGUSBModeAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 时间管理
    // ════════════════════════════════════════════════════════════

    void nvSetOTGUSBModeAsync(String data, ViplexCallback callback);

    /**
     * 校准时间（NTP 授时）
     */
    void nvCalibrateTimeAsync(String data, ViplexCallback callback);

    /**
     * 获取当前时间与时区
     */
    void nvGetCurrentTimeAndZoneAsync(String data, ViplexCallback callback);

    /**
     * 设置终端时间与时区（新协议 0x99）。
     *
     * <p>参数: {@code {"sn":"...","taskInfo":{"data":{"utcTimeMillis":...,"timeZone":"Asia/Shanghai","gmt":"GMT+08:00"}}}}</p>
     */
    void nvSetTimeAndZoneAsync(String data, ViplexCallback callback);

    void nvGetNetTimingInfoAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 升级管理
    // ════════════════════════════════════════════════════════════

    void nvSetNetTimingInfoAsync(String data, ViplexCallback callback);

    void nvQueryUpdateFileByTypeAsync(String data, ViplexCallback callback);

    void nvGetOnlineUpgradeFileAsync(String data, ViplexCallback callback);

    void nvDownloadUpgradeFileAsync(String data, ViplexCallback callback);

    void nvStopDownloadUpgradeFileAsync(String data, ViplexCallback callback);

    void nvUpdateAppAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // APP管理
    // ════════════════════════════════════════════════════════════

    void nvUpdateOSAsync(String data, ViplexCallback callback);

    void nvGetInstalledPackageInfoAsync(String data, ViplexCallback callback);

    void nvGetRunningPackageInfoAsync(String data, ViplexCallback callback);

    void nvForceStopAppAsync(String data, ViplexCallback callback);

    void nvUninstallPackageAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 传感器
    // ════════════════════════════════════════════════════════════

    void nvStartUploadApkAsync(String data, ViplexCallback callback);

    void nvGetSupportSensorInfoAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 监控诊断
    // ════════════════════════════════════════════════════════════

    void nvSetSupportSensorInfoAsync(String data, ViplexCallback callback);

    void nvGetCPUUsageAsync(String data, ViplexCallback callback);

    void nvGetCPUTempAsync(String data, ViplexCallback callback);

    void nvGetAvailableMemoryAsync(String data, ViplexCallback callback);

    void nvGetAvailableStorageDataAsync(String data, ViplexCallback callback);

    void nvGetSendCardMonitorInfoAsync(String data, ViplexCallback callback);

    void nvGetReceiverCountAndInfoAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 多屏拼接
    // ════════════════════════════════════════════════════════════

    void nvGetMonitorInfoByReceiverIndexAsync(String data, ViplexCallback callback);

    void nvGetSpliceInfoAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 用户信息
    // ════════════════════════════════════════════════════════════

    void nvSetSpliceInfoAsync(String data, ViplexCallback callback);

    void nvGetUserInfoAsync(String data, ViplexCallback callback);

    // ════════════════════════════════════════════════════════════
    // 工厂方法
    // ════════════════════════════════════════════════════════════

    void nvSetUserInfoAsync(String data, ViplexCallback callback);

    /**
     * SDK 异步回调：code=0 成功，data 为 JSON 字符串
     */
    interface ViplexCallback extends Callback {
        void dataCallBack(int code, String data);
    }
}
