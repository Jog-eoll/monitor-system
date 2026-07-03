package com.gateway.device.core.controller;

import com.gateway.device.core.controller.dto.BatchCommandRequestDTO;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 目标设备 → DeviceSelector 映射器
 * <p>
 * 将 HTTP 请求中的 {@link BatchCommandRequestDTO.TargetRef} 转换为
 * 内部 {@link DeviceSelector} 多维度筛选条件。
 * </p>
 *
 * <p>映射规则：
 * <ul>
 *   <li>deviceId 优先 → 作为设备注册 ID 精确匹配</li>
 *   <li>若无 deviceId，则用 ip 作为 deviceId 兜底（设备注册时 IP 即为 ID）</li>
 *   <li>vendor / productType / groupId 可选维度，映射为 groupLabel 筛选</li>
 *   <li>onlineOnly 默认 true，仅操作在线设备</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class TargetToSelectorMapper {

    /**
     * 将 TargetRef 映射为 DeviceSelector。
     *
     * @param target 目标设备引用（可为 null，表示广播所有在线设备）
     * @return 构造好的 DeviceSelector
     */
    public DeviceSelector map(BatchCommandRequestDTO.TargetRef target) {
        DeviceSelector.DeviceSelectorBuilder builder = DeviceSelector.builder();

        if (target == null) {
            // 无目标信息 → 广播模式：仅在线
            log.info("[target映射] 无目标信息，广播至所有在线设备");
            builder.onlineOnly(true);
            return builder.build();
        }

        // -- deviceId / ip --
        String effectiveDeviceId = target.getDeviceId();
        if (StringUtils.hasText(effectiveDeviceId)) {
            effectiveDeviceId = effectiveDeviceId.trim();
        }
        if (StringUtils.hasText(effectiveDeviceId) && !StringUtils.hasText(target.getIp())) {
            builder.deviceIds(Collections.singleton(effectiveDeviceId));
            log.info("[target映射] 设备ID={}", effectiveDeviceId);
        }

        // -- ip --
        Set<String> ips = StringUtils.hasText(target.getIp())
                ? Collections.singleton(target.getIp().trim()) : Collections.emptySet();
        if (!ips.isEmpty()) {
            builder.ips(ips);
            log.info("[target-mapping] IP={}", ips);
        }

        if (StringUtils.hasText(target.getVendor())) {
            DeviceVendor vendor = resolveVendor(target.getVendor());
            if (vendor != null) {
                builder.vendors(Collections.singleton(vendor));
                log.info("[target映射] 厂商={}", vendor);
            } else {
                log.warn("[target映射] 未知厂商 '{}', 已跳过", target.getVendor());
            }
        }

        // ── 产品类型 / 分组：当前 DeviceSelector 统一使用 groupLabel ──
        Set<String> groupLabels = new LinkedHashSet<>();
        if (StringUtils.hasText(target.getProductType())) {
            groupLabels.add(target.getProductType().trim());
        }
        if (StringUtils.hasText(target.getGroupId())) {
            groupLabels.add(target.getGroupId().trim());
        }
        if (!groupLabels.isEmpty()) {
            builder.groupLabels(groupLabels);
            log.info("[target映射] 分组标签={}", groupLabels);
        }

        // ── 在线状态：默认仅在线 ──
        builder.onlineOnly(target.getOnlineOnly() != null ? target.getOnlineOnly() : true);

        return builder.build();
    }

    private DeviceVendor resolveVendor(String vendor) {
        if (!StringUtils.hasText(vendor)) {
            return null;
        }
        String normalized = vendor.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        if ("QINGSONG".equals(normalized)
                || "QING_SONG".equals(normalized)
                || "JETFILEII".equals(normalized)
                || "JET_FILEII".equals(normalized)
                || "JET_FILE_II".equals(normalized)) {
            return DeviceVendor.JET_FILE_II_STANDARD;
        }
        if ("COLORLIGHT".equals(normalized) || "COLOR_LIGHT".equals(normalized)) {
            return DeviceVendor.COLOR_LIGHT_STANDARD;
        }
        try {
            return DeviceVendor.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
