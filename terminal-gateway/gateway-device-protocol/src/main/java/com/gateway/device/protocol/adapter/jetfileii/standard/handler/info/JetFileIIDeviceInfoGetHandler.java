package com.gateway.device.protocol.adapter.jetfileii.standard.handler.info;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractSimpleJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.sys.ConfigSysFile;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class JetFileIIDeviceInfoGetHandler extends AbstractSimpleJetFileIIHandler<EmptyParams> {

    public JetFileIIDeviceInfoGetHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.DEVICE_INFO_GET;
    }

    @Override
    protected JetFileIIRequest buildRequest(DeviceContext device, EmptyParams params) {
        int gg = JetFileIIMessaging.resolveGg(device);
        int uu = JetFileIIMessaging.resolveUu(device);
        return JetFileIIRequest.builder()
                .mainCmd(MainCmd.READ).subCmd(SubCmd.READ_SYSINFO)
                .destGg(gg).destUu(uu)
                .needReply(true).build();
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        JetFileIIRequest req = buildRequest(device, params);
        Duration timeout = req.isNeedReply() ? getTimeout() : Duration.ofSeconds(2);
        CommandResult result = messaging().executeSimple(transport(), device, req, timeout);

        if (!result.isSuccess() || !(result.getData() instanceof byte[])) {
            return result;
        }

        byte[] rawBytes = (byte[]) result.getData();
        try {
            String rawText = new String(rawBytes, ProtocolConstant.GB18030);
            ConfigSysFile.Info info = ConfigSysFile.parseText(rawBytes);
            Map<String, Object> deviceInfo = assembleDeviceInfo(info, rawText);
            return CommandResult.builder()
                    .success(true)
                    .code(result.getCode())
                    .message(result.getMessage())
                    .data(deviceInfo)
                    .costMillis(result.getCostMillis())
                    .build();
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("dataType", "JETFILEII_SYSINFO");
            fallback.put("rawEncoding", "GB18030");
            fallback.put("rawText", new String(rawBytes, ProtocolConstant.GB18030));
            fallback.put("parseError", e.getMessage());
            return CommandResult.builder()
                    .success(true)
                    .code(result.getCode())
                    .message(result.getMessage())
                    .data(fallback)
                    .costMillis(result.getCostMillis())
                    .build();
        }
    }

    private Map<String, Object> assembleDeviceInfo(ConfigSysFile.Info info, String rawText) {
        Map<String, Object> deviceInfo = new LinkedHashMap<>();
        deviceInfo.put("dataType", "JETFILEII_SYSINFO");
        deviceInfo.put("rawEncoding", "GB18030");

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("macAddr", info.macAddr);
        identity.put("serialNo", info.serialNo);
        identity.put("ipAddr", info.ipAddr);
        identity.put("groupAddr", info.groupAddr);
        identity.put("unitAddr", info.unitAddr);
        deviceInfo.put("identity", identity);

        Map<String, Object> version = new LinkedHashMap<>();
        version.put("softVer", info.softVer);
        version.put("softSecVer", info.softSecVer);
        version.put("hardVer", info.hardVer);
        version.put("fpgaVersion", info.fpgaVersion);
        version.put("protocolVersion", info.protocolVersion);
        version.put("updateVersion", info.updateVersion);
        version.put("updateBuildDate", info.updateBuildDate);
        version.put("updateHardware", info.updateHardware);
        deviceInfo.put("version", version);

        Map<String, Object> display = new LinkedHashMap<>();
        display.put("width", info.displayWidth);
        display.put("height", info.displayHeight);
        display.put("monitorStatus", info.monitorStatus);
        display.put("brightPercent1", info.brightPercent1);
        display.put("brightAdValue1", info.brightAdValue1);
        display.put("brightPercent2", info.brightPercent2);
        display.put("brightAdValue2", info.brightAdValue2);
        display.put("brightMinLimit", info.brightMinLimit);
        display.put("brightAdjustStart", info.brightAdjustStart);
        display.put("brightMaxLimit", info.brightMaxLimit);
        display.put("brightAdjust", info.brightAdjust);
        display.put("screenInput", info.screenInput);
        display.put("pixelBadPoints", info.pixelBadPoints);
        display.put("pixelSignalErrors", info.pixelSignalErrors);
        deviceInfo.put("display", display);

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("authorized", info.authorized);
        runtime.put("dateTime", info.dateTime);
        runtime.put("timezone", info.timezone);
        runtime.put("runningTime", info.runningTime);
        runtime.put("runningDays", info.runningDays);
        runtime.put("door", info.door);
        runtime.put("port1", info.port1);
        runtime.put("alarm", info.alarm);
        runtime.put("bat1", info.bat1);
        runtime.put("bat2", info.bat2);
        deviceInfo.put("runtime", runtime);

        Map<String, Object> communication = new LinkedHashMap<>();
        communication.put("baudRate1", info.baudRate1);
        communication.put("baudRate2", info.baudRate2);
        communication.put("csq", info.csq);
        communication.put("temperatureInC", info.temperatureInC);
        communication.put("temperatureInF", info.temperatureInF);
        communication.put("temperatureOutC", info.temperatureOutC);
        communication.put("temperatureOutF", info.temperatureOutF);
        communication.put("humidityOut", info.humidityOut);
        communication.put("humidityIn", info.humidityIn);
        deviceInfo.put("communication", communication);

        Map<String, Object> power = new LinkedHashMap<>();
        power.put("batteryCommuStatus", info.batteryCommuStatus);
        power.put("batteryVoltage", info.batteryVoltage);
        power.put("solarChargerVoltage", info.solarChargerVoltage);
        power.put("nowChargeCurrent", info.nowChargeCurrent);
        power.put("nowDischargeCurrent", info.nowDischargeCurrent);
        power.put("nowSolarDischargeCurrent", info.nowSolarDischargeCurrent);
        power.put("nowCharge", info.nowCharge);
        power.put("nowDischarge", info.nowDischarge);
        power.put("totalChargeToday", info.totalChargeToday);
        power.put("totalDischargeToday", info.totalDischargeToday);
        power.put("maxChargeCurrent", info.maxChargeCurrent);
        deviceInfo.put("power", power);

        deviceInfo.put("rawText", rawText);
        return deviceInfo;
    }
}
