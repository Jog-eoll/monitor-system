package com.publishgateway.udpproxy.enums;

/**
 * 情报板厂家枚举
 * 统一管理支持的厂家标识，作为 manufacturer 字段的标准值
 *
 * 使用规范：
 *   - {@link #getCode()} 用于配置传输、数据库存储、策略路由匹配
 *   - {@link #getDesc()} 用于界面展示的中文名称
 */
public enum ManufacturerEnum {

    SIGMA("sigma", "青松"),
    NOVA("nova", "诺瓦"),
    COLORLIGHT("colorlight", "卡莱特");

    /** 厂家标识码（存 DB、下发配置、策略路由均使用此值） */
    private final String code;

    /** 厂家中文名称（前端展示用） */
    private final String desc;

    ManufacturerEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据 code 查找对应枚举，未匹配返回 null
     */
    public static ManufacturerEnum fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (ManufacturerEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 判断 code 是否为合法的厂家标识
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
