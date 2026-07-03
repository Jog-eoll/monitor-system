package com.gateway.device.protocol.base.novastar.viplexcore.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.base.novastar.viplexcore.font.NovaStarFontInfo;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.model.params.IpConfigParams;
import com.gateway.device.protocol.model.params.ScreenAttributeParams;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ViplexCore SDK JSON 协议构建器 —— 静态工具类，纯函数无状态。
 *
 * <p>所有 {@code nv*Async} 请求 JSON 由此统一构建，属于 base 层。</p>
 */
public final class ViplexCoreJsonBuilder {

    private static final String DEFAULT_IFACE = "eth0";
    private static final int DEFAULT_SCOPE_ID = -1;

    private ViplexCoreJsonBuilder() {
    }

    // ════════════════════════════════════════════════════════════
    // 通用
    // ════════════════════════════════════════════════════════════

    /**
     * 构建仅含 SN 的通用请求 JSON {@code {"sn":"xxx"}}
     */
    public static String buildSnJson(String sn) {
        return JsonCustomMapper.get().createObjectNode()
                .put("sn", sn).toString();
    }

    // ════════════════════════════════════════════════════════════
    // 以太网配置
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetEthernetInfoAsync} 请求 JSON
     */
    public static String buildEthernetInfoJson(String sn, IpConfigParams params, boolean useStaticIp) {
        ObjectMapper mapper = JsonCustomMapper.get();

        ObjectNode eth = mapper.createObjectNode();
        eth.put("scopeId", DEFAULT_SCOPE_ID);
        eth.put("name", DEFAULT_IFACE);
        eth.put("dhcp", !useStaticIp);

        if (useStaticIp) {
            eth.put("ip", params.getIp());
            if (params.getMask() != null) {
                eth.put("mask", params.getMask());
            }
            if (params.getGateway() != null) {
                eth.put("gateWay", params.getGateway());
            }
        }
        String d1 = params.getDns1(), d2 = params.getDns2();
        if (d1 != null || d2 != null) {
            ArrayNode dnsArr = mapper.createArrayNode();
            if (d1 != null) dnsArr.add(d1);
            if (d2 != null) dnsArr.add(d2);
            eth.set("dns", dnsArr);
        }

        ArrayNode ethernets = mapper.createArrayNode();
        ethernets.add(eth);

        ObjectNode taskInfo = mapper.createObjectNode();
        taskInfo.set("ethernets", ethernets);

        ObjectNode root = mapper.createObjectNode();
        root.put("sn", sn);
        root.set("taskInfo", taskInfo);
        return root.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 校时
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvCalibrateTimeAsync} 请求 JSON
     */
    public static String buildCalibrateTimeJson(String sn, ZonedDateTime zdt) {
        String currentTime = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));

        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode tzInfo = mapper.createObjectNode();
        tzInfo.put("utcTimeMillis", zdt.toInstant().toEpochMilli());
        tzInfo.put("timeZone", zdt.getZone().getId());
        tzInfo.put("gmt", "GMT" + zdt.getOffset().toString());
        tzInfo.put("isTimeOffsetEnable", false);
        tzInfo.put("beginTime", "");
        tzInfo.put("endTime", "");
        tzInfo.put("timeOffsetValue", 0);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.put("currentTime", currentTime);
        json.set("timeZoneInfo", tzInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // NTP 配置
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetNetTimingInfoAsync} 请求 JSON
     */
    public static String buildNtpConfigJson(String sn, String ntpServer) {
        ObjectMapper mapper = JsonCustomMapper.get();

        ObjectNode ntpData = mapper.createObjectNode();
        ntpData.put("enable", true);
        ntpData.put("server", ntpServer);

        ObjectNode ntpTask = mapper.createObjectNode();
        ntpTask.put("type", "NTP_CONFIG");
        ntpTask.put("action", 4);
        ntpTask.set("data", ntpData);

        ObjectNode source = mapper.createObjectNode();
        source.put("type", 1);
        source.put("platform", 1);

        ObjectNode timingInfo = mapper.createObjectNode();
        timingInfo.set("source", source);
        timingInfo.putArray("taskArray").add(ntpTask);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("TimingInfo", timingInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 自定义分辨率
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetCustomResolutionAsync} 请求 JSON
     */
    public static String buildCustomResolutionJson(String sn, int displayMode, int width, int height) {
        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode info = mapper.createObjectNode();
        info.put("displayMode", displayMode);
        info.put("width", width);
        info.put("height", height);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("info", info);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 配屏（点阵像素）
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetScreenAttributeAsync} 请求 JSON
     */
    public static String buildScreenAttributeJson(String sn, ScreenAttributeParams params) {
        ObjectMapper mapper = JsonCustomMapper.get();

        ObjectNode scanInfo = mapper.createObjectNode();
        scanInfo.put("width", params.getWidth());
        scanInfo.put("height", params.getHeight());
        scanInfo.put("x", 0);
        scanInfo.put("y", 0);
        scanInfo.put("xInPort", 0);
        scanInfo.put("yInPort", 0);
        scanInfo.put("portIndex", 0);
        scanInfo.put("connectIndex", 0);

        ArrayNode scanInfos = mapper.createArrayNode();
        scanInfos.add(scanInfo);

        ArrayNode orders = mapper.createArrayNode();
        for (Integer o : params.getOrders()) {
            orders.add(o);
        }

        ObjectNode screenAttr = mapper.createObjectNode();
        screenAttr.put("id", params.getId());
        screenAttr.put("screenSource", params.getScreenSource());
        screenAttr.put("xCount", params.getXCount());
        screenAttr.put("yCount", params.getYCount());
        screenAttr.put("xOffset", params.getXOffset());
        screenAttr.put("yOffset", params.getYOffset());
        screenAttr.put("portNumber", params.getPortNumber());
        screenAttr.set("orders", orders);
        screenAttr.set("scanInfos", scanInfos);

        ArrayNode screenAttributes = mapper.createArrayNode();
        screenAttributes.add(screenAttr);

        ObjectNode screenAttribute = mapper.createObjectNode();
        screenAttribute.set("screenAttributes", screenAttributes);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("screenAttribute", screenAttribute);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 屏体电源
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetScreenPowerStateAsync} 请求 JSON
     */
    public static String buildScreenPowerJson(String sn, String state) {
        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode taskInfo = mapper.createObjectNode();
        taskInfo.put("state", state);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("taskInfo", taskInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 音量
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetVolumeAsync} 请求 JSON
     */
    public static String buildVolumeJson(String sn, double ratio) {
        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode volumeInfo = mapper.createObjectNode();
        volumeInfo.put("ratio", ratio);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("volumeInfo", volumeInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 亮度
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvGetScreenBrightnessAsync} 请求 JSON
     */
    public static String buildBrightnessGetJson(String sn) {
        return buildSnJson(sn);
    }

    /**
     * 构建 {@code nvSetScreenBrightnessAsync} 请求 JSON
     */
    public static String buildBrightnessSetJson(String sn, double ratio) {
        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode brightnessInfo = mapper.createObjectNode();
        brightnessInfo.put("ratio", ratio);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("screenBrightnessInfo", brightnessInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 重启
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvSetReBootTaskAsync} 请求 JSON
     */
    public static String buildRebootJson(String sn) {
        ObjectMapper mapper = JsonCustomMapper.get();

        ObjectNode source = mapper.createObjectNode();
        source.put("type", 0);
        source.put("platform", 2);

        ObjectNode taskInfo = mapper.createObjectNode();
        taskInfo.put("type", "REBOOT");
        taskInfo.set("source", source);
        taskInfo.put("executionType", "IMMEDIATELY");
        taskInfo.put("reason", "gateway command");

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.set("taskInfo", taskInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // 字体管理
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvGetTerminalFontAsync} 请求 JSON（仅 SN）
     */
    public static String buildFontGetJson(String sn) {
        return buildSnJson(sn);
    }

    /**
     * 构建 {@code nvUpdateFontAsync} 请求 JSON
     *
     * @param sn            设备序列号
     * @param localFontPath 本地 .ttf 字体目录（如 C:\Windows\Fonts）
     * @param fonts         待同步的字体列表
     */
    public static String buildFontUpdateJson(String sn, String localFontPath, java.util.List<NovaStarFontInfo> fonts) {
        ObjectMapper mapper = JsonCustomMapper.get();

        ArrayNode fontsArr = mapper.createArrayNode();
        for (NovaStarFontInfo f : fonts) {
            ObjectNode fn = mapper.createObjectNode();
            fn.put("name", f.getName());
            ArrayNode stylesArr = mapper.createArrayNode();
            for (String s : f.getStyles()) {
                stylesArr.add(s);
            }
            fn.set("style", stylesArr);
            ArrayNode filesArr = mapper.createArrayNode();
            for (String file : f.getFiles()) {
                filesArr.add(file);
            }
            fn.set("file", filesArr);
            fontsArr.add(fn);
        }

        ObjectNode taskInfo = mapper.createObjectNode();
        taskInfo.set("fonts", fontsArr);

        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.put("localFontPath", localFontPath != null ? localFontPath : "");
        json.set("taskInfo", taskInfo);
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════
    // AP 热点开关
    // ════════════════════════════════════════════════════════════

    /**
     * 构建 {@code nvGetAPNetworkOpenStatusAsync} 请求 JSON
     */
    public static String buildApStatusGetJson(String sn) {
        return buildSnJson(sn);
    }

    /**
     * 构建 {@code nvSetAPNetworkOpenStatusAsync} 请求 JSON
     */
    public static String buildApSwitchJson(String sn, boolean enable) {
        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode json = mapper.createObjectNode();
        json.put("sn", sn);
        json.put("enable", enable);
        return json.toString();
    }
}
