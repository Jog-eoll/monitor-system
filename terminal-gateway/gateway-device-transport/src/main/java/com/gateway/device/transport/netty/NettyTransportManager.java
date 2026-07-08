package com.gateway.device.transport.netty;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ParsedHttpResponse;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.transport.netty.common.EventLoopResources;
import com.gateway.device.transport.netty.http.HttpChannelPool;
import com.gateway.device.transport.netty.http.HttpTransport;
import com.gateway.device.transport.netty.tcp.TcpChannelPool;
import com.gateway.device.transport.netty.tcp.TcpTransport;
import com.gateway.device.transport.netty.udp.UdpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Netty transport facade.
 *
 * <p>The concrete HTTP/TCP/UDP implementations own protocol-specific channel
 * pools and request handling. This class keeps the historical DeviceTransport
 * entry point stable for protocol adapters and core auto-configuration.</p>
 */
public class NettyTransportManager implements DeviceTransport, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyTransportManager.class);

    private final EventLoopResources resources;
    private final HttpTransport httpTransport;
    private final TcpTransport tcpTransport;
    private final UdpTransport udpTransport;
    private final ConnectionHealthChecker healthChecker;

    public NettyTransportManager(NettyTransportConfig config,
                                 TcpChannelPool tcpChannelPool,
                                 HttpChannelPool httpChannelPool,
                                 ConnectionHealthChecker healthChecker) {
        this.resources = new EventLoopResources(config);
        this.healthChecker = healthChecker;

        tcpChannelPool.setHealthChecker(healthChecker);
        httpChannelPool.setHealthChecker(healthChecker);
        healthChecker.setOfflineListener(deviceKey -> {
            tcpChannelPool.removeAndClose(deviceKey);
            httpChannelPool.removeAndClose(deviceKey);
            healthChecker.remove(deviceKey);
        });

        this.tcpTransport = new TcpTransport(resources, config, tcpChannelPool);
        this.httpTransport = new HttpTransport(resources, config, httpChannelPool);
        this.udpTransport = new UdpTransport(resources, config);
    }

    NettyTransportManager(EventLoopResources resources,
                          HttpTransport httpTransport,
                          TcpTransport tcpTransport,
                          UdpTransport udpTransport,
                          ConnectionHealthChecker healthChecker) {
        this.resources = resources;
        this.httpTransport = httpTransport;
        this.tcpTransport = tcpTransport;
        this.udpTransport = udpTransport;
        this.healthChecker = healthChecker;
    }

    public ConnectionHealthChecker getHealthChecker() {
        return healthChecker;
    }

    public void startUdp(int localPort) throws InterruptedException {
        udpTransport.startUdp(localPort);
    }

    @Override
    public CompletableFuture<byte[]> sendAndReceive(DeviceContext device, byte[] payload, Duration timeout) {
        TransportType type = device.getTransportType();
        if (TransportType.TCP.equals(type) || TransportType.HTTP.equals(type)) {
            return tcpTransport.sendAndReceive(device, payload, timeout);
        }
        return udpTransport.sendAndReceive(device, payload, timeout);
    }

    @Override
    public CompletableFuture<ParsedHttpResponse> sendHttp(DeviceContext device, Object httpRequest, Duration timeout) {
        return httpTransport.sendHttp(device, httpRequest, timeout);
    }

    @Override
    public CompletableFuture<Void> sendOnly(DeviceContext device, byte[] payload) {
        return udpTransport.sendOnly(device, payload);
    }

    @Override
    public CompletableFuture<Boolean> tcpProbe(DeviceContext device, Duration timeout) {
        return tcpTransport.tcpProbe(device, timeout);
    }

    public CompletableFuture<List<BroadcastResponse>> broadcastAndCollect(
            byte[] payload, InetSocketAddress target, Duration timeout) {
        return udpTransport.broadcastAndCollect(payload, target, timeout);
    }

    @Override
    public void close() {
        log.info("Closing NettyTransportManager...");
        try {
            httpTransport.close();
        } finally {
            try {
                tcpTransport.close();
            } finally {
                try {
                    udpTransport.close();
                } finally {
                    healthChecker.shutdown();
                    resources.close();
                }
            }
        }
    }

    public static class BroadcastResponse {
        private final byte[] data;
        private final String senderIp;
        private final int senderPort;

        public BroadcastResponse(byte[] data, String senderIp, int senderPort) {
            this.data = data;
            this.senderIp = senderIp;
            this.senderPort = senderPort;
        }

        public byte[] getData() {
            return data;
        }

        public String getSenderIp() {
            return senderIp;
        }

        public int getSenderPort() {
            return senderPort;
        }
    }
}
