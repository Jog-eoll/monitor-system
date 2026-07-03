package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;

/**
 * 空参数 —— 用于无参数能力（查询/监控等仅需设备 SN 的能力）。
 */
public final class EmptyParams implements CommandParams {

    public static final EmptyParams INSTANCE = new EmptyParams();

    private EmptyParams() {
    }
}
