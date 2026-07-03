package com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractSimpleJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.ValueClamp;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.BrightnessSetParams;


public class JetFileIIBrightnessHandler extends AbstractSimpleJetFileIIHandler<BrightnessSetParams> {

    public JetFileIIBrightnessHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<BrightnessSetParams> capability() {
        return CommonDeviceCapability.BRIGHTNESS_SET;
    }

    @Override
    protected JetFileIIRequest buildRequest(DeviceContext device, BrightnessSetParams params) {
        int level = params != null ? params.getRatio() : 80;
        byte[] arg = new byte[4];
        arg[0] = (byte) ValueClamp.ratio(level);
        arg[1] = (byte) 0xFF;
        return JetFileIIRequest.builder()
                .mainCmd(MainCmd.CONTROL)
                .subCmd(SubCmd.CTL_BRIGHTNESS)
                .arg(arg).needReply(true).build();
    }
}
