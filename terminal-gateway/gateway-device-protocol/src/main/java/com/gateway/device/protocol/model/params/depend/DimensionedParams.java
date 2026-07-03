package com.gateway.device.protocol.model.params.depend;

import com.gateway.device.protocol.model.params.MediaMultiUploadParams;
import com.gateway.device.protocol.model.params.MediaUploadParams;
import com.gateway.device.protocol.model.params.TextUploadParams;

/**
 * 携带画面尺寸的参数接口 —— 替代 {@code AbstractNovaViplexCoreHandler.resolveDimension()}。
 *
 * <p>实现类：{@link MediaUploadParams}、{@link TextUploadParams}、{@link MediaMultiUploadParams}</p>
 */
public interface DimensionedParams extends CommandParams {

    Integer getWidth();

    Integer getHeight();

    /**
     * 解析宽度：优先取 params 值，无值时回退到 fallback
     */
    default int resolveWidth(Integer fallback) {
        return getWidth() != null ? getWidth() : (fallback != null ? fallback : 0);
    }

    /**
     * 解析高度：优先取 params 值，无值时回退到 fallback
     */
    default int resolveHeight(Integer fallback) {
        return getHeight() != null ? getHeight() : (fallback != null ? fallback : 0);
    }
}
