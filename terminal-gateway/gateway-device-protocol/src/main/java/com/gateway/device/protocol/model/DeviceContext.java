package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.TransportType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 设备上下文 —— 设备在内存注册表中的统一表示。
 *
 * <p>每个 IP 对应一个设备，deviceId 为唯一标识。</p>
 */
@Data
@Builder
public class DeviceContext {

    /**
     * 设备唯一标识(当前取Mac)
     */
    private String deviceId;

    /**
     * IP 地址
     */
    private String ip;

    /**
     * 端口，0 表示使用厂商默认
     */
    private int port;

    /**
     * 厂商编码
     */
    private DeviceVendor vendor;

    /**
     * 传输协议类型（UDP/TCP/HTTP/NATIVE_SDK），设备构建时由 Adapter 确定。
     */
    private TransportType transportType;

    /**
     * 分组标签（合规校验匹配到的分组名，如 TB4-series / default）
     */
    private String groupLabel;

    /**
     * 是否在线
     */
    private boolean online;

    /**
     * 屏幕宽度（像素）
     */
    private Integer width;

    /**
     * 屏幕高度（像素）
     */
    private Integer height;

    /**
     * 序列号
     */
    private String sn;

    /**
     * MAC 地址
     */
    private String macAddr;

    /**
     * 设备支持的能力集合
     */
    private Set<DeviceCapability<?>> capabilities;

    /**
     * 厂商/型号扩展属性
     */
    private Map<String, Object> attributes;
}
