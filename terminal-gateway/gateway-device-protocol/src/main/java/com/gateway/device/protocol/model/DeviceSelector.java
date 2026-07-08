package com.gateway.device.protocol.model;

import com.gateway.device.protocol.api.DeviceFilter;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;

/**
 * 设备筛选条件 —— 支持多维度组合筛选，通过 {@link #toFilters()} 生成过滤链。
 */
@Data
@Builder
public class DeviceSelector {

    /**
     * 指定设备 ID 列表
     */
    private Set<String> deviceIds;

    /**
     * 指定厂商
     */
    private Set<DeviceVendor> vendors;

    /**
     * 指定分组标签
     */
    private Set<String> groupLabels;

    /**
     * 仅在线设备
     */
    private Boolean onlineOnly;

    /**
     * 仅已登录设备
     */
    private Boolean loggedInOnly;

    /**
     * 要求具备的能力
     */
    private Set<DeviceCapability<?>> requiredCapabilities;

    /**
     * 按 IP 筛选
     */
    private Set<String> ips;

    /**
     * 按设备 SN 筛选
     */
    private Set<String> sns;

    /**
     * 按设备 MAC 筛选（支持多种格式，内部自动归一化）
     */
    private Set<String> macs;

    /**
     * 按 attributes 键值对筛选（如 {"serialNo":"ABC", "macAddr":"xx:xx"}）
     */
    private Map<String, String> attributeFilters;

    /**
     * 是否显式指定了目标设备（通过 deviceId / IP / SN / MAC 精确定位），
     * 用于区分"明确指定但设备未就绪"和"广播式无匹配"两种场景。
     */
    public boolean isExplicit() {
        return CollectionUtils.isNotEmpty(deviceIds)
                || CollectionUtils.isNotEmpty(ips)
                || CollectionUtils.isNotEmpty(sns)
                || CollectionUtils.isNotEmpty(macs);
    }

    /**
     * 将全部筛选维度转为 Filter 列表，Resolver 统一遍历执行。
     */
    public List<DeviceFilter> toFilters() {
        List<DeviceFilter> filters = new ArrayList<>();

        if (CollectionUtils.isNotEmpty(deviceIds)) {
            Set<String> ids = this.deviceIds;
            filters.add(device -> ids.contains(device.getDeviceId()));
        }
        if (CollectionUtils.isNotEmpty(vendors)) {
            Set<DeviceVendor> v = this.vendors;
            filters.add(device -> v.contains(device.getVendor()));
        }
        if (CollectionUtils.isNotEmpty(groupLabels)) {
            Set<String> t = this.groupLabels;
            filters.add(device -> t.contains(device.getGroupLabel()));
        }
        if (Boolean.TRUE.equals(onlineOnly)) {
            filters.add(DeviceContext::isOnline);
        }
        if (Boolean.TRUE.equals(loggedInOnly)) {
            filters.add(DeviceContext::isLoggedIn);
        }
        if (CollectionUtils.isNotEmpty(requiredCapabilities)) {
            Set<DeviceCapability<?>> c = this.requiredCapabilities;
            filters.add(device -> {
                Set<DeviceCapability<?>> dc = device.getCapabilities();
                return dc != null && dc.containsAll(c);
            });
        }
        if (CollectionUtils.isNotEmpty(ips)) {
            Set<String> ipSet = this.ips;
            filters.add(device -> ipSet.contains(device.getIp()));
        }
        if (CollectionUtils.isNotEmpty(sns)) {
            Set<String> snSet = this.sns;
            filters.add(device -> snSet.contains(device.getSn()));
        }
        if (CollectionUtils.isNotEmpty(macs)) {
            Set<String> normalized = new HashSet<>();
            for (String m : this.macs) {
                normalized.add(ProtocolConstant.formatMac(m));
            }
            filters.add(device -> normalized.contains(device.getMacAddr()));
        }
        if (MapUtils.isNotEmpty(attributeFilters)) {
            Map<String, String> attrs = this.attributeFilters;
            filters.add(device -> {
                Map<String, Object> devAttrs = device.getAttributes();
                if (devAttrs == null) return false;
                return attrs.entrySet().stream()
                        .allMatch(e -> {
                            Object devVal = devAttrs.get(e.getKey());
                            if (devVal == null) return false;
                            return Objects.equals(devVal.toString(), e.getValue());
                        });
            });
        }

        return filters;
    }
}
