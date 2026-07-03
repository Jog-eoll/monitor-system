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
import com.gateway.device.protocol.model.params.ScreenBlackoutParams;

public class JetFileIIScreenBlackoutHandler extends AbstractSimpleJetFileIIHandler<ScreenBlackoutParams> {

    public JetFileIIScreenBlackoutHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<ScreenBlackoutParams> capability() {
        return CommonDeviceCapability.SCREEN_BLACKOUT;
    }

    @Override
    protected JetFileIIRequest buildRequest(DeviceContext device, ScreenBlackoutParams params) {
        return JetFileIIRequest.of(MainCmd.CONTROL,
                params.isBlackout() ? SubCmd.CTL_BLACK_ON : SubCmd.CTL_BLACK_OFF);
    }
}
