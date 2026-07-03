package com.gateway.device.protocol.adapter.jetfileii.standard.handler.operate;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractSimpleJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ColorTestParams;
import com.gateway.device.protocol.model.params.ColorTestParams.Color;

/**
 * 色彩测试处理器 —— 开启/关闭终端纯色/彩条测试。
 *
 * <p>开启时根据 {@code color} 发送对应测试命令（默认红色），关闭时发送 {@code TEST_END}，
 * 设备均需回送 2B 状态码。色彩映射预留后续扩展。</p>
 */
public class JetFileIIColorTestHandler extends AbstractSimpleJetFileIIHandler<ColorTestParams> {

    public JetFileIIColorTestHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    /**
     * 色彩枚举 → 测试子命令映射，默认 {@link SubCmd#TEST_COLOR}。
     */
    private static byte toColorSubCmd(ColorTestParams params) {
        Color color = params != null ? params.getColor() : null;
        if (color == null) {
            return SubCmd.TEST_COLOR;
        }
        switch (color) {
            case RED:
                return SubCmd.TEST_ALL_RED;
            case GREEN:
                return SubCmd.TEST_ALL_GREEN;
            case BLUE:
                return SubCmd.TEST_ALL_BLUE;
            case WHITE:
                return SubCmd.TEST_ALL_WHITE;
            case GRAY:
                return SubCmd.TEST_GRAY;
            case COLOR_BAR:
            default:
                return SubCmd.TEST_COLOR;
        }
    }

    @Override
    public DeviceCapability<ColorTestParams> capability() {
        return JetFileIICapability.COLOR_TEST;
    }

    @Override
    protected JetFileIIRequest buildRequest(DeviceContext device, ColorTestParams params) {
        boolean enabled = params == null || params.isEnabled();
        byte subCmd = enabled ? toColorSubCmd(params) : SubCmd.TEST_END;
        return JetFileIIRequest.of(MainCmd.TEST, subCmd);
    }
}
