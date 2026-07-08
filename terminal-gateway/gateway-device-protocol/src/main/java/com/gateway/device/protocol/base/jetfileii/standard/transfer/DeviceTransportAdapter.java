package com.gateway.device.protocol.base.jetfileii.standard.transfer;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.JetFileIIParser;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.model.DeviceContext;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link DeviceTransport} 适配器 — 桥接异步 Netty 通道为同步 sendAndReceive。
 */
public class DeviceTransportAdapter implements TransportAdapter {

    private static final Duration TIMEOUT = Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS);
    private final DeviceTransport transport;
    private final DeviceContext device;

    public DeviceTransportAdapter(DeviceTransport transport, DeviceContext device) {
        this.transport = transport;
        this.device = device;
    }

    @Override
    public PacketMessage sendAndReceive(byte[] req) throws IOException {
        try {
            byte[] resp = transport.sendAndReceive(device, req, TIMEOUT)
                    .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            PacketMessage pkt = JetFileIIParser.parse(resp);
            if (pkt == null) throw new IOException("无法解析响应报文");
            return pkt;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("传输中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("传输失败: " + e.getMessage(), e);
        }
    }
}
