package com.gateway.device.protocol.base.novastar.viplexcore;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ViplexCore SDK 登录类型。
 *
 * <p>对应 {@code nvLoginAsync} 的 {@code loginType} 参数。</p>
 */
@Getter
@AllArgsConstructor
public enum LoginType {
    /**
     * 屏体管理（常规发现登录）
     */
    MANAGEMENT(0),
    /**
     * 系统设置（暗门登录，重启等特权操作需要）
     */
    SYSTEM(1),
    /**
     * 诊断模块
     */
    DIAGNOSTICS(2),
    /**
     * LCT 登录
     */
    LCT(3),
    /**
     * 公网 SDK 登录
     */
    PUBLIC_SDK(5);

    private final int code;
}
