package com.gateway.device.protocol.base.novastar.viplexcore.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gateway.device.protocol.base.novastar.viplexcore.font.NovaStarFontInfo;
import com.gateway.device.protocol.base.novastar.viplexcore.model.request.*;
import com.gateway.device.protocol.common.DateTimeFormatUtils;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.model.params.IpConfigParams;
import com.gateway.device.protocol.model.params.ScreenAttributeParams;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ViplexCore SDK JSON 协议构建器 —— 静态工具类，纯函数无状态。
 *
 * <p>所有 {@code nv*Async} 请求 JSON 由此统一构建，属于 base 层。
 * 内部通过 POJO + Jackson 序列化生成 JSON，不再手动拼接 ObjectNode。</p>
 */
public final class ViplexCoreJsonBuilder {

    private ViplexCoreJsonBuilder() {
    }

    // ════════════════════════════════════════════════════════════
    // 内部工具
    // ════════════════════════════════════════════════════════════

    /**
     * 将 POJO 序列化为 JSON 字符串
     */
    private static String toJson(Object obj) {
        try {
            return JsonCustomMapper.get().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    // ════════════════════════════════════════════════════════════
    // 通用
    // ════════════════════════════════════════════════════════════

    /**
     * 构建仅含 SN 的通用请求 JSON {@code {"sn":"xxx"}}
     */
    public static String buildSnJson(String sn) {
        return toJson(SnRequest.builder().sn(sn).build());
    }

    // ════════════════════════════════════════════════════════════
    // 以太网配置
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetEthernetInfoAsync} 请求 JSON
     */
    public static String buildEthernetInfoJson(String sn, IpConfigParams params, boolean useStaticIp) {
        EthernetInfoRequest.Ethernet.EthernetBuilder ethBuilder = EthernetInfoRequest.Ethernet.builder()
                .dhcp(!useStaticIp);

        if (useStaticIp) {
            ethBuilder.ip(params.getIp());
            if (params.getMask() != null) {
                ethBuilder.mask(params.getMask());
            }
            if (params.getGateway() != null) {
                ethBuilder.gateWay(params.getGateway());
            }
        }

        String d1 = params.getDns1(), d2 = params.getDns2();
        if (d1 != null || d2 != null) {
            List<String> dns = new ArrayList<>();
            if (d1 != null) dns.add(d1);
            if (d2 != null) dns.add(d2);
            ethBuilder.dns(dns);
        }

        return toJson(EthernetInfoRequest.builder()
                .sn(sn)
                .taskInfo(EthernetInfoRequest.TaskInfo.builder()
                        .ethernets(Collections.singletonList(ethBuilder.build()))
                        .build())
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 校时
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvCalibrateTimeAsync} 请求 JSON
     */
    public static String buildCalibrateTimeJson(String sn, ZonedDateTime zdt) {
        String currentTime = DateTimeFormatUtils.TimeFormat(zdt, DateTimeFormatUtils.FMT.ISO_8601_OFFSET);

        CalibrateTimeRequest.TimeZoneInfo tzInfo = CalibrateTimeRequest.TimeZoneInfo.builder()
                .utcTimeMillis(zdt.toInstant().toEpochMilli())
                .timeZone(zdt.getZone().getId())
                .gmt("GMT" + zdt.getOffset().toString())
                .build();

        return toJson(CalibrateTimeRequest.builder()
                .sn(sn)
                .currentTime(currentTime)
                .timeZoneInfo(tzInfo)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // NTP 配置
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetNetTimingInfoAsync} 请求 JSON
     */
    public static String buildNtpConfigJson(String sn, String ntpServer) {
        NtpConfigRequest.NtpData ntpData = NtpConfigRequest.NtpData.builder()
                .server(ntpServer)
                .build();

        NtpConfigRequest.Task task = NtpConfigRequest.Task.builder()
                .data(ntpData)
                .build();

        NtpConfigRequest.Source source = NtpConfigRequest.Source.builder().build();

        NtpConfigRequest.TimingInfo timingInfo = NtpConfigRequest.TimingInfo.builder()
                .source(source)
                .taskArray(Collections.singletonList(task))
                .build();

        return toJson(NtpConfigRequest.builder()
                .sn(sn)
                .timingInfo(timingInfo)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 自定义分辨率
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetCustomResolutionAsync} 请求 JSON
     */
    public static String buildCustomResolutionJson(String sn, int displayMode, int width, int height) {
        CustomResolutionRequest.Info info = CustomResolutionRequest.Info.builder()
                .displayMode(displayMode)
                .width(width)
                .height(height)
                .build();

        return toJson(CustomResolutionRequest.builder()
                .sn(sn)
                .info(info)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 配屏（点阵像素）
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetScreenAttributeAsync} 请求 JSON
     */
    public static String buildScreenAttributeJson(String sn, ScreenAttributeParams params) {
        ScreenAttributeRequest.ScanInfo scanInfo = ScreenAttributeRequest.ScanInfo.builder()
                .width(params.getWidth())
                .height(params.getHeight())
                .build();

        ScreenAttributeRequest.ScreenAttr screenAttr = ScreenAttributeRequest.ScreenAttr.builder()
                .id(params.getId())
                .screenSource(params.getScreenSource())
                .xCount(params.getXCount())
                .yCount(params.getYCount())
                .xOffset(params.getXOffset())
                .yOffset(params.getYOffset())
                .portNumber(params.getPortNumber())
                .orders(params.getOrders())
                .scanInfos(Collections.singletonList(scanInfo))
                .build();

        ScreenAttributeRequest.ScreenAttributeWrapper wrapper =
                ScreenAttributeRequest.ScreenAttributeWrapper.builder()
                        .screenAttributes(Collections.singletonList(screenAttr))
                        .build();

        return toJson(ScreenAttributeRequest.builder()
                .sn(sn)
                .screenAttribute(wrapper)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 屏体电源
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetScreenPowerStateAsync} 请求 JSON
     */
    public static String buildScreenPowerJson(String sn, String state) {
        ScreenPowerRequest.TaskInfo taskInfo = ScreenPowerRequest.TaskInfo.builder()
                .state(state)
                .build();

        return toJson(ScreenPowerRequest.builder()
                .sn(sn)
                .taskInfo(taskInfo)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 音量
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetVolumeAsync} 请求 JSON
     */
    public static String buildVolumeJson(String sn, double ratio) {
        VolumeRequest.VolumeInfo volumeInfo = VolumeRequest.VolumeInfo.builder()
                .ratio(ratio)
                .build();

        return toJson(VolumeRequest.builder()
                .sn(sn)
                .volumeInfo(volumeInfo)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 亮度
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetScreenBrightnessAsync} 请求 JSON
     */
    public static String buildBrightnessSetJson(String sn, double ratio) {
        BrightnessSetRequest.ScreenBrightnessInfo info =
                BrightnessSetRequest.ScreenBrightnessInfo.builder()
                        .ratio(ratio)
                        .build();

        return toJson(BrightnessSetRequest.builder()
                .sn(sn)
                .screenBrightnessInfo(info)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 重启
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetReBootTaskAsync} 请求 JSON
     */
    public static String buildRebootJson(String sn) {
        RebootRequest.Source source = RebootRequest.Source.builder().build();
        RebootRequest.TaskInfo taskInfo = RebootRequest.TaskInfo.builder()
                .source(source)
                .build();

        return toJson(RebootRequest.builder()
                .sn(sn)
                .taskInfo(taskInfo)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // 字体管理
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvUpdateFontAsync} 请求 JSON
     *
     * @param sn            设备序列号
     * @param localFontPath 本地 .ttf 字体目录（如 C:\Windows\Fonts）
     * @param fonts         待同步的字体列表
     */
    public static String buildFontUpdateJson(String sn, String localFontPath, java.util.List<NovaStarFontInfo> fonts) {
        List<FontUpdateRequest.FontInfo> fontInfos = fonts.stream()
                .map(f -> FontUpdateRequest.FontInfo.builder()
                        .name(f.getName())
                        .styles(f.getStyles())
                        .files(f.getFiles())
                        .build())
                .collect(Collectors.toList());

        FontUpdateRequest.TaskInfo taskInfo = FontUpdateRequest.TaskInfo.builder()
                .fonts(fontInfos)
                .build();

        return toJson(FontUpdateRequest.builder()
                .sn(sn)
                .localFontPath(localFontPath != null ? localFontPath : "")
                .taskInfo(taskInfo)
                .build());
    }

    // ════════════════════════════════════════════════════════════
    // AP 热点开关
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetAPNetworkOpenStatusAsync} 请求 JSON
     */
    public static String buildApSwitchJson(String sn, boolean enable) {
        return toJson(ApSwitchRequest.builder()
                .sn(sn)
                .enable(enable)
                .build());
    }
}
