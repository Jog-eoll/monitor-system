package com.gateway.device.protocol.adapter.jetfileii.standard.handler.operate;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractSimpleJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.TimeSyncParams;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 时间同步处理器 —— JetFileII 串口协议。
 *
 * <p>始终以 TIME_WRITE 同步：提供 {@code targetTime} 时用指定时间，否则用当前系统时间。</p>
 */
@Slf4j
public class JetFileIITimeSyncHandler extends AbstractSimpleJetFileIIHandler<TimeSyncParams> {

    public JetFileIITimeSyncHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    /**
     * 将 LocalDateTime 按指定时区编码为 8 字节 BCD。
     */
    private static byte[] toBcdBytes(LocalDateTime dt, ZoneId zone) {
        ZonedDateTime zdt = dt.atZone(zone);
        byte[] bcd = new byte[8];
        bcd[0] = toBcd(zdt.getYear() % 100);
        bcd[1] = toBcd(zdt.getYear() / 100);
        bcd[2] = toBcd(zdt.getMonthValue());
        bcd[3] = toBcd(zdt.getDayOfMonth());
        bcd[4] = toBcd(zdt.getHour());
        bcd[5] = toBcd(zdt.getMinute());
        bcd[6] = toBcd(zdt.getSecond());
        bcd[7] = 0x0c;
        return bcd;
    }

    private static byte toBcd(int value) {
        return (byte) (((value / 10) << 4) | (value % 10));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public DeviceCapability<TimeSyncParams> capability() {
        return CommonDeviceCapability.TIME_SYNC;
    }

    @Override
    protected JetFileIIRequest buildRequest(DeviceContext device, TimeSyncParams params) {
        LocalDateTime targetTime = params.getTargetTime() != null
                ? params.getTargetTime() : LocalDateTime.now();
        ZoneId zone = params.getTimeZone() != null
                ? params.getTimeZone() : ZoneId.systemDefault();
        byte[] bcd = toBcdBytes(targetTime, zone);

        log.info("[JetFileII TIME_WRITE] SN={} targetTime={} zone={} bcd={}",
                device.getSn(), targetTime, zone, bytesToHex(bcd));

        return JetFileIIRequest.builder()
                .mainCmd(MainCmd.TIME)
                .subCmd(SubCmd.TIME_WRITE)
                .data(bcd).needReply(true).build();
    }
}
