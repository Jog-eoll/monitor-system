package com.gateway.device.protocol.common.capability.expand;

/**
 * ColorLight 协议私有能力全集（PlayerSDK HTTP REST API）。
 * 本类仅定义 ColorLight 私有能力，不重复 Common 已定义的能力。</p>
 */
public final class ColorLightCapability {

    private ColorLightCapability() {
    }

    // ════════════════════════════════════════════════════════════
    // 以下能力已定义但暂未启用，保留供后续按需激活
    // 格式: 中文说明 — "/api/path" — ParamClass
    // ════════════════════════════════════════════════════════════

    // ── 电源 / 屏幕状态 ──
    // 获取设备截图 — "/api/screenshot"

    // ── 亮度 / 色温 / 色彩 ──
    // 查询亮度/色温 — "/api/brightnessandcolortemp.json"
    // 设置色温 (2000-10000) — "/api/colortemp" — ColorTempPayload
    // 查询亮度与色温 — "/api/brightnessandcolortemp.json"
    // 设置对比度/色调/饱和度 — "/api/screenconfig" — ScreenConfigPayload
    // 查询对比度/色调/饱和度 — "/api/screenconfig.json"
    // 查询所有亮度相关数据（含传感器状态） — "/api/allbrightnessinfo.json"
    // 设置低亮高灰（新版 A600/A800） — "/api/smart_sender"
    // 查询低亮高灰状态（新版） — "/api/smart_read_serial_info"
    // 配置自动亮度曲线（含 gamma） — "/api/brightcurve" — BrightCurvePayload
    // 查询自动亮度曲线 — "/api/brightcurve.json"

    // ── 屏幕方向 / 显示 ──
    // 设置屏幕旋转方向 — "/api/screen_orientation" — OrientationPayload
    // 查询屏幕旋转方向 — "/api/screen_orientation.json"
    // 查询屏幕开关状态 — "/api/screenstatus.json"
    // 配置输出分辨率 — "/api/dimension" — DimensionSetPayload
    // 设置帧率 (15-120) — "/api/fps" — FpsPayload
    // 查询帧率 — "/api/fps.json"
    // 设置输入模式 (hdmi/dvi) — "/api/inputmode" — InputModePayload
    // 查询输入模式 — "/api/inputmode.json"
    // 设置 HDMI 强制输出 — "/api/hdmi_status" — HdmiStatusPayload
    // 查询 HDMI 状态 — "/api/hdmi_status.json"
    // 设置音频输入源 — "/api/audiointput" — AudioInputPayload
    // 查询音频输入源 — "/api/audiointput.json"
    // 设置裁剪与缩放 — "/api/setcutandscale" — CutAndScalePayload
    // 查询裁剪与缩放 — "/api/setcutandscale.json"

    // ── 设备管理 ──
    // 查询电源状态（休眠/唤醒） — "/api/powerstatus.json"
    // 查询设备系统时间 — "/api/newrtc.json"
    // 配置设备名称与描述 — "/api/terminal" — TerminalPayload
    // 查询设备名称与描述 — "/api/terminal.json"
    // 配置语言与国家 — "/api/locale" — LocalePayload
    // 查询语言与国家 — "/api/locale.json"
    // 恢复出厂设置 — "/api/resetfact" — ResetFactoryPayload
    // 查询当前网络配置 — "/api/network.json"

    // ── 外设 / 继电器 ──
    // 设置继电器开关状态 — "/api/relay" — RelaySetPayload
    // 查询继电器状态 — "/api/relay.json"
    // 设置自动继电器 — "/api/autorelay" — AutoRelayPayload
    // 查询自动继电器配置 — "/api/autorelay.json"
    // 设置网口控制面积 — "/api/sendingcard" — SendingCardPayload
    // 查询网口控制面积 — "/api/sendingcard.json"

    // ── 媒体 / 节目控制 ──
    // [已弃用] 视频播放/暂停 — "/api/videocontrol" — 使用 "/api/videocommand" (VIDEO_COMMAND) 替代
    // 视频播放命令（快进/快退/跳转） — "/api/videocommand" — VideoCommandPayload
    // 视频节目进度跳转（毫秒） — "/api/program/seekto" — SeekToPayload
    // [已弃用] 快速发布单行文本节目 — "/api/program/singletext" — 使用 "/api/program/{0}.vsn" (MEDIA_UPLOAD) 替代
    // 清空节目缓存资源 — "/api/clrcache"
    // 清空局域网无用文件 — "/api/clrfiles"
    // 查询节目分辨率自适应开关 — "/api/programautoscale.json"
    // 配置节目分辨率自适应 — "/api/programautoscale"
    // 查询同步节目开关状态 — "/api/sync_program_mode.json"
    // 设置同步节目开关 — "/api/sync_program_mode" — SyncProgramModePayload
    // 设置节目名提示 — "/api/showtoast"
    // 查询节目名提示开关 — "/api/showtoast.json"

    // ── 网络诊断 ──
    // 查询 4G 网络详细信息 — "/api/4ginfo.json"
    // 扫描 WiFi 列表 — "/api/wifi.json"
    // Ping IP/主机名 — "/api/ping" — PingPayload

    // ── 排程 ──
    // 查询局域网排程 — "/api/lanschedule.json"
    // 设置局域网排程 — "/api/lanschedule" — SchedulePayload

    // ── 接收卡 ──
    // 回读连接关系为 JSON — "/api/rcv_layout.json"
    // 通过 JSON 发送/固化连接关系 — "/api/rcv_layout" — RcvLayoutPayload
    // 探测接收卡 — "/api/smart_read_serial_info"
}
