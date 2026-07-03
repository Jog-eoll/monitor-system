package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * AP 热点开关参数 — AP_NETWORK_SWITCH。
 *
 * <p>{@code enable=true} 开启 AP 热点，{@code false} 关闭。</p>
 */
@Data
@Builder
public class ApNetworkSwitchParams implements CommandParams {

    /**
     * AP 热点开关：{@code true} 开启，{@code false} 关闭
     */
    private boolean enable;
}
