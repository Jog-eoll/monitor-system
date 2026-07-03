package com.gateway.device.protocol.adapter.colorlight.standard.handler.operate;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.request.SyncProgramModePayload;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.NtpSetParams;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;

/**
 * ColorLight NTP 配置 —— PUT /api/sync_program_mode（旧 /api/ntp 已废弃）。
 */
@Slf4j
public class ColorLightNtpConfigHandler extends AbstractColorLightHttpHandler<NtpSetParams> {

    public ColorLightNtpConfigHandler(DeviceTransport transport,
                                      ColorLightCredentialStore credentialStore,
                                      ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        super(transport, credentialStore, codec);
    }

    @Override
    public DeviceCapability<NtpSetParams> capability() {
        return CommonDeviceCapability.NTP_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, NtpSetParams params) {
        SyncProgramModePayload payload = SyncProgramModePayload.builder()
                .gps(SyncProgramModePayload.GpsSync.builder().enable(false).build())
                .ntp(SyncProgramModePayload.NtpSync.builder()
                        .enable(true)
                        .syncNtpServer(SyncProgramModePayload.SyncNtpServer.builder()
                                .server(params.resolveNtpServer())
                                .interval(params.getNtpInterval())
                                .threshold(params.getNtpThreshold())
                                .build())
                        .build())
                .build();
        ColorLightHttpResponse resp = send(device, ColorLightApi.SYNC_PROGRAM_MODE_SET, serializeBody(payload));
        if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            return failureResult("CL_NTP_FAIL", "Failed to set NTP");
        }
        return successResult();
    }
}
