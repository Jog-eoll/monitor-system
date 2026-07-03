package com.gateway.device.core.config;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.discovery.ComplianceGroupConfig;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

/**
 * 自动广播发现配置。
 *
 * <pre>
 * device.discovery:
 *   enabled: true
 *   scan-interval-minutes: 1
 *   mappings:
 *     - port: 9520
 *       vendor: JET_FILE_II
 *   compliance:
 *     JET_FILE_II:
 *       TB4-series:
 *         rules:
 *           - field: wID
 *             expected: "0x55AA"
 *       default:
 *         enabled: true
 *         rules: []
 * </pre>
 */
@Setter
@Getter
@ConfigurationProperties("device.discovery")
public class DiscoveryProperties {

    /**
     * 是否启用自动发现
     */
    private boolean enabled = true;

    /**
     * 扫描间隔（分钟），默认 5
     */
    private int scanIntervalMinutes = 5;

    /**
     * 广播扫描未发现设备时是否自动标记离线。
     */
    private boolean markOfflineWhenMissing = false;

    /**
     * 端口/IP → 厂商映射
     */
    private List<DeviceVendorMapping> mappings = new ArrayList<>();

    /**
     * 合规校验规则：厂商 → 产品类型 → 分组配置（按 YAML 书写顺序，default 为 fallback）
     */
    private Map<DeviceVendor, LinkedHashMap<String, ComplianceGroupConfig>> compliance = new HashMap<>();

}
