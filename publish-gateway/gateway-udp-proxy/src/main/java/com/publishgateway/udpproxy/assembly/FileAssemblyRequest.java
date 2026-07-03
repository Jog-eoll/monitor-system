package com.publishgateway.udpproxy.assembly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UDP 文件重组入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAssemblyRequest {

    private String ruleId;

    private Long chainId;

    /** 厂商标识，当前支持 sigma；为空时按 sigma 处理。 */
    private String manufacturer;

    private String sourceIp;

    private Integer sourcePort;

    private String targetIp;

    private Integer targetPort;

    /** 原始 UDP payload。 */
    private byte[] payload;

    /** 接收时间，毫秒时间戳。 */
    private Long receivedAt;

    public long resolveReceivedAt() {
        return receivedAt != null ? receivedAt : System.currentTimeMillis();
    }
}
