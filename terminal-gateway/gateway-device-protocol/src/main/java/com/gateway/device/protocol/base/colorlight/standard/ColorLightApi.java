package com.gateway.device.protocol.base.colorlight.standard;

import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpMethod;
import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;

/**
 * ColorLight HTTP API 端点枚举 —— 集中定义所有 ColorLight 设备的 REST 接口。
 *
 * <p>基于 PlayerSDK 文档（~158 个接口）全覆盖映射。
 * 每个枚举常量绑定 HTTP 方法和路径模板。
 * 含变量的路径使用 {@code {0}} {@code {1}} 占位符，
 * 调用 {@link #resolvePath(Object...)} 替换为实际值。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 固定路径
 * ColorLightApi.DEVICE_INFO.method()  // GET
 * ColorLightApi.DEVICE_INFO.path()    // "/api/info.json"
 *
 * // 变量路径
 * ColorLightApi.MEDIA_DELETE.resolvePath("background.jpg")
 * // → "/api/vsns/sources/lan/vsns/background.jpg"
 * </pre>
 *
 * @see ColorLightCommand VSN 节目内容常量
 */
public enum ColorLightApi {

    // ════════════════════════════════════════════════════
    // 设备信息
    // ════════════════════════════════════════════════════

    /**
     * GET /api/info.json — 设备基本信息
     */
    DEVICE_INFO(ColorLightHttpMethod.GET, "/api/info.json"),
    /**
     * GET /api/boardconfig.json — 板卡配置
     */
    BOARD_CONFIG(ColorLightHttpMethod.GET, "/api/boardconfig.json"),
    /**
     * GET /api/ifstatus.json — 网络接口状态（含 MAC）
     */
    IF_STATUS(ColorLightHttpMethod.GET, "/api/ifstatus.json"),
    /**
     * GET /api/dimension.json — 屏幕物理参数（宽高/帧率/时钟）
     */
    DIMENSION(ColorLightHttpMethod.GET, "/api/dimension.json"),
    /**
     * GET /api/terminal.json — 设备名称与描述
     */
    TERMINAL_GET(ColorLightHttpMethod.GET, "/api/terminal.json"),
    /**
     * GET /api/powerstatus.json — 设备电源状态（休眠/唤醒）
     */
    POWER_STATUS_GET(ColorLightHttpMethod.GET, "/api/powerstatus.json"),
    /**
     * GET /api/locale.json — 语言与国家配置
     */
    LOCALE_GET(ColorLightHttpMethod.GET, "/api/locale.json"),
    /**
     * GET /api/newrtc.json — 设备系统时间与时区
     */
    TIME_GET(ColorLightHttpMethod.GET, "/api/newrtc.json"),
    /**
     * GET /api/ftpd.json — FTP 功能开关状态
     */
    FTPD_GET(ColorLightHttpMethod.GET, "/api/ftpd.json"),
    /**
     * GET /api/ntpd.json — NTPD 服务开关状态
     */
    NTPD_GET(ColorLightHttpMethod.GET, "/api/ntpd.json"),
    /**
     * GET /api/network.json — 当前网络配置
     */
    NETWORK_GET(ColorLightHttpMethod.GET, "/api/network.json"),
    /**
     * GET /api/board_relay.json — 板载继电器数据
     */
    BOARD_RELAY_GET(ColorLightHttpMethod.GET, "/api/board_relay.json"),
    /**
     * GET /api/screen_orientation.json — 屏幕旋转方向
     */
    SCREEN_ORIENTATION_GET(ColorLightHttpMethod.GET, "/api/screen_orientation.json"),
    /**
     * GET /api/brightnessandcolortemp.json — 亮度与色温
     */
    BRIGHTNESS_COLOR_TEMP_GET(ColorLightHttpMethod.GET, "/api/brightnessandcolortemp.json"),
    /**
     * GET /api/brightcurve.json — 自动亮度曲线
     */
    BRIGHT_CURVE_GET(ColorLightHttpMethod.GET, "/api/brightcurve.json"),
    /**
     * GET /api/screenconfig.json — 对比度/色调/饱和度
     */
    SCREEN_CONFIG_GET(ColorLightHttpMethod.GET, "/api/screenconfig.json"),
    /**
     * GET /api/cpu_color_control.json — 低亮高灰开关状态（旧版）
     */
    CPU_COLOR_CONTROL_GET(ColorLightHttpMethod.GET, "/api/cpu_color_control.json"),
    /**
     * GET /api/smart_read_serial_info — 通用串口读取。
     *
     * <p>通过 {@code ?intent=} 查询参数区分功能：
     * <ul>
     *   <li>{@code basicInfo} — 低亮高灰状态（新版）</li>
     *   <li>{@code scanReceiverCard} — 探测接收卡</li>
     * </ul>
     *
     * @see com.gateway.device.protocol.base.colorlight.standard.model.api.response.LowBrightHighGrayInfo
     * @see com.gateway.device.protocol.base.colorlight.standard.model.api.response.RcvScanInfo
     */
    SMART_READ_SERIAL_INFO(ColorLightHttpMethod.GET, "/api/smart_read_serial_info"),
    /**
     * GET /api/4ginfo.json — 4G 网络详细信息
     */
    NET_4G_INFO_GET(ColorLightHttpMethod.GET, "/api/4ginfo.json"),
    /**
     * GET /api/wifi.json — 扫描 WiFi 列表
     */
    WIFI_SCAN_GET(ColorLightHttpMethod.GET, "/api/wifi.json"),
    /**
     * GET /api/apn.json — APN 配置列表
     */
    APN_LIST_GET(ColorLightHttpMethod.GET, "/api/apn.json"),
    /**
     * GET /api/simslot.json — 当前 SIM 卡槽
     */
    SIM_SLOT_GET(ColorLightHttpMethod.GET, "/api/simslot.json"),
    /**
     * GET /api/veth.json — 虚拟网口开关状态
     */
    VETH_GET(ColorLightHttpMethod.GET, "/api/veth.json"),
    /**
     * GET /api/networkfailurepromptswitch.json — 网络错误提示开关
     */
    NETWORK_FAILURE_PROMPT_GET(ColorLightHttpMethod.GET, "/api/networkfailurepromptswitch.json"),
    /**
     * GET /api/relay.json — 继电器状态
     */
    RELAY_GET(ColorLightHttpMethod.GET, "/api/relay.json"),
    /**
     * GET /api/autorelay.json — 自动继电器配置
     */
    AUTO_RELAY_GET(ColorLightHttpMethod.GET, "/api/autorelay.json"),
    /**
     * GET /api/screenstatus.json — 屏幕开关状态
     */
    SCREEN_STATUS_GET(ColorLightHttpMethod.GET, "/api/screenstatus.json"),

    // ════════════════════════════════════════════════════
    // 设备控制
    // ════════════════════════════════════════════════════

    /**
     * PUT /api/brightness — 亮度调节
     */
    BRIGHTNESS(ColorLightHttpMethod.PUT, "/api/brightness"),
    /**
     * PUT /api/screenstatus — 屏幕开关（黑屏/亮屏）
     */
    SCREEN_STATUS(ColorLightHttpMethod.PUT, "/api/screenstatus"),
    /**
     * PUT /api/newrtc — 时间同步
     */
    TIME_SYNC(ColorLightHttpMethod.PUT, "/api/newrtc"),
    /**
     * GET /api/volume.json — 音量查询
     */
    VOLUME_GET(ColorLightHttpMethod.GET, "/api/volume.json"),
    /**
     * PUT /api/volume — 音量设置
     */
    VOLUME_SET(ColorLightHttpMethod.PUT, "/api/volume"),
    /**
     * GET /api/ntp.json — NTP 配置查询
     */
    NTP_GET(ColorLightHttpMethod.GET, "/api/ntp.json"),
    /**
     * PUT /api/ntp — NTP 配置设置（已废弃，使用 {@link #SYNC_PROGRAM_MODE_SET}）
     */
    @Deprecated
    NTP_SET(ColorLightHttpMethod.PUT, "/api/ntp"),
    /**
     * POST /api/network — 网络配置
     */
    NETWORK(ColorLightHttpMethod.POST, "/api/network"),
    /**
     * POST /api/action — 电源/设备操作 (reboot/sleep/wakeup)
     */
    ACTION(ColorLightHttpMethod.POST, "/api/action"),
    /**
     * PUT /api/terminal — 配置设备名称与描述
     */
    TERMINAL_SET(ColorLightHttpMethod.PUT, "/api/terminal"),
    /**
     * PUT /api/locale — 配置语言与国家
     */
    LOCALE_SET(ColorLightHttpMethod.PUT, "/api/locale"),
    /**
     * PUT /api/ftpd — 设置 FTP 功能开关
     */
    FTPD_SET(ColorLightHttpMethod.PUT, "/api/ftpd"),
    /**
     * PUT /api/ntpd — 设备作为 NTP 服务器开关
     */
    NTPD_SET(ColorLightHttpMethod.PUT, "/api/ntpd"),
    /**
     * PUT /api/board_relay — 设置板载继电器开关
     */
    BOARD_RELAY_SET(ColorLightHttpMethod.PUT, "/api/board_relay"),
    /**
     * POST /api/screen_orientation — 设置屏幕旋转方向
     */
    SCREEN_ORIENTATION_SET(ColorLightHttpMethod.POST, "/api/screen_orientation"),
    /**
     * PUT /api/resetfact — 恢复出厂设置
     */
    RESET_FACTORY(ColorLightHttpMethod.PUT, "/api/resetfact"),
    /**
     * PUT /api/adb — 开启 ADB 调试模式
     */
    ADB_SET(ColorLightHttpMethod.PUT, "/api/adb"),
    /**
     * PUT /api/colortemp — 设置色温值 (2000-10000)
     */
    COLOR_TEMP_SET(ColorLightHttpMethod.PUT, "/api/colortemp"),
    /**
     * PUT /api/savebrightnessandcolortemp — 同时设置亮度和色温
     */
    BRIGHTNESS_COLOR_TEMP_SET(ColorLightHttpMethod.PUT, "/api/savebrightnessandcolortemp"),
    /**
     * PUT /api/screenconfig — 设置对比度/色调/饱和度
     */
    SCREEN_CONFIG_SET(ColorLightHttpMethod.PUT, "/api/screenconfig"),
    /**
     * PUT /api/cpu_color_control — 设置低亮高灰（旧版 A 系列）
     */
    CPU_COLOR_CONTROL_SET(ColorLightHttpMethod.PUT, "/api/cpu_color_control"),
    /**
     * PUT /api/smart_sender?intent=lowBrightHighGray — 设置低亮高灰（新版）
     */
    LOW_BRIGHT_HIGH_GRAY_SET(ColorLightHttpMethod.PUT, "/api/smart_sender"),
    /**
     * POST /api/brightcurve — 配置自动亮度曲线（含 gamma）
     */
    BRIGHT_CURVE_SET(ColorLightHttpMethod.POST, "/api/brightcurve"),
    /**
     * PUT /api/autorelay — 自动继电器设置
     */
    AUTO_RELAY_SET(ColorLightHttpMethod.PUT, "/api/autorelay"),
    /**
     * PUT /api/relay — 设置继电器开关状态
     */
    RELAY_SET(ColorLightHttpMethod.PUT, "/api/relay"),
    /**
     * PUT /api/sendingcard — 设置网口控制面积
     */
    SENDING_CARD_SET(ColorLightHttpMethod.PUT, "/api/sendingcard"),
    /**
     * GET /api/sendingcard.json — 获取网口控制面积
     */
    SENDING_CARD_GET(ColorLightHttpMethod.GET, "/api/sendingcard.json"),
    /**
     * PUT /api/filter_zero_brightness — 设置亮度无效值屏蔽
     */
    FILTER_ZERO_BRIGHTNESS_SET(ColorLightHttpMethod.PUT, "/api/filter_zero_brightness"),
    /**
     * GET /api/filter_zero_brightness.json — 获取亮度无效值屏蔽状态
     */
    FILTER_ZERO_BRIGHTNESS_GET(ColorLightHttpMethod.GET, "/api/filter_zero_brightness.json"),
    /**
     * PUT /api/veth — 设置虚拟网口开关
     */
    VETH_SET(ColorLightHttpMethod.PUT, "/api/veth"),
    /**
     * PUT /api/networkfailurepromptswitch — 设置网络错误提示开关
     */
    NETWORK_FAILURE_PROMPT_SET(ColorLightHttpMethod.PUT, "/api/networkfailurepromptswitch"),
    /**
     * PUT /api/simslot — 切换 SIM 卡槽
     */
    SIM_SLOT_SET(ColorLightHttpMethod.PUT, "/api/simslot"),

    // ════════════════════════════════════════════════════
    // 输入输出 / 显示
    // ════════════════════════════════════════════════════

    /**
     * PUT /api/inputmode — 设置输入模式 (hdmi/dvi)
     */
    INPUT_MODE_SET(ColorLightHttpMethod.PUT, "/api/inputmode"),
    /**
     * GET /api/inputmode.json — 获取输入模式
     */
    INPUT_MODE_GET(ColorLightHttpMethod.GET, "/api/inputmode.json"),
    /**
     * PUT /api/dimension — 配置输出分辨率
     */
    DIMENSION_SET(ColorLightHttpMethod.PUT, "/api/dimension"),
    /**
     * PUT /api/fps — 设置帧率 (15-120)
     */
    FPS_SET(ColorLightHttpMethod.PUT, "/api/fps"),
    /**
     * GET /api/fps.json — 获取帧率
     */
    FPS_GET(ColorLightHttpMethod.GET, "/api/fps.json"),
    /**
     * PUT /api/audiointput — 设置音频输入源
     */
    AUDIO_INPUT_SET(ColorLightHttpMethod.PUT, "/api/audiointput"),
    /**
     * GET /api/audiointput.json — 获取音频输入源
     */
    AUDIO_INPUT_GET(ColorLightHttpMethod.GET, "/api/audiointput.json"),
    /**
     * PUT /api/hdmi_status — 设置 HDMI 强制输出
     */
    HDMI_STATUS_SET(ColorLightHttpMethod.PUT, "/api/hdmi_status"),
    /**
     * GET /api/hdmi_status.json — 获取 HDMI 强制输出
     */
    HDMI_STATUS_GET(ColorLightHttpMethod.GET, "/api/hdmi_status.json"),
    /**
     * POST /api/autoscale — 设置 HDMI 自动缩放
     */
    AUTO_SCALE_HDMI_SET(ColorLightHttpMethod.POST, "/api/autoscale"),
    /**
     * GET /api/autoscale.json — 获取 HDMI 自动缩放
     */
    AUTO_SCALE_HDMI_GET(ColorLightHttpMethod.GET, "/api/autoscale.json"),
    /**
     * POST /api/setcutandscale — 设置裁剪数据
     */
    CUT_AND_SCALE_SET(ColorLightHttpMethod.POST, "/api/setcutandscale"),
    /**
     * GET /api/setcutandscale.json — 获取裁剪数据
     */
    CUT_AND_SCALE_GET(ColorLightHttpMethod.GET, "/api/setcutandscale.json"),
    /**
     * GET /api/allbrightnessinfo.json — 获取所有亮度相关信息
     */
    ALL_BRIGHTNESS_GET(ColorLightHttpMethod.GET, "/api/allbrightnessinfo.json"),

    // ════════════════════════════════════════════════════
    // 视频播放控制
    // ════════════════════════════════════════════════════

    /**
     * POST /api/videocontrol — 视频播放/暂停。
     *
     * @deprecated 使用 {@link #VIDEO_COMMAND}（/api/videocommand）替代，支持更完整的视频控制命令
     */
    @Deprecated
    VIDEO_CONTROL(ColorLightHttpMethod.POST, "/api/videocontrol"),
    /**
     * POST /api/videocommand — 视频播放命令（快进/快退/跳转）
     */
    VIDEO_COMMAND(ColorLightHttpMethod.POST, "/api/videocommand"),
    /**
     * POST /api/program/seekto — 视频节目进度跳转（毫秒）
     */
    PROGRAM_SEEK_TO(ColorLightHttpMethod.POST, "/api/program/seekto"),

    // ════════════════════════════════════════════════════
    // VSN / 媒体管理
    // ════════════════════════════════════════════════════

    /**
     * GET /api/vsns.json — 媒体/节目列表
     */
    VSN_LIST(ColorLightHttpMethod.GET, "/api/vsns.json"),
    /**
     * POST /api/program/{0}.vsn — 单媒体上传
     */
    MEDIA_UPLOAD(ColorLightHttpMethod.POST, "/api/program/{0}.vsn"),
    /**
     * DELETE /api/vsns/sources/lan/vsns/{0} — 媒体删除
     */
    MEDIA_DELETE(ColorLightHttpMethod.DELETE, "/api/vsns/sources/lan/vsns/{0}"),

    // ════════════════════════════════════════════════════
    // 节目管理
    // ════════════════════════════════════════════════════

    /**
     * POST /api/program/singletext — 快速发布单行文本节目。
     *
     * @deprecated 使用 {@link #MEDIA_UPLOAD}（/api/program/{0}.vsn）替代，文本内容通过
     * {@link com.gateway.device.protocol.base.colorlight.standard.helper.ColorLightProgramBuilder#buildSingleText} 构建 VSN JSON
     */
    @Deprecated
    SINGLE_TEXT_UPLOAD(ColorLightHttpMethod.POST, "/api/program/singletext"),
    /**
     * PUT /api/rename/{0} — 节目重命名
     */
    PROGRAM_RENAME(ColorLightHttpMethod.PUT, "/api/rename/{0}"),
    /**
     * PUT /api/programthumbnail/{0} — 设置局域网节目缩略图
     */
    PROGRAM_THUMBNAIL_SET(ColorLightHttpMethod.PUT, "/api/programthumbnail/{0}"),
    /**
     * DELETE /api/clrcache — 清空节目缓存资源
     */
    CLEAR_CACHE(ColorLightHttpMethod.DELETE, "/api/clrcache"),
    /**
     * DELETE /api/clrfiles — 清空局域网无用文件
     */
    CLEAR_UNUSED_FILES(ColorLightHttpMethod.DELETE, "/api/clrfiles"),
    /**
     * DELETE /api/clrresunused — 清除无用缓存文件
     */
    CLEAR_UNUSED_RES(ColorLightHttpMethod.DELETE, "/api/clrresunused"),
    /**
     * POST /api/setprogramjson/{0} — 上传局域网节目编辑文件
     */
    SET_PROGRAM_JSON(ColorLightHttpMethod.POST, "/api/setprogramjson/{0}"),
    /**
     * POST /api/image_thumb — 配置大图缩略图
     */
    IMAGE_THUMB_SET(ColorLightHttpMethod.POST, "/api/image_thumb"),
    /**
     * GET /api/image_thumb.json — 获取大图缩略图配置
     */
    IMAGE_THUMB_GET(ColorLightHttpMethod.GET, "/api/image_thumb.json"),

    // ════════════════════════════════════════════════════
    // 字体
    // ════════════════════════════════════════════════════

    /**
     * GET /api/fonts.json — 字体列表查询
     */
    FONTS_GET(ColorLightHttpMethod.GET, "/api/fonts.json"),
    /**
     * POST /api/fonts — 字体上传
     */
    FONTS_SYNC(ColorLightHttpMethod.POST, "/api/fonts"),
    /**
     * DELETE /api/fonts/{0} — 删除指定字体
     */
    FONT_DELETE(ColorLightHttpMethod.DELETE, "/api/fonts/{0}"),
    /**
     * DELETE /api/fonts/* — 删除所有字体
     */
    FONT_DELETE_ALL(ColorLightHttpMethod.DELETE, "/api/fonts/*"),

    // ════════════════════════════════════════════════════
    // 播放列表
    // ════════════════════════════════════════════════════

    /**
     * PUT /api/vsns/sources/lan/vsns/{0}/activated — 激活指定节目
     */
    PLAYLIST_SET(ColorLightHttpMethod.PUT, "/api/vsns/sources/lan/vsns/{0}/activated"),
    /**
     * DELETE /api/clrprgms — 清空终端节目
     */
    PLAYLIST_CLEAR(ColorLightHttpMethod.DELETE, "/api/clrprgms"),

    // ════════════════════════════════════════════════════
    // 节目效果
    // ════════════════════════════════════════════════════

    /**
     * GET /api/sync_program_mode.json — 获取同步节目开关状态
     */
    SYNC_PROGRAM_MODE_GET(ColorLightHttpMethod.GET, "/api/sync_program_mode.json"),
    /**
     * PUT /api/sync_program_mode — 设置同步节目开关
     */
    SYNC_PROGRAM_MODE_SET(ColorLightHttpMethod.PUT, "/api/sync_program_mode"),
    /**
     * GET /api/textSource.json — 获取文本节目渲染方式
     */
    TEXT_SOURCE_GET(ColorLightHttpMethod.GET, "/api/textSource.json"),
    /**
     * PUT /api/textSource — 设置文本节目渲染方式
     */
    TEXT_SOURCE_SET(ColorLightHttpMethod.PUT, "/api/textSource"),
    /**
     * PUT /api/textantialias — 设置文本节目抗锯齿
     */
    TEXT_ANTIALIAS_SET(ColorLightHttpMethod.PUT, "/api/textantialias"),
    /**
     * GET /api/programautoscale.json — 获取节目分辨率自适应开关
     */
    PROGRAM_AUTO_SCALE_GET(ColorLightHttpMethod.GET, "/api/programautoscale.json"),
    /**
     * PUT /api/programautoscale — 配置节目分辨率自适应
     */
    PROGRAM_AUTO_SCALE_SET(ColorLightHttpMethod.PUT, "/api/programautoscale"),
    /**
     * GET /api/showtoast.json — 获取节目名提示开关
     */
    SHOW_TOAST_GET(ColorLightHttpMethod.GET, "/api/showtoast.json"),
    /**
     * POST /api/showtoast — 设置节目名提示
     */
    SHOW_TOAST_SET(ColorLightHttpMethod.POST, "/api/showtoast"),

    // ════════════════════════════════════════════════════
    // 网络管理
    // ════════════════════════════════════════════════════

    /**
     * POST /api/ping — Ping IP/主机名
     */
    PING(ColorLightHttpMethod.POST, "/api/ping"),
    /**
     * POST /api/reset4g — 重置 4G 模块
     */
    RESET_4G(ColorLightHttpMethod.POST, "/api/reset4g"),
    /**
     * POST /api/apn — 添加 APN
     */
    APN_CREATE(ColorLightHttpMethod.POST, "/api/apn"),
    /**
     * PUT /api/apnconfig — 修改 APN
     */
    APN_UPDATE(ColorLightHttpMethod.PUT, "/api/apnconfig"),
    /**
     * DELETE /api/apn/{0}/{1} — 按 MCC/MNC 删除 APN
     */
    APN_DELETE_BY_MCC_MNC(ColorLightHttpMethod.DELETE, "/api/apn/{0}/{1}"),
    /**
     * DELETE /api/apn/{0} — 按 numeric 删除 APN
     */
    APN_DELETE_BY_NUMERIC(ColorLightHttpMethod.DELETE, "/api/apn/{0}"),

    // ════════════════════════════════════════════════════
    // 安全认证
    // ════════════════════════════════════════════════════

    /**
     * GET /api/http_verification.json — 获取局域网加密状态
     */
    HTTP_VERIFICATION_GET(ColorLightHttpMethod.GET, "/api/http_verification.json"),
    /**
     * PUT /api/http_verification — 设置局域网加密
     */
    HTTP_VERIFICATION_SET(ColorLightHttpMethod.PUT, "/api/http_verification"),
    /**
     * GET /api/usbplay_verification.json — 获取 USB 加密状态
     */
    USB_VERIFICATION_GET(ColorLightHttpMethod.GET, "/api/usbplay_verification.json"),
    /**
     * PUT /api/usbplay_verification — 设置 USB 加密
     */
    USB_VERIFICATION_SET(ColorLightHttpMethod.PUT, "/api/usbplay_verification"),
    /**
     * GET /api/question.json — 获取安全问题配置
     */
    SECURITY_QUESTION_GET(ColorLightHttpMethod.GET, "/api/question.json"),
    /**
     * POST /api/question — 设置安全问题
     */
    SECURITY_QUESTION_SET(ColorLightHttpMethod.POST, "/api/question"),
    /**
     * POST /api/verify_answer — 校验安全问题答案
     */
    SECURITY_ANSWER_VERIFY(ColorLightHttpMethod.POST, "/api/verify_answer"),
    /**
     * GET /api/security_email.json — 获取安全邮箱配置
     */
    SECURITY_EMAIL_GET(ColorLightHttpMethod.GET, "/api/security_email.json"),
    /**
     * POST /api/security_email — 配置安全邮箱
     */
    SECURITY_EMAIL_SET(ColorLightHttpMethod.POST, "/api/security_email"),
    /**
     * GET /api/hotp_qr_code.json — 获取邮箱重置密码二维码
     */
    HOTP_QR_CODE_GET(ColorLightHttpMethod.GET, "/api/hotp_qr_code.json"),
    /**
     * POST /api/verify_totp_code — 校验邮箱安全码
     */
    TOTP_CODE_VERIFY(ColorLightHttpMethod.POST, "/api/verify_totp_code"),
    /**
     * POST /api/reset_pwd — 使用 token 重置密码
     */
    PASSWORD_RESET(ColorLightHttpMethod.POST, "/api/reset_pwd"),

    // ════════════════════════════════════════════════════
    // 排程
    // ════════════════════════════════════════════════════

    /**
     * GET /api/lanschedule.json — 获取局域网排程
     */
    LAN_SCHEDULE_GET(ColorLightHttpMethod.GET, "/api/lanschedule.json"),
    /**
     * GET /api/internetschedule.json — 获取互联网排程
     */
    INTERNET_SCHEDULE_GET(ColorLightHttpMethod.GET, "/api/internetschedule.json"),
    /**
     * PUT /api/lanschedule — 设置局域网排程
     */
    LAN_SCHEDULE_SET(ColorLightHttpMethod.PUT, "/api/lanschedule"),

    // ════════════════════════════════════════════════════
    // 接收卡
    // ════════════════════════════════════════════════════

    /**
     * GET /api/rcv_layout.json — 回读连接关系为 JSON
     */
    RCV_LAYOUT_GET(ColorLightHttpMethod.GET, "/api/rcv_layout.json"),
    /**
     * GET /api/rcv_layout_file — 回读连接关系为文件
     */
    RCV_LAYOUT_FILE(ColorLightHttpMethod.GET, "/api/rcv_layout_file"),
    /**
     * GET /api/rcv_params_file — 回读接收卡参数为文件
     */
    RCV_PARAMS_FILE(ColorLightHttpMethod.GET, "/api/rcv_params_file"),
    /**
     * POST /api/rcv_layout — 通过 JSON 发送/固化连接关系
     */
    RCV_LAYOUT_SET(ColorLightHttpMethod.POST, "/api/rcv_layout"),
    /**
     * POST /api/set_rcv_layout — 通过文件发送/固化连接关系
     */
    RCV_LAYOUT_FILE_SET(ColorLightHttpMethod.POST, "/api/set_rcv_layout"),
    /**
     * POST /api/set_rcv_params — 通过文件固化接收卡参数
     */
    RCV_PARAMS_SET(ColorLightHttpMethod.POST, "/api/set_rcv_params"),
    /**
     * POST /api/rcv_update — 升级接收卡
     */
    RCV_UPDATE(ColorLightHttpMethod.POST, "/api/rcv_update"),
    /**
     * POST /api/reset_bit_error_rate — 清空接收卡误码率
     */
    RCV_RESET_BIT_ERROR(ColorLightHttpMethod.POST, "/api/reset_bit_error_rate"),
    // ════════════════════════════════════════════════════
    // 调试维护
    // ════════════════════════════════════════════════════

    /**
     * GET /api/logcat.json — 获取系统日志
     */
    LOG_GET(ColorLightHttpMethod.GET, "/api/logcat.json"),
    /**
     * GET /api/dmesg.json — 获取内核日志
     */
    DMESG_GET(ColorLightHttpMethod.GET, "/api/dmesg.json"),
    /**
     * GET /api/4G.json — 获取 4G 日志
     */
    LOG_4G_GET(ColorLightHttpMethod.GET, "/api/4G.json"),
    /**
     * POST /api/ntptest — 测试 NTP 服务器
     */
    NTP_TEST(ColorLightHttpMethod.POST, "/api/ntptest"),

    // ════════════════════════════════════════════════════
    // [已弃用] 快速更新图片 — 使用 MEDIA_UPLOAD (/api/program/{0}.vsn) 替代，
    // 删除操作使用 MEDIA_DELETE
    // ════════════════════════════════════════════════════

    /**
     * POST /api/update_image — 上传快速更新图片。
     *
     * @deprecated 使用 {@link #MEDIA_UPLOAD}（/api/program/{0}.vsn）替代
     */
    @Deprecated
    UPDATE_IMAGE_UPLOAD(ColorLightHttpMethod.POST, "/api/update_image"),
    /**
     * GET /api/update_image.json — 获取快速更新图片列表。
     *
     * @deprecated 使用 {@link #VSN_LIST}（/api/vsns.json）替代
     */
    @Deprecated
    UPDATE_IMAGE_LIST(ColorLightHttpMethod.GET, "/api/update_image.json"),
    /**
     * DELETE /api/update_image/{0} — 删除指定快速更新图片。
     *
     * @deprecated 使用 {@link #MEDIA_DELETE}（/api/vsns/sources/lan/vsns/{0}）替代
     */
    @Deprecated
    UPDATE_IMAGE_DELETE(ColorLightHttpMethod.DELETE, "/api/update_image/{0}"),
    /**
     * DELETE /api/update_image/* — 删除所有快速更新图片。
     *
     * @deprecated 使用 {@link #CLEAR_CACHE} 或 {@link #CLEAR_UNUSED_FILES} 替代
     */
    @Deprecated
    UPDATE_IMAGE_DELETE_ALL(ColorLightHttpMethod.DELETE, "/api/update_image/*"),

    // ════════════════════════════════════════════════════
    // 文本 / WebView
    // ════════════════════════════════════════════════════

    /**
     * GET /api/webview_config — 获取 WebView 缓存配置
     */
    WEBVIEW_CONFIG_GET(ColorLightHttpMethod.GET, "/api/webview_config"),
    /**
     * PUT /api/webview_config — 设置 WebView 缓存配置
     */
    WEBVIEW_CONFIG_SET(ColorLightHttpMethod.PUT, "/api/webview_config");

    // ════════════════════════════════════════════════════
    // 字段与方法
    // ════════════════════════════════════════════════════

    private final ColorLightHttpMethod method;
    private final String pathTemplate;

    ColorLightApi(ColorLightHttpMethod method, String pathTemplate) {
        this.method = method;
        this.pathTemplate = pathTemplate;
    }

    /**
     * HTTP 方法 (GET/POST/PUT/DELETE)
     */
    public ColorLightHttpMethod method() {
        return method;
    }

    /**
     * 路径模板（可能含 {0} {1} 占位符）。
     * 无变量端点直接返回完整 API 路径。
     */
    public String path() {
        return pathTemplate;
    }

    /**
     * 使用 {@link StringSubstitutor} 替换路径模板中的 {0} {1} … 占位符。
     *
     * @param vars 按索引顺序替换的值，{0} 对应 vars[0]
     * @return 替换后的完整路径
     */
    public String resolvePath(Object... vars) {
        if (vars == null || vars.length == 0) {
            return pathTemplate;
        }
        Map<String, String> valueMap = new HashMap<>(vars.length);
        for (int i = 0; i < vars.length; i++) {
            valueMap.put(String.valueOf(i), String.valueOf(vars[i]));
        }
        return StringSubstitutor.replace(pathTemplate, valueMap, "{", "}");
    }
}
