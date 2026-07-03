package com.gateway.standardization.service.impl;

import com.gateway.standardization.service.CapabilityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 业务动作映射器实现
 * <p>
 * 将协议/内容/命令组合映射为<b>中文业务动作名称</b>（action），
 * 作为对外契约字段供 Sigma 团队和管控平台理解。
 * 解密网关内部通过 ActionMapper 将 action 翻译为 DeviceCapability，
 * 再根据目标设备厂商路由到青松、卡莱特等厂商适配器指令。
 * </p>
 *
 * <p>映射对照表（publish-gateway 输出 → terminal-gateway ActionMapper 接收）：
 * <pre>
 *   命令类:
 *     PLAYLIST / FILE_PLAY          → 设置播放列表
 *     BRIGHTNESS                    → 调节亮度
 *     SCREEN_BLACKOUT / BLACK_SCREEN→ 黑屏
 *     POWER_ON                      → 开机
 *     POWER_OFF                     → 关机
 *     REBOOT / RESTART              → 重启
 *     TIME_SYNC                     → 校时
 *     COLOR_TEST                    → 色彩测试
 *
 *   内容类:
 *     image                         → 上传图片
 *     video                         → 上传视频
 *     text + nmg 协议               → 上传NMG
 *     text + pmg 协议               → 上传PMG
 *     text + qst 协议               → 上传QST
 *     text (纯文本)                 → 上传文字
 *     binary + qst 协议             → 上传QST
 *     binary (默认)                 → 上传NMG
 * </pre>
 * </p>
 */
@Slf4j
@Service
public class CapabilityMapperImpl implements CapabilityMapper {

    // ── 业务动作常量（与 terminal-gateway ActionMapper 保持一致） ──
    private static final String ACTION_PLAYLIST_SET      = "设置播放列表";
    private static final String ACTION_BRIGHTNESS_SET    = "调节亮度";
    private static final String ACTION_SCREEN_BLACKOUT   = "黑屏";
    private static final String ACTION_SCREEN_ON         = "恢复显示";
    private static final String ACTION_POWER_ON          = "开机";
    private static final String ACTION_POWER_OFF         = "关机";
    private static final String ACTION_RESTART           = "重启";
    private static final String ACTION_TIME_SYNC         = "校时";
    private static final String ACTION_COLOR_TEST        = "色彩测试";
    private static final String ACTION_IMAGE_UPLOAD      = "上传图片";
    private static final String ACTION_VIDEO_UPLOAD      = "上传视频";
    private static final String ACTION_TEXT_UPLOAD       = "上传文字";
    private static final String ACTION_NMG_UPLOAD        = "上传NMG";
    private static final String ACTION_PMG_UPLOAD        = "上传PMG";
    private static final String ACTION_QST_UPLOAD        = "上传QST";

    @Override
    public String mapAction(String protocol, String contentType, String commandType) {
        // ── 优先按 commandType 映射（控制指令优先级最高） ──
        if (commandType != null) {
            String cmd = commandType.toUpperCase();

            if (cmd.contains("FILE_PLAY") || cmd.contains("PLAYLIST")) {
                return ACTION_PLAYLIST_SET;
            }
            if (cmd.contains("BRIGHTNESS")) {
                return ACTION_BRIGHTNESS_SET;
            }
            if (cmd.contains("SCREEN_BLACKOUT") || cmd.contains("BLACK_SCREEN")) {
                return ACTION_SCREEN_BLACKOUT;
            }
            if (cmd.contains("SCREEN_ON") || cmd.contains("DISPLAY_ON")) {
                return ACTION_SCREEN_ON;
            }
            if (cmd.contains("POWER_ON") || cmd.contains("TURN_ON")) {
                return ACTION_POWER_ON;
            }
            if (cmd.contains("POWER_OFF") || cmd.contains("TURN_OFF") || cmd.contains("SHUTDOWN")) {
                return ACTION_POWER_OFF;
            }
            if (cmd.contains("REBOOT") || cmd.contains("RESTART") || cmd.contains("RESET")) {
                return ACTION_RESTART;
            }
            if (cmd.contains("POWER")) {
                // POWER 无明确 on/off 后缀时默认关机
                return ACTION_POWER_OFF;
            }
            if (cmd.contains("TIME_SYNC")) {
                return ACTION_TIME_SYNC;
            }
            if (cmd.contains("COLOR_TEST")) {
                return ACTION_COLOR_TEST;
            }
        }

        // ── 按 contentType 映射（内容上传） ──
        if (contentType != null) {
            switch (contentType.toLowerCase()) {
                case "image":
                    return ACTION_IMAGE_UPLOAD;
                case "video":
                    return ACTION_VIDEO_UPLOAD;
                case "text":
                    if (protocol != null) {
                        String proto = protocol.toLowerCase();
                        if (proto.contains("pmg")) {
                            return ACTION_PMG_UPLOAD;
                        }
                        if (proto.contains("nmg")) {
                            return ACTION_NMG_UPLOAD;
                        }
                        if (proto.contains("qst")) {
                            return ACTION_QST_UPLOAD;
                        }
                    }
                    return ACTION_TEXT_UPLOAD;
                case "binary":
                    if (protocol != null && protocol.toLowerCase().contains("qst")) {
                        return ACTION_QST_UPLOAD;
                    }
                    return ACTION_NMG_UPLOAD;
                default:
                    break;
            }
        }

        // ── 按协议兜底 ──
        if (protocol != null) {
            String proto = protocol.toLowerCase();
            if (proto.contains("nova")) {
                if (commandType != null) {
                    return ACTION_POWER_OFF;
                }
                return null;
            }
        }

        return null;
    }
}
