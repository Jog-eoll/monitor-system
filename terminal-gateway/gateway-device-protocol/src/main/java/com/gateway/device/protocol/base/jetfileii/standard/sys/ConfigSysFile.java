package com.gateway.device.protocol.base.jetfileii.standard.sys;

import com.gateway.device.protocol.common.constant.ProtocolConstant;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备信息模型 —— 从 READ_SYSINFO (0x12) 文本响应解析字段。
 *
 * <p>文本为 GB18030 编码，\r\n 分隔的 Key:Value 对。复合值行自动拆分为独立字段。</p>
 */
public final class ConfigSysFile {

    private ConfigSysFile() {
    }

    /**
     * 从 READ_SYSINFO 文本 data 段解析设备信息
     */
    public static Info parseText(byte[] data) {
        Info c = new Info();
        String text = new String(data, ProtocolConstant.GB18030);
        for (String line : text.split("\\r\\n")) {
            int sep = line.indexOf(':');
            if (sep < 0) continue;
            String key = line.substring(0, sep).trim();
            String val = line.substring(sep + 1).trim();
            if (!val.isEmpty() && val.charAt(0) < 0x20) {
                val = val.substring(1);
            }
            switch (key) {
                // ── 设备标识 ──
                case "MAC":
                    c.macAddr = val;
                    break;
                case "Serial No":
                    c.serialNo = val;
                    break;
                case "IP Address":
                    c.ipAddr = val;
                    break;
                case "GGUU":
                    if (val.length() >= 4) {
                        c.groupAddr = Integer.parseInt(val.substring(0, 2));
                        c.unitAddr = Integer.parseInt(val.substring(2, 4));
                    }
                    break;
                // ── 版本 ──
                case "Firmware Version":
                    parseFirmwareVersion(val, c);
                    break;
                case "Hardware Version":
                    c.hardVer = Integer.parseInt(val);
                    break;
                case "FPGA Version":
                    c.fpgaVersion = val;
                    break;
                case "Protocol Version":
                    c.protocolVersion = val;
                    break;
                case "UpdatePacket":
                    parseUpdatePacket(val, c);
                    break;
                // ── 屏幕 ──
                case "Display Size":
                    parseDisplaySize(val, c);
                    break;
                case "Monitor Status":
                    c.monitorStatus = val;
                    break;
                case "Bright Schedule":
                    parseBrightSchedule(val, c, 1);
                    break;
                case "Bright Schedule2":
                    parseBrightSchedule(val, c, 2);
                    break;
                case "Bright Min Limit":
                    parseBrightLimits(val, c);
                    break;
                case "Bright Adjust":
                    c.brightAdjust = val;
                    break;
                case "Screen Input":
                    c.screenInput = val;
                    break;
                case "Pixel Check Result":
                    parsePixelCheckResult(val, c);
                    break;
                // ── 通信 ──
                case "Baud Rate1":
                    c.baudRate1 = Integer.parseInt(val);
                    break;
                case "Baud Rate2":
                    c.baudRate2 = Integer.parseInt(val);
                    break;
                case "CSQ":
                    c.csq = Integer.parseInt(val);
                    break;
                // ── 传感器 ──
                case "Temperature(IN)":
                    parseTemperature(val, c, true);
                    break;
                case "Temperature(Out)":
                    parseTemperature(val, c, false);
                    break;
                case "Humidity(Out)":
                    c.humidityOut = val;
                    break;
                case "Humidity(In)":
                    c.humidityIn = val;
                    break;
                // ── 状态 ──
                case "Authorized":
                    c.authorized = val;
                    break;
                case "Date Time":
                    parseDateTime(val, c);
                    break;
                case "Running Time":
                    parseRunningTime(val, c);
                    break;
                case "Door":
                    c.door = val;
                    break;
                case "Port1":
                    c.port1 = val;
                    break;
                case "Alarm":
                    c.alarm = val;
                    break;
                case "BAT1":
                    c.bat1 = val;
                    break;
                case "BAT2":
                    c.bat2 = val;
                    break;
                // ── 电源 ──
                case "Battery Commu Status":
                    c.batteryCommuStatus = val;
                    break;
                case "Battery Voltage(mV)":
                    c.batteryVoltage = val;
                    break;
                case "SolarCharger Voltage(mV)":
                    c.solarChargerVoltage = val;
                    break;
                case "Now Charge Current(mA)":
                    c.nowChargeCurrent = val;
                    break;
                case "Now Discharge Current(mA)":
                    c.nowDischargeCurrent = val;
                    break;
                case "Now Solar Discharge Current(mA)":
                    c.nowSolarDischargeCurrent = val;
                    break;
                case "Now Charge(mAH)":
                    c.nowCharge = val;
                    break;
                case "Now Discharge(mAH)":
                    c.nowDischarge = val;
                    break;
                case "Total Charge Today(mAH)":
                    c.totalChargeToday = val;
                    break;
                case "Total Discharge Today(mAH)":
                    c.totalDischargeToday = val;
                    break;
                case "Max. Charge Current(mA)":
                    c.maxChargeCurrent = val;
                    break;
            }
        }
        return c;
    }

    // ═══════════════════════════════════════════════════
    // 复合值解析
    // ═══════════════════════════════════════════════════

    /**
     * "[A900.28][Apr 27 2026][14:53:52]" → softVer, softSecVer
     */
    private static void parseFirmwareVersion(String raw, Info c) {
        if (raw.startsWith("[")) {
            int end = raw.indexOf(']');
            if (end > 1) {
                String ver = raw.substring(1, end);
                int dot = ver.indexOf('.');
                if (dot > 0) {
                    c.softVer = Integer.parseInt(ver.substring(0, dot), 16);
                    c.softSecVer = Integer.parseInt(ver.substring(dot + 1));
                }
            }
        }
    }

    /**
     * "64x64" → screenWidth, screenHeight
     */
    private static void parseDisplaySize(String val, Info c) {
        int x = val.indexOf('x');
        if (x > 0) {
            c.screenWidth = Integer.parseInt(val.substring(0, x));
            c.screenHeight = Integer.parseInt(val.substring(x + 1));
        }
    }

    /**
     * "80% - AD Value:65" → brightPercent, brightAdValue; "80% - AD Value2:NA" → brightAdValue2="NA"
     */
    private static void parseBrightSchedule(String val, Info c, int idx) {
        Matcher m = Pattern.compile("(\\d+)%\\s*-\\s*AD Value[2]?:\\s*(.+)").matcher(val);
        if (m.matches()) {
            int pct = Integer.parseInt(m.group(1));
            String ad = m.group(2);
            if (idx == 1) {
                c.brightPercent1 = pct;
                c.brightAdValue1 = "NA".equals(ad) ? -1 : Integer.parseInt(ad);
            } else {
                c.brightPercent2 = pct;
                c.brightAdValue2 = ad;
            }
        }
    }

    /**
     * "1%, Bright Adjust Start: 1%, Bright Max Limit: 100%" → 三个 int
     */
    private static void parseBrightLimits(String val, Info c) {
        Matcher m = Pattern.compile("(\\d+)%.*?(\\d+)%.*?(\\d+)%").matcher(val);
        if (m.find()) {
            c.brightMinLimit = Integer.parseInt(m.group(1));
            c.brightAdjustStart = Integer.parseInt(m.group(2));
            c.brightMaxLimit = Integer.parseInt(m.group(3));
        }
    }

    /**
     * "0 Bad Points, 0 Signal Errors" → pixelBadPoints, pixelSignalErrors
     */
    private static void parsePixelCheckResult(String val, Info c) {
        Matcher m = Pattern.compile("(\\d+)\\s*Bad\\s*Points?\\s*,?\\s*(\\d+)\\s*Signal\\s*Errors?").matcher(val);
        if (m.find()) {
            c.pixelBadPoints = Integer.parseInt(m.group(1));
            c.pixelSignalErrors = Integer.parseInt(m.group(2));
        }
    }

    /**
     * "40(C)/104(F)" → temperatureInC/F or temperatureOutC/F; "NA" → 保持默认
     */
    private static void parseTemperature(String val, Info c, boolean isIn) {
        if ("NA".equals(val)) return;
        Matcher m = Pattern.compile("(\\d+)\\(C\\)/(\\d+)\\(F\\)").matcher(val);
        if (m.matches()) {
            if (isIn) {
                c.temperatureInC = Integer.parseInt(m.group(1));
                c.temperatureInF = Integer.parseInt(m.group(2));
            } else {
                c.temperatureOutC = Integer.parseInt(m.group(1));
                c.temperatureOutF = Integer.parseInt(m.group(2));
            }
        }
    }

    /**
     * "[2026-06-17 09:12:47] GMT+00:00" → dateTime, timezone
     */
    private static void parseDateTime(String val, Info c) {
        Matcher m = Pattern.compile("\\[([^\\]]+)]\\s*(.+)").matcher(val);
        if (m.matches()) {
            c.dateTime = m.group(1);
            c.timezone = m.group(2);
        }
    }

    /**
     * "07:14:23 up 0 day" → runningTime, runningDays
     */
    private static void parseRunningTime(String val, Info c) {
        Matcher m = Pattern.compile("(\\d{2}:\\d{2}:\\d{2})\\s*up\\s*(\\d+)\\s*day").matcher(val);
        if (m.find()) {
            c.runningTime = m.group(1);
            c.runningDays = Integer.parseInt(m.group(2));
        }
    }

    /**
     * "Version: 1.04.52 BuildDate:2026-04-27(PF9246MAGE)" → updateVersion, updateBuildDate, updateHardware
     */
    private static void parseUpdatePacket(String val, Info c) {
        Matcher m = Pattern.compile("Version:\\s*(\\S+)\\s*BuildDate:\\s*(\\S+)\\((.+)\\)").matcher(val);
        if (m.matches()) {
            c.updateVersion = m.group(1);
            c.updateBuildDate = m.group(2);
            c.updateHardware = m.group(3);
        }
    }

    // ═══════════════════════════════════════════════════
    // 模型
    // ═══════════════════════════════════════════════════

    /**
     * 设备信息数据
     */
    public static class Info {
        // ── 设备标识 ──
        public String macAddr;
        public String serialNo;
        public String ipAddr;
        public int groupAddr;
        public int unitAddr;

        // ── 版本 ──
        public int softVer;            // CPU型号 hex (0xA900)
        public int softSecVer;         // 次版本 (28)
        public int hardVer;            // 硬件版本 (9020)
        public String fpgaVersion;     // "F923"
        public String protocolVersion; // "2807"
        public String updateVersion;   // "1.04.52"
        public String updateBuildDate; // "2026-04-27"
        public String updateHardware;  // "PF9246MAGE"

        // ── 屏幕与显示 ──
        public int screenWidth;
        public int screenHeight;
        public String monitorStatus;   // "Play Mode"
        public int brightPercent1;     // 亮度1 百分比 (80)
        public int brightAdValue1;     // 亮度1 AD值 (65), -1=NA
        public int brightPercent2;     // 亮度2 百分比 (80)
        public String brightAdValue2;  // 亮度2 AD值 ("NA"或数值)
        public int brightMinLimit;     // 亮度下限 (1)
        public int brightAdjustStart;  // 亮度调节起点 (1)
        public int brightMaxLimit;     // 亮度上限 (100)
        public String brightAdjust;    // "OFF"/"ON"
        public String screenInput;     // "0x0c@0"
        public int pixelBadPoints;     // 坏点数 (0)
        public int pixelSignalErrors;  // 信号错误数 (0)

        // ── 通信 ──
        public int baudRate1;
        public int baudRate2;
        public int csq;                // 信号质量

        // ── 传感器 ──
        public int temperatureInC;     // 内部温度(℃)
        public int temperatureInF;     // 内部温度(℉)
        public int temperatureOutC;    // 外部温度(℃), 0=NA
        public int temperatureOutF;    // 外部温度(℉), 0=NA
        public String humidityOut;     // 外部湿度
        public String humidityIn;      // 内部湿度

        // ── 运行状态 ──
        public String authorized;      // "True"/"False"
        public String dateTime;        // "2026-06-17 09:12:47"
        public String timezone;        // "GMT+00:00"
        public String runningTime;     // "07:14:23"
        public int runningDays;        // 运行天数 (0)
        public String door;            // "Close"/"Open"
        public String port1;           // "Ok"
        public String alarm;           // "Unknow"
        public String bat1;            // "NONE"
        public String bat2;            // "NONE"

        // ── 电源 ──
        public String batteryCommuStatus;
        public String batteryVoltage;
        public String solarChargerVoltage;
        public String nowChargeCurrent;
        public String nowDischargeCurrent;
        public String nowSolarDischargeCurrent;
        public String nowCharge;
        public String nowDischarge;
        public String totalChargeToday;
        public String totalDischargeToday;
        public String maxChargeCurrent;
    }
}
