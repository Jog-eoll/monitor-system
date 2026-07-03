package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.gateway.device.protocol.base.colorlight.standard.model.NetworkTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * ColorLight /api/ifstatus.json 响应 POJO。
 *
 * <p>用于获取网卡 MAC 地址，按 {@code type == "lan"} 过滤提取。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IfStatus {

    private List<NetworkType> types;

    /**
     * 按 type == "lan" 查找 MAC 地址，未匹配返回 null
     */
    public String findLanMac() {
        if (CollectionUtils.isEmpty(types)) return null;
        return types.stream().map(NetworkType::getMac).filter(StringUtils::isNotBlank).findFirst().orElse(null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkType {
        private NetworkTypeEnum type;
        private String mac;
        private int carrier;
        private int connected;
        private int enabled;
        private IpsInfo ips;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpsInfo {
        private String ip;
        private String mask;
        private String gateway;
        private String broadcast;
    }
}
