package com.gateway.device.protocol.api;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.discovery.ComplianceRule;

import java.util.List;

/**
 * 设备合规校验器 —— 设备信息查询后校验其硬件/固件是否符合预期。
 *
 * <p>每个厂商一个实现，Spring 自动注入到 AutoDiscoveryService。
 * 同一厂商的不同产品通过 compliance yaml 配置区分。</p>
 */
public interface DeviceComplianceValidator {

    /**
     * 所属厂商
     */
    DeviceVendor vendor();

    /**
     * 校验设备信息是否匹配给定规则。
     *
     * @param device   设备上下文
     * @param infoData 设备信息原始字节（如 SUB_READ_SYSINFO 返回的 data 段）
     * @param rules    当前产品的合规规则
     * @return 全部规则通过返回 true
     */
    boolean validate(DeviceContext device, Object infoData, List<ComplianceRule> rules);
}
