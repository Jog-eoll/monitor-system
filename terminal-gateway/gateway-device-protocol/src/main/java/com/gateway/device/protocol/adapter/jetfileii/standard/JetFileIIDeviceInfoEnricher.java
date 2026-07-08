package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.api.DeviceInfoEnricher;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.helper.ScreenAttributeHelper;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.base.jetfileii.standard.sys.ConfigSysFile;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JetFileII 设备信息增强 —— 从 CONFIG.SYS 解析设备全量属性，并通过 MULTI_WIN 获取点阵真实宽高。
 *
 * <p>ConfigSysFile.Info 解析 44 个字段，本类将其全部有用字段映射到 attrs。
 * 屏幕宽高不再使用 "Display Size"，改为 READ_MULTI_WIN 查询真实点阵像素尺寸。</p>
 */
@Slf4j
public class JetFileIIDeviceInfoEnricher implements DeviceInfoEnricher {

    private final JetFileIIMessaging messaging;
    private final DeviceTransport transport;

    public JetFileIIDeviceInfoEnricher(JetFileIIMessaging messaging, DeviceTransport transport) {
        this.messaging = messaging;
        this.transport = transport;
    }

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

            // ── 屏幕宽高（MULTI_WIN 查询真实点阵像素）──
            fetchScreenSize(dd, attrs);
            // CONFIG.SYS "Display Size" 保留写入 displayWidth/displayHeight，供参考
            if (cfg.displayWidth > 0) attrs.put("displayWidth", cfg.displayWidth);
            if (cfg.displayHeight > 0) attrs.put("displayHeight", cfg.displayHeight);

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

    // ════════════════════════════════════════════════════
    // MULTI_WIN 查询
    // ════════════════════════════════════════════════════

    /**
     * 通过 READ_MULTI_WIN 查询真实点阵像素宽高，写入 attrs。
     * 查询失败时静默跳过，不阻塞注册流程。
     */
    private void fetchScreenSize(DiscoveredDevice dd, Map<String, Object> attrs) {
        try {
            Map<String, Object> ddAttrs = dd.getAttributes();
            int gg = MapUtils.getInteger(ddAttrs, "gg", ScreenAttributeHelper.DEFAULT_WINDOW_INDEX);
            int uu = MapUtils.getInteger(ddAttrs, "uu", ScreenAttributeHelper.DEFAULT_WINDOW_INDEX);

            DeviceContext tempCtx = DeviceContext.builder()
                    .ip(dd.getIp())
                    .port(dd.getSourcePort())
                    .vendor(DeviceVendor.JET_FILE_II_STANDARD)
                    .transportType(TransportType.UDP)
                    .attributes(ddAttrs)
                    .build();

            JetFileIIRequest req = ScreenAttributeHelper.buildReadRequest(gg, uu);
            byte[] payload = messaging.encode(req);
            byte[] resp = transport.sendAndReceive(tempCtx, payload, Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS))
                    .get(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS, TimeUnit.MILLISECONDS);
            PacketMessage pkt = messaging.decode(resp);

            byte[] data = pkt != null ? pkt.getData() : null;
            if (ScreenAttributeHelper.isValidData(data)) {
                int w = ScreenAttributeHelper.readWidth(data);
                int h = ScreenAttributeHelper.readHeight(data);
                attrs.put("width", w);
                attrs.put("height", h);
                log.debug("[{}] MULTI_WIN 点阵宽高: {}x{}", dd.getIp(), w, h);
            }
        } catch (Exception e) {
            log.warn("[{}] MULTI_WIN 查询失败，width/height 保持为空: {}", dd.getIp(), e.getMessage());
            log.error(e.getMessage(), e);
        }
    }
}
