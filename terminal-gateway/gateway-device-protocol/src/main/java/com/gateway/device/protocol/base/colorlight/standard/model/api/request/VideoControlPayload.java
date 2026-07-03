package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/videocontrol 请求体。
 *
 * @deprecated 使用 {@link VideoCommandPayload} 配合
 * {@link ColorLightApi#VIDEO_COMMAND} 替代，
 * 支持更完整的视频控制命令（command/value 参数）
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoControlPayload {

    private String videostatus;
}
