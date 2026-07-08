package com.gateway.device.protocol.adapter.colorlight.standard.handler.info;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.ColorLightCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight 设备截图 —— GET /api/screenshot。
 *
 * <p>Codec 已移除 {@code HttpObjectAggregator} 上限，单次 GET 即可获取完整 PNG 截图。</p>
 */
@Slf4j
public class ColorLightScreenshotHandler extends AbstractColorLightHttpHandler<EmptyParams> {

    public ColorLightScreenshotHandler(DeviceTransport transport,
                                       ColorLightCredentialStore credentialStore,
                                       ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return ColorLightCapability.SCREENSHOT_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        ColorLightHttpResponse resp = send(device, ColorLightApi.SCREENSHOT);
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_SCREENSHOT_FAIL", "获取设备截图失败");
        }
        byte[] png = resp.getBody();
        if (png == null || png.length == 0) {
            return failureResult("CL_SCREENSHOT_EMPTY", "设备截图数据为空");
        }
        return successResult(png);
    }
}
