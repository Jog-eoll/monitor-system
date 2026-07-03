package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * NTP 服务器配置参数 — NTP_SET。
 */
@Data
@Builder
public class NtpSetParams implements CommandParams {

    private static final String DEFAULT_NTP_SERVER = "ntp.ntsc.ac.cn";
    /**
     * NTP 服务器地址，默认 {@code ntp.ntsc.ac.cn}
     */
    private String ntpServer;
    /**
     * 同步时间间隔
     */
    @Builder.Default
    private long ntpInterval = 600000L;
    /**
     * 同步时间阈值
     */
    @Builder.Default
    private long ntpThreshold = 2000L;

    public String resolveNtpServer() {
        if (StringUtils.isNotEmpty(ntpServer)) {
            return ntpServer;
        }
        return DEFAULT_NTP_SERVER;
    }
}
