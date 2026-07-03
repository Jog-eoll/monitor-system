package com.gateway.device.protocol.adapter.jetfileii.standard.handler.operate;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractSimpleJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;

/**
 * 热重启处理器 —— 发送 {@code CTL_RESET_HOT (0x00)} 使设备软复位。
 */
public class JetFileIIRestartHandler extends AbstractSimpleJetFileIIHandler<EmptyParams> {

    public JetFileIIRestartHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.POWER_CONTROL_REBOOT;
    }

    @Override
    protected JetFileIIRequest buildRequest(DeviceContext device, EmptyParams params) {
        return JetFileIIRequest.of(MainCmd.CONTROL, SubCmd.CTL_RESET_HOT);
    }
}
