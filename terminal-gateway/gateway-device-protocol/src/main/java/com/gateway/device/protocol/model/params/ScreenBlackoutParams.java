package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 黑屏控制参数 — SCREEN_BLACKOUT。
 *
 * <p>{@code blackout=true} 黑屏，{@code false} 亮屏，默认 {@code true}。</p>
 */
@Data
@Builder
public class ScreenBlackoutParams implements CommandParams {

    /**
     * 黑屏开关：{@code true} 黑屏，{@code false} 亮屏
     */
    private boolean blackout;
}
