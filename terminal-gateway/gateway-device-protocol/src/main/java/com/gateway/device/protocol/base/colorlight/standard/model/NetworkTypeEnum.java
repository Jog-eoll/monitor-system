package com.gateway.device.protocol.base.colorlight.standard.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.Strings;

/**
 * ColorLight 设备网络类型枚举。
 *
 * <p>映射 /api/network.json 和 /api/ifstatus.json 中 {@code type} 字段的取值，
 * 参考 PlayerSDK 文档"配置播放盒网络"。</p>
 *
 * <p>WiFi、LAN、4G 只能开启使用其中一种，WiFi AP 可与 WiFi、LAN 或 4G 同时使用。</p>
 */
@Getter
@AllArgsConstructor
public enum NetworkTypeEnum {

    /**
     * 有线局域网
     */
    LAN("lan"),
    /**
     * 无线局域网（客户端模式）
     */
    WIFI("wifi"),
    /**
     * 无线热点（AP 模式）
     */
    WIFI_AP("wifi ap"),
    /**
     * 4G 蜂窝网络
     */
    MOBILE_4G("4G");

    @JsonValue
    private final String value;

    /**
     * 按原始字符串值查找枚举，未匹配返回 {@code null}。
     */
    @JsonCreator
    public static NetworkTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (NetworkTypeEnum e : values()) {
            if (Strings.CI.equals(e.value, value)) {
                return e;
            }
        }
        if (Strings.CI.equals("ap", value)) {
            return WIFI_AP;
        }
        return null;
    }
}
