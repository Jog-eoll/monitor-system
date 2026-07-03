package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.TimeSyncPayload;
import com.gateway.device.protocol.common.DateTimeFormatUtils;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.TimeSyncParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.net.HttpURLConnection;
import java.time.ZoneId;

/**
 * ColorLight 时间同步 —— PUT /api/newrtc。
 */
@Slf4j
public class ColorLightTimeSyncHandler extends AbstractColorLightHttpHandler<TimeSyncParams> {
    public ColorLightTimeSyncHandler(DeviceTransport transport,
                                     ColorLightCredentialStore credentialStore,
                                     ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<TimeSyncParams> capability() {
        return CommonDeviceCapability.TIME_SYNC;
    }

    @Override
    public CommandResult execute(DeviceContext device, TimeSyncParams params) {
        ImmutablePair<ZoneId, Float> zonePair = DateTimeFormatUtils.ZoneFormat(params.getTargetTime(), params.getTimeZone());

        TimeSyncPayload payload = TimeSyncPayload.builder()
                .time(DateTimeFormatUtils.TimeFormat(params.getTargetTime()))
                .timezoneId(zonePair.getLeft().getId())
                .timezone(zonePair.getRight())
                .isautotime(1)
                .build();
        ColorLightHttpResponse resp = send(device, ColorLightApi.TIME_SYNC, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_TIME_FAIL", "Failed to sync time");
        }
        return successResult();
    }
}
