package com.gateway.device.protocol.base.novastar.standard.discovery;

import com.gateway.device.protocol.api.DiscoveredDevice;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * NovaStandard AVON 广播发现的设备信息，组合 {@link NovaStandardAvonReply}。
 *
 * <p>设备唯一标识：SN（序列号），MAC 地址当前 AVON 协议不支持。</p>
 */
@Data
@Builder
public class NovaStandardDiscoveredDevice implements DiscoveredDevice {

    private String ip;
    private int sourcePort;

    /**
     * AVON 广播回复 JSON 映射，包含设备全部属性。
     */
    private NovaStandardAvonReply avon;

    /**
     * MAC 地址（当前 AVON 协议不支持，预留）
     */
    private String macAddr;

    // ═══════════════════════════════════════════════════
    // DiscoveredDevice 接口
    // ═══════════════════════════════════════════════════

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        if (avon != null) {
            // deviceName 特殊处理：如果 aliasName 不为 null，即使为空字符串也放入
            if (avon.getAliasName() != null) {
                attrs.put("deviceName", avon.getAliasName());
            }
            MapUtils.safeAddToMap(attrs, "sn", avon.getSn());
            MapUtils.safeAddToMap(attrs, "productName", avon.getProductName());
            MapUtils.safeAddToMap(attrs, "platform", avon.getPlatform());
            MapUtils.safeAddToMap(attrs, "tcpPort", avon.getTcpPort());
            MapUtils.safeAddToMap(attrs, "ftpPort", avon.getFtpPort());
            MapUtils.safeAddToMap(attrs, "syssetTcpPort", avon.getSyssetTcpPort());
            MapUtils.safeAddToMap(attrs, "syssetFtpPort", avon.getSyssetFtpPort());
            MapUtils.safeAddToMap(attrs, "width", avon.getWidth());
            MapUtils.safeAddToMap(attrs, "height", avon.getHeight());
            MapUtils.safeAddToMap(attrs, "rotation", avon.getRotation());
            MapUtils.safeAddToMap(attrs, "encodeType", avon.getEncodeType());
            MapUtils.safeAddToMap(attrs, "logined", avon.isLogined());
            MapUtils.safeAddToMap(attrs, "privacy", avon.isPrivacy());
            MapUtils.safeAddToMap(attrs, "key", avon.getKey());
            MapUtils.safeAddToMap(attrs, "loginedUsernames", avon.getLoginedUsernames());
        }
        MapUtils.safeAddToMap(attrs, "macAddr", macAddr);
        return attrs;
    }
}
