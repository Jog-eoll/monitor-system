package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 节目分辨率自适应开关参数。
 *
 * <p>{@code enable=true} 开启节目自适应屏幕分辨率，{@code false} 关闭。</p>
 */
@Data
@Builder
public class ProgramAutoScaleParams implements CommandParams {

    /**
     * 开关：{@code true} 开启自适应，{@code false} 关闭。
     */
    private boolean enable;
}
