package com.gateway.device.protocol.base.novastar.standard.discovery;

import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.common.discovery.DiscoveryConst;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * NovaStandard 设备自动发现 —— AVON UDP 广播搜索。
 *
 * <p>在源端口 16600 向 255.255.255.255 + 各子网广播地址发送 AVON 搜索包，
 * 设备回应 JSON 注册信息。设备要求源端口必须为 16600。</p>
 *
 * <pre>{@code
 *   List<NovaStandardDiscoveredDevice> devices = NovaStandardDeviceDiscovery.discover(3000);
 * }</pre>
 */
public final class NovaStandardDeviceDiscovery {

    private static final NovaStandardAvonCodec avonCodec = new NovaStandardAvonCodec();

    private NovaStandardDeviceDiscovery() {
    }

    public static List<NovaStandardDiscoveredDevice> discover() throws IOException {
        return discover(GatewayTimeoutConstants.DISCOVERY_DEFAULT_TIMEOUT_MS);
    }

    /**
     * 广播搜索 NovaStandard 设备。
     *
     * @param timeoutMs 等待回应的超时毫秒数
     * @return 发现的设备列表 (按 SN 去重)
     */
    public static List<NovaStandardDiscoveredDevice> discover(int timeoutMs) throws IOException {
        byte[] request = buildBroadcastRequest();
        List<String> broadcastAddrs = collectBroadcastAddresses();
        Map<String, NovaStandardDiscoveredDevice> seen = new LinkedHashMap<>();

        DatagramSocket socket = new DatagramSocket(VendorDefaultPort.NOVA_STAR_STANDARD.getPort());
        socket.setBroadcast(true);
        socket.setSoTimeout(timeoutMs);

        try {
            for (int discoveryPort : DiscoveryConst.NOVA_STAR_DISCOVERY_PORTS) {
                for (String broadcastAddr : broadcastAddrs) {
                    try {
                        InetAddress addr = InetAddress.getByName(broadcastAddr);
                        socket.send(new DatagramPacket(request, request.length, addr, discoveryPort));
                    } catch (IOException ignored) {
                    }
                }
            }

            byte[] buf = new byte[65535];
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (true) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                socket.setSoTimeout(Math.max((int) remain, 1));

                try {
                    DatagramPacket recvPacket = new DatagramPacket(buf, buf.length);
                    socket.receive(recvPacket);

                    byte[] data = new byte[recvPacket.getLength()];
                    System.arraycopy(recvPacket.getData(), recvPacket.getOffset(),
                            data, 0, recvPacket.getLength());

                    NovaStandardDiscoveredDevice device = parseReply(data,
                            recvPacket.getAddress().getHostAddress(), recvPacket.getPort());
                    if (device != null) {
                        String key = device.getAvon().getSn() != null ? device.getAvon().getSn() : device.getIp();
                        seen.putIfAbsent(key, device);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                } catch (IOException ignored) {
                }
            }
        } finally {
            socket.close();
        }

        return new ArrayList<>(seen.values());
    }

    public static List<String> collectBroadcastAddresses() {
        List<String> addrs = new ArrayList<>();
        addrs.add(DiscoveryConst.BROADCAST_HOST);

        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InterfaceAddress ifAddr : iface.getInterfaceAddresses()) {
                    InetAddress bcast = ifAddr.getBroadcast();
                    if (bcast != null) {
                        addrs.add(bcast.getHostAddress());
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return addrs;
    }

    /**
     * 使用 Avon 编解码器构建搜索请求
     */
    public static byte[] buildBroadcastRequest() {
        return avonCodec.buildSearchRequest();
    }

    /**
     * 解析 AVON 回复，转换为 NovaStandardDiscoveredDevice
     */
    public static NovaStandardDiscoveredDevice parseReply(byte[] raw, String sourceIp, int sourcePort) {
        NovaStandardAvonReply reply = avonCodec.parseReply(raw, sourceIp, sourcePort);
        if (reply == null) return null;
        return reply.toDiscoveredDevice();
    }
}
