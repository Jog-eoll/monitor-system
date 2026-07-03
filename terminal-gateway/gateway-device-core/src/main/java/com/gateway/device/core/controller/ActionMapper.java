package com.gateway.device.core.controller;

import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 业务动作 → 内部设备能力映射器
 * <p>
 * 对外接口（Sigma / 管控平台）使用中文业务动作名称，如"设置播放列表"、"调节亮度"；
 * 本类负责将其翻译为解密网关内部的 {@link DeviceCapability} 能力常量，
 * 再由现有 Handler 路由到对应的 JetFileII 二进制指令。
 * </p>
 *
 * <p>完整链路示例：
 * <pre>
 *   Sigma JSON { action: "设置播放列表" }
 *       ↓  ActionMapper
 *   CommonDeviceCapability.PLAYLIST_SET
 *       ↓  JetFileIIAdapter
 *   DISPLAY / DISP_REPLAY_LIST (0x06 / 0x01)
 * </pre>
 * </p>
 */
@Slf4j
@Component
public class ActionMapper {

    // ════════════════════════════════════════════════════
    // 业务动作常量（对外契约，Sigma 团队可见）
    // ════════════════════════════════════════════════════

    // ── 设备管理 ──
    public static final String ACTION_DEVICE_SEARCH       = "搜索设备";
    public static final String ACTION_DEVICE_INFO_GET     = "获取设备信息";

    // ── 图片 ──
    public static final String ACTION_IMAGE_LIST_QUERY    = "查询图片列表";
    public static final String ACTION_IMAGE_UPLOAD        = "上传图片";
    public static final String ACTION_IMAGE_DOWNLOAD      = "下载图片";

    // ── 文字 ──
    public static final String ACTION_TEXT_UPLOAD         = "上传文字";
    public static final String ACTION_TEXT_DOWNLOAD       = "下载文字";
    public static final String ACTION_TEXT_DELETE         = "删除文字";

    // ── 视频 ──
    public static final String ACTION_VIDEO_LIST_QUERY    = "查询视频列表";
    public static final String ACTION_VIDEO_UPLOAD        = "上传视频";
    public static final String ACTION_VIDEO_DOWNLOAD      = "下载视频";

    // ── 文件管理 ──
    public static final String ACTION_FILE_DELETE_CLEAR   = "清除文件";

    // ── 播放列表 ──
    public static final String ACTION_PLAYLIST_GET        = "获取播放列表";
    public static final String ACTION_PLAYLIST_SET        = "设置播放列表";

    // ── 设备控制 ──
    public static final String ACTION_POWER_ON            = "开机";
    public static final String ACTION_POWER_OFF           = "关机";
    public static final String ACTION_RESTART             = "重启";
    public static final String ACTION_SCREEN_BLACKOUT     = "黑屏";
    public static final String ACTION_SCREEN_ON           = "恢复显示";
    public static final String ACTION_BRIGHTNESS_SET      = "调节亮度";
    public static final String ACTION_COLOR_TEST          = "色彩测试";
    public static final String ACTION_TIME_SYNC           = "校时";

    // ── Nmg 自有格式 ──
    public static final String ACTION_NMG_LIST_QUERY      = "查询NMG列表";
    public static final String ACTION_NMG_UPLOAD          = "上传NMG";
    public static final String ACTION_NMG_DOWNLOAD        = "下载NMG";

    // ── Pmg 自有格式 ──
    public static final String ACTION_PMG_LIST_QUERY      = "查询PMG列表";
    public static final String ACTION_PMG_UPLOAD          = "上传PMG";
    public static final String ACTION_PMG_DOWNLOAD        = "下载PMG";

    // ── Qst 自有格式 ──
    public static final String ACTION_QST_LIST_QUERY      = "查询QST列表";
    public static final String ACTION_QST_UPLOAD          = "上传QST";
    public static final String ACTION_QST_DOWNLOAD        = "下载QST";

    // ════════════════════════════════════════════════════
    // 映射表（不可变）
    // ════════════════════════════════════════════════════

    private static final Map<String, DeviceCapability<?>> ACTION_MAP;
    private static final Map<String, String> ACTION_ALIASES;
    private static final DeviceCapability<?> DEVICE_SEARCH_CAPABILITY = DeviceCapability.of("DEVICE_SEARCH");

    static {
        Map<String, DeviceCapability<?>> m = new LinkedHashMap<>();

        // 设备管理
        m.put(ACTION_DEVICE_SEARCH,     DEVICE_SEARCH_CAPABILITY);
        m.put(ACTION_DEVICE_INFO_GET,   CommonDeviceCapability.DEVICE_INFO_GET);

        // 图片
        m.put(ACTION_IMAGE_LIST_QUERY,  CommonDeviceCapability.IMAGE_LIST_QUERY);
        m.put(ACTION_IMAGE_UPLOAD,      CommonDeviceCapability.IMAGE_UPLOAD);
        m.put(ACTION_IMAGE_DOWNLOAD,    CommonDeviceCapability.IMAGE_DOWNLOAD);

        // 文字
        m.put(ACTION_TEXT_UPLOAD,       CommonDeviceCapability.TEXT_UPLOAD);
        m.put(ACTION_TEXT_DOWNLOAD,     CommonDeviceCapability.TEXT_DOWNLOAD);
        m.put(ACTION_TEXT_DELETE,       CommonDeviceCapability.TEXT_DELETE);

        // 视频
        m.put(ACTION_VIDEO_LIST_QUERY,  CommonDeviceCapability.VIDEO_LIST_QUERY);
        m.put(ACTION_VIDEO_UPLOAD,      CommonDeviceCapability.VIDEO_UPLOAD);
        m.put(ACTION_VIDEO_DOWNLOAD,    CommonDeviceCapability.VIDEO_DOWNLOAD);

        // 文件管理
        m.put(ACTION_FILE_DELETE_CLEAR, CommonDeviceCapability.MEDIA_CLEAR);

        // 播放列表
        m.put(ACTION_PLAYLIST_GET,      CommonDeviceCapability.PLAYLIST_GET);
        m.put(ACTION_PLAYLIST_SET,      CommonDeviceCapability.PLAYLIST_SET);

        // 设备控制（当前通用能力仅启用重启；开关机动作暂不映射）
        m.put(ACTION_RESTART,           CommonDeviceCapability.POWER_CONTROL_REBOOT);
        m.put(ACTION_SCREEN_BLACKOUT,   CommonDeviceCapability.SCREEN_BLACKOUT);
        m.put(ACTION_SCREEN_ON,         CommonDeviceCapability.SCREEN_BLACKOUT);
        m.put(ACTION_BRIGHTNESS_SET,    CommonDeviceCapability.BRIGHTNESS_SET);
        m.put(ACTION_COLOR_TEST,        JetFileIICapability.COLOR_TEST);
        m.put(ACTION_TIME_SYNC,         CommonDeviceCapability.TIME_SYNC);

        // Nmg
        m.put(ACTION_NMG_LIST_QUERY,    JetFileIICapability.NMG_FILE_LIST_QUERY);
        m.put(ACTION_NMG_UPLOAD,        JetFileIICapability.NMG_FILE_UPLOAD);
        m.put(ACTION_NMG_DOWNLOAD,      JetFileIICapability.NMG_FILE_DOWNLOAD);

        // Pmg
        m.put(ACTION_PMG_LIST_QUERY,    JetFileIICapability.PMG_FILE_LIST_QUERY);
        m.put(ACTION_PMG_UPLOAD,        JetFileIICapability.PMG_FILE_UPLOAD);
        m.put(ACTION_PMG_DOWNLOAD,      JetFileIICapability.PMG_FILE_DOWNLOAD);

        // Qst
        m.put(ACTION_QST_LIST_QUERY,    JetFileIICapability.QST_FILE_LIST_QUERY);
        m.put(ACTION_QST_UPLOAD,        JetFileIICapability.QST_FILE_UPLOAD);
        m.put(ACTION_QST_DOWNLOAD,      JetFileIICapability.QST_FILE_DOWNLOAD);

        ACTION_MAP = Collections.unmodifiableMap(m);

        Map<String, String> aliases = new HashMap<>();
        aliases.put("查询设备信息", ACTION_DEVICE_INFO_GET);
        aliases.put("上传图片播放文件", ACTION_IMAGE_UPLOAD);
        aliases.put("上传视频播放文件", ACTION_VIDEO_UPLOAD);
        aliases.put("上传文本播放文件", ACTION_TEXT_UPLOAD);
        aliases.put("上传NMG播放文件", ACTION_NMG_UPLOAD);
        aliases.put("上传 NMG 播放文件", ACTION_NMG_UPLOAD);
        aliases.put("上传PMG播放文件", ACTION_PMG_UPLOAD);
        aliases.put("上传 PMG 播放文件", ACTION_PMG_UPLOAD);
        aliases.put("上传QST播放文件", ACTION_QST_UPLOAD);
        aliases.put("上传 QST 播放文件", ACTION_QST_UPLOAD);
        aliases.put("设置待播放列表", ACTION_PLAYLIST_SET);
        aliases.put("查询待播放列表", ACTION_PLAYLIST_GET);
        aliases.put("黑屏/恢复", ACTION_SCREEN_BLACKOUT);
        aliases.put("时间同步", ACTION_TIME_SYNC);
        aliases.put("读取屏端文件", ACTION_NMG_DOWNLOAD);
        aliases.put("删除屏端文件", ACTION_FILE_DELETE_CLEAR);
        ACTION_ALIASES = Collections.unmodifiableMap(aliases);
    }

    // ════════════════════════════════════════════════════
    // 公共方法
    // ════════════════════════════════════════════════════

    /**
     * 将中文业务动作映射为内部 DeviceCapability。
     *
     * @param action 业务动作名称（如"设置播放列表"）
     * @return 对应的 DeviceCapability；无法识别时返回 null
     */
    public DeviceCapability<?> map(String action) {
        if (action == null || action.trim().isEmpty()) {
            return null;
        }
        String normalizedAction = normalize(action);
        DeviceCapability<?> cap = ACTION_MAP.get(normalizedAction);
        if (cap == null) {
            log.warn("[action映射] 未识别的业务动作: '{}'", action);
        }
        return cap;
    }

    /**
     * 根据业务动作自动填充 params 中的控制参数。
     * <p>
     * 例如：
     * <ul>
     *   <li>"开机" → params.put("on", true)</li>
     *   <li>"关机" → params.put("on", false)</li>
     *   <li>"黑屏" → params.put("on", true)</li>
     *   <li>"恢复显示" → params.put("on", false)</li>
     * </ul>
     * 调用方可在返回后覆盖这些默认值。
     * </p>
     *
     * @param action 业务动作
     * @param params 指令参数（可变，此方法会往里写入默认值）
     */
    public void applyActionDefaults(String action, Map<String, Object> params) {
        if (action == null || params == null) {
            return;
        }
        switch (normalize(action)) {
            case ACTION_POWER_ON:
                params.putIfAbsent("on", true);
                break;
            case ACTION_POWER_OFF:
                params.putIfAbsent("on", false);
                break;
            case ACTION_SCREEN_BLACKOUT:
                params.putIfAbsent("on", true);
                break;
            case ACTION_SCREEN_ON:
                params.putIfAbsent("on", false);
                break;
            default:
                break;
        }
    }

    /**
     * 获取所有已注册的业务动作名称（供文档生成或接口查询使用）。
     */
    public Set<String> listActions() {
        return ACTION_MAP.keySet();
    }

    private String normalize(String action) {
        String trimmed = action.trim();
        String canonical = ACTION_ALIASES.get(trimmed);
        return canonical != null ? canonical : trimmed;
    }
}
