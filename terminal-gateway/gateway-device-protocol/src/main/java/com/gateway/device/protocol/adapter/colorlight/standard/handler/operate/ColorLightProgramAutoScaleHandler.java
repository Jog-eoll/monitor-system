package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.ProgramAutoScaleInfo;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.ColorLightCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ProgramAutoScaleParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 节目分辨率自适应开关 —— PUT /api/programautoscale。
 *
 * <p>开启后发布节目的宽高根据屏幕分辨率等比例缩放铺满全屏，
 * 关闭后按节目原始分辨率显示。</p>
 */
@Slf4j
public class ColorLightProgramAutoScaleHandler extends AbstractColorLightHttpHandler<ProgramAutoScaleParams> {

    public ColorLightProgramAutoScaleHandler(DeviceTransport transport,
                                             ColorLightCredentialStore credentialStore,
                                             ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<ProgramAutoScaleParams> capability() {
        return ColorLightCapability.PROGRAM_AUTO_SCALE;
    }

    @Override
    public CommandResult execute(DeviceContext device, ProgramAutoScaleParams params) {
        ProgramAutoScaleInfo payload = ProgramAutoScaleInfo.builder()
                .programautoscale(params.isEnable())
                .build();

        log.info("[{}] 节目分辨率自适应: {}",
                device.getIp(), params.isEnable() ? "开启" : "关闭");

        ColorLightHttpResponse resp = send(device, ColorLightApi.PROGRAM_AUTO_SCALE_SET, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_PROGRAM_AUTO_SCALE_FAIL", "节目分辨率自适应开关设置失败");
        }
        return successResult();
    }
}
