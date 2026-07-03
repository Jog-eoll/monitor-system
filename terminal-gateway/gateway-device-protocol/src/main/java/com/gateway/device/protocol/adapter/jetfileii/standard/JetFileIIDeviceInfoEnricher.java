package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.api.DeviceInfoEnricher;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.base.jetfileii.standard.sys.ConfigSysFile;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * JetFileII 设备信息增强 —— 从 CONFIG.SYS 解析设备全量属性。
 *
 * <p>ConfigSysFile.Info 解析 44 个字段，本类将其全部有用字段映射到 attrs，
 * 包括版本、屏幕状态、传感器、运行状态、电源等维度。</p>
 */
@Slf4j
public class JetFileIIDeviceInfoEnricher implements DeviceInfoEnricher {

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.JET_FILE_II_STANDARD;
    }

    @Override
    public void enrich(DiscoveredDevice dd, byte[] infoData, Map<String, Object> attrs) {
        if (infoData == null) return;
        try {
            ConfigSysFile.Info cfg = ConfigSysFile.parseText(infoData);

            // ── 设备标识 ──
            if (StringUtils.isNotEmpty(cfg.serialNo))
                attrs.put("serialNo", cfg.serialNo);
            if (StringUtils.isNotEmpty(cfg.macAddr))
                attrs.put("macAddr", ProtocolConstant.formatMac(cfg.macAddr));

            // ── 屏幕 ──
            if (cfg.screenWidth > 0) attrs.put("width", cfg.screenWidth);
            if (cfg.screenHeight > 0) attrs.put("height", cfg.screenHeight);

            // ── 版本（CONFIG.SYS 为权威来源，覆盖广播阶段的字段）──
            attrs.put("cpuVersion", cfg.softVer);
            if (cfg.softSecVer > 0) attrs.put("softSecVer", cfg.softSecVer);
            if (cfg.hardVer > 0) attrs.put("hardVer", cfg.hardVer);
            if (StringUtils.isNotEmpty(cfg.fpgaVersion))
                attrs.put("fpgaVersion", cfg.fpgaVersion);
            if (StringUtils.isNotEmpty(cfg.protocolVersion))
                attrs.put("protocolVersion", cfg.protocolVersion);
            if (StringUtils.isNotEmpty(cfg.updateVersion))
                attrs.put("updateVersion", cfg.updateVersion);
            if (StringUtils.isNotEmpty(cfg.updateBuildDate))
                attrs.put("updateBuildDate", cfg.updateBuildDate);
            if (StringUtils.isNotEmpty(cfg.updateHardware))
                attrs.put("updateHardware", cfg.updateHardware);

            // ── 屏幕/显示状态 ──
            if (StringUtils.isNotEmpty(cfg.monitorStatus))
                attrs.put("monitorStatus", cfg.monitorStatus);
            if (cfg.brightPercent1 > 0) attrs.put("brightPercent1", cfg.brightPercent1);
            if (cfg.brightPercent2 > 0) attrs.put("brightPercent2", cfg.brightPercent2);
            if (StringUtils.isNotEmpty(cfg.brightAdjust))
                attrs.put("brightAdjust", cfg.brightAdjust);
            if (cfg.pixelBadPoints > 0 || cfg.pixelSignalErrors > 0) {
                attrs.put("pixelBadPoints", cfg.pixelBadPoints);
                attrs.put("pixelSignalErrors", cfg.pixelSignalErrors);
            }

            // ── 通信 ──
            if (cfg.baudRate1 > 0) attrs.put("baudRate1", cfg.baudRate1);
            if (cfg.baudRate2 > 0) attrs.put("baudRate2", cfg.baudRate2);
            attrs.put("csq", cfg.csq);

            // ── 传感器 ──
            if (cfg.temperatureInC > 0) attrs.put("temperatureInC", cfg.temperatureInC);
            if (cfg.temperatureInF > 0) attrs.put("temperatureInF", cfg.temperatureInF);
            if (StringUtils.isNotEmpty(cfg.humidityIn) && !"NA".equals(cfg.humidityIn))
                attrs.put("humidityIn", cfg.humidityIn);
            if (StringUtils.isNotEmpty(cfg.humidityOut) && !"NA".equals(cfg.humidityOut))
                attrs.put("humidityOut", cfg.humidityOut);

            // ── 运行状态 ──
            if (StringUtils.isNotEmpty(cfg.authorized))
                attrs.put("authorized", cfg.authorized);
            if (StringUtils.isNotEmpty(cfg.dateTime))
                attrs.put("dateTime", cfg.dateTime);
            if (StringUtils.isNotEmpty(cfg.timezone))
                attrs.put("timezone", cfg.timezone);
            if (StringUtils.isNotEmpty(cfg.runningTime))
                attrs.put("runningTime", cfg.runningTime);
            if (cfg.runningDays > 0) attrs.put("runningDays", cfg.runningDays);
            if (StringUtils.isNotEmpty(cfg.door))
                attrs.put("door", cfg.door);
            if (StringUtils.isNotEmpty(cfg.alarm))
                attrs.put("alarm", cfg.alarm);

            // ── 电源（过滤全零默认值）──
            if (StringUtils.isNotEmpty(cfg.batteryVoltage) && !"0".equals(cfg.batteryVoltage))
                attrs.put("batteryVoltage", cfg.batteryVoltage);
            if (StringUtils.isNotEmpty(cfg.solarChargerVoltage) && !"0".equals(cfg.solarChargerVoltage))
                attrs.put("solarChargerVoltage", cfg.solarChargerVoltage);
            if (StringUtils.isNotEmpty(cfg.nowChargeCurrent) && !"0".equals(cfg.nowChargeCurrent))
                attrs.put("nowChargeCurrent", cfg.nowChargeCurrent);
            if (StringUtils.isNotEmpty(cfg.nowDischargeCurrent) && !"0".equals(cfg.nowDischargeCurrent))
                attrs.put("nowDischargeCurrent", cfg.nowDischargeCurrent);

            log.debug("[{}] CONFIG.SYS 映射完成: sn={}, mac={}, hardVer={}, fpga={}, protocol={}, "
                            + "monitor={}, bright={}%/{}%, csq={}, temp={}°C, authorized={}, "
                            + "dateTime={}, runningTime={}, door={}",
                    dd.getIp(), cfg.serialNo,
                    cfg.macAddr != null ? ProtocolConstant.formatMac(cfg.macAddr) : null,
                    cfg.hardVer, cfg.fpgaVersion, cfg.protocolVersion,
                    cfg.monitorStatus, cfg.brightPercent1, cfg.brightPercent2,
                    cfg.csq, cfg.temperatureInC, cfg.authorized,
                    cfg.dateTime, cfg.runningTime, cfg.door);
        } catch (Exception ignored) {
        }
    }
}
