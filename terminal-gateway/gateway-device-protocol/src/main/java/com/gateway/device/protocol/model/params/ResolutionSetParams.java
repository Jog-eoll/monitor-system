package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 显示屏分辨率设置参数 —— DISPLAY_RESOLUTION_SET。
 *
 * <p>SDK: {@code nvSetCustomResolutionAsync}
 * <br>JSON 格式: {@code {"sn":"...","info":{"displayMode":1,"width":1920,"height":1079}}}
 * <br>设置终端显示分辨率（宽高），用于修改 NOVA 显示屏大小。</p>
 */
@Data
@Builder
public class ResolutionSetParams implements CommandParams {

    /**
     * 显示模式，默认 1（自定义分辨率模式）
     */
    @Builder.Default
    private int displayMode = 1;

    /**
     * 屏幕宽度（像素）
     */
    private Integer width;

    /**
     * 屏幕高度（像素）
     */
    private Integer height;
}
