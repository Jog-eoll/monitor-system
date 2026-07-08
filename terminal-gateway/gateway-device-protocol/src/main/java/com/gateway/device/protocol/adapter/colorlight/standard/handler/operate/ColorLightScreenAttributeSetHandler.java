package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.DimensionSetPayload;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ScreenAttributeParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 输出分辨率配置 —— PUT /api/dimension。
 *
 * <p>设置设备视频输出分辨率（width/height/freq），
 * 区别于 NovaStar 的 LED 物理点阵配屏（{@code SCREEN_ATTRIBUTE_SET}）。</p>
 */
@Slf4j
public class ColorLightScreenAttributeSetHandler extends AbstractColorLightHttpHandler<ScreenAttributeParams> {

    public ColorLightScreenAttributeSetHandler(DeviceTransport transport,
                                               ColorLightCredentialStore credentialStore,
                                               ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<ScreenAttributeParams> capability() {
        return CommonDeviceCapability.SCREEN_ATTRIBUTE_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, ScreenAttributeParams params) {
        if (params == null) {
            return failureResult("CL_DIMENSION_FAIL", "缺少 width/height 参数");
        }

        DimensionSetPayload payload = DimensionSetPayload.builder()
                .width(params.getWidth())
                .height(params.getHeight())
                .freq(params.getFreq())
                .build();

        log.info("[{}] 配置输出分辨率: width={} height={} freq={}",
                device.getIp(), payload.getWidth(), payload.getHeight(), payload.getFreq());

        ColorLightHttpResponse resp = send(device, ColorLightApi.DIMENSION_SET, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_DIMENSION_FAIL", "配置输出分辨率失败");
        }
        return successResult();
    }
}
