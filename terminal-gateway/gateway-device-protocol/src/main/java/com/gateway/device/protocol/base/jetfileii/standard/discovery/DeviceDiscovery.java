package com.gateway.device.protocol.base.jetfileii.standard.discovery;

import com.gateway.device.protocol.base.jetfileii.standard.JetFileIIParser;
import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.common.discovery.DiscoveryConst;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.*;

/**
 * 广播搜索局域网内 JetFileII 设备。
 *
 * <p>通过 UDP 广播 0x0301 连接测试命令，收集所有在线设备的回送信息。</p>
 *
 * <pre>{@code
 *   List<JetFileIIDiscoveredDevice> devices = DeviceDiscovery.discover(9520, 3000);
 *   for (JetFileIIDiscoveredDevice d : devices) {
 *       System.out.println(d.getIp() + "  GG=" + d.getGg() + " UU=" + d.getUu());
 *   }
 * }</pre>
 */
public final class DeviceDiscovery {

    private DeviceDiscovery() {
    }

    /**
     * 使用默认端口 9520、默认超时搜索设备
     */
    public static List<JetFileIIDiscoveredDevice> discover() throws IOException {
        return discover(VendorDefaultPort.JET_FILE_II.getPort(), DiscoveryConst.DEFAULT_TIMEOUT_MS);
    }

    /**
     * 指定端口搜索设备
     */
    public static List<JetFileIIDiscoveredDevice> discover(int port) throws IOException {
        return discover(port, DiscoveryConst.DEFAULT_TIMEOUT_MS);
    }

    /**
     * 广播搜索局域网内的 JetFileII 设备。
     *
     * @param port      目标 UDP 端口
     * @param timeoutMs 等待回送的超时毫秒数
     * @return 发现的设备列表，按 GG+UU 排序
     */
    public static List<JetFileIIDiscoveredDevice> discover(int port, int timeoutMs) throws IOException {
        byte[] request = buildBroadcastRequest();
        Map<String, JetFileIIDiscoveredDevice> seen = new LinkedHashMap<>();

        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.setSoTimeout(timeoutMs);

        try {
            InetAddress broadcastAddr = InetAddress.getByName(DiscoveryConst.BROADCAST_HOST);
            DatagramPacket sendPacket = new DatagramPacket(
                    request, request.length, broadcastAddr, port);
            socket.send(sendPacket);

            byte[] buf = new byte[65535];
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (true) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                socket.setSoTimeout((int) remain);

                try {
                    DatagramPacket recvPacket = new DatagramPacket(buf, buf.length);
                    socket.receive(recvPacket);

                    byte[] data = Arrays.copyOfRange(recvPacket.getData(),
                            recvPacket.getOffset(), recvPacket.getOffset() + recvPacket.getLength());

                    JetFileIIDiscoveredDevice device = parseReply(data, recvPacket.getAddress().getHostAddress(),
                            recvPacket.getPort());
                    if (device != null) {
                        seen.putIfAbsent(device.getIp(), device);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                } catch (IOException ignored) {
                }
            }
        } finally {
            socket.close();
        }

        List<JetFileIIDiscoveredDevice> result = new ArrayList<>(seen.values());
        result.sort(Comparator.comparingInt(JetFileIIDiscoveredDevice::getGg)
                .thenComparingInt(JetFileIIDiscoveredDevice::getUu));
        return result;
    }

    /**
     * 构建 0x0301 连接测试广播请求
     */
    public static byte[] buildBroadcastRequest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_CONNECT)
                .broadcast().needReply().buildBytes();
    }

    /**
     * 解析 0x0301 回送 Arg 格式 (12B):
     * [0-1] CPU 版本 (UWORD), [2-3] FPGA 版本 (UWORD),
     * [4-7] IP 地址 (LE), [8] GG, [9] UU, [10-11] Rev
     */
    public static JetFileIIDiscoveredDevice parseReply(byte[] raw, String sourceIp, int sourcePort) {
        PacketMessage pkt = JetFileIIParser.parse(raw);
        if (pkt == null || !pkt.isReply()) return null;

        byte[] arg = pkt.getArg();
        if (arg == null || arg.length < 10) return null;

        int cpuVersion = LittleEndianByteBufUtils.readUShortLE(arg, 0);
        int fpgaVersion = LittleEndianByteBufUtils.readUShortLE(arg, 2);
        int gg = arg[8] & 0xFF;
        int uu = arg[9] & 0xFF;

        String deviceIp;
        if (arg.length >= 8) {
            deviceIp = String.format("%d.%d.%d.%d",
                    arg[7] & 0xFF, arg[6] & 0xFF,
                    arg[5] & 0xFF, arg[4] & 0xFF);
        } else {
            deviceIp = sourceIp;
        }
        if ("0.0.0.0".equals(deviceIp)) {
            deviceIp = sourceIp;
        }

        return JetFileIIDiscoveredDevice.builder()
                .ip(deviceIp)
                .gg(gg).uu(uu)
                .cpuVersion(cpuVersion).fpgaVersion(fpgaVersion)
                .sourcePort(sourcePort)
                .build();
    }
}
