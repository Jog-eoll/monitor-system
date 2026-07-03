package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd.READ;
import static com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd.READ_SYSINFO;

/**
 * JetFileII 设备注册信息提供者 —— 独立于 {@code JetFileIIDeviceInfoGetHandler}。
 *
 * <p>发送 READ_SYSINFO 命令获取 CONFIG.SYS 全量数据，返回 {@code byte[]}，
 * 供 AutoDiscoveryService 注册 + DeviceInfoEnricher 解析使用。</p>
 */
@Slf4j
public class JetFileIIDeviceRegistrationProvider implements DeviceRegistrationProvider {

    private final JetFileIIMessaging messaging;
    private final DeviceTransport transport;

    public JetFileIIDeviceRegistrationProvider(JetFileIIMessaging messaging, DeviceTransport transport) {
        this.messaging = messaging;
        this.transport = transport;
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.JET_FILE_II_STANDARD;
    }

    @Override
    public Object fetchRegistrationInfo(DeviceContext device) {
        int gg = JetFileIIMessaging.resolveGg(device);
        int uu = JetFileIIMessaging.resolveUu(device);
        JetFileIIRequest request = JetFileIIRequest.builder()
                .mainCmd(READ).subCmd(READ_SYSINFO)
                .destGg(gg).destUu(uu)
                .needReply(true).build();

        byte[] payload = messaging.encode(request);
        Duration timeout = Duration.ofSeconds(5);

        try {
            byte[] response = transport.sendAndReceive(device, payload, timeout)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            PacketMessage pkt = messaging.decode(response);
            if (pkt == null) {
                log.warn("[{}] 注册信息获取失败: 无法解析响应", device.getIp());
                return null;
            }
            if (pkt.isStatusReply()) {
                log.warn("[{}] 注册信息获取失败: 设备返回错误 0x{}",
                        device.getIp(), Integer.toHexString(pkt.getStatusCode() & 0xFFFF));
                return null;
            }
            return pkt.getData();
        } catch (Exception e) {
            log.error("[{}] 注册信息获取异常: {}", device.getIp(), e.getMessage());
            return null;
        }
    }
}
