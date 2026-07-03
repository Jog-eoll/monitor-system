package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 屏幕旋转方向参数 — SCREEN_ORIENTATION_SET。
 *
 * <p>orientation 取值: "0"=0°, "1"=90°, "2"=180°, "3"=270°</p>
 */
@Data
@Builder
public class ScreenOrientationParams implements CommandParams {
    private String orientation;
}
