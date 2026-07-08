package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 节目缩略图查询参数。
 */
@Data
@Builder
public class ProgramThumbnailGetParams implements CommandParams {

    /**
     * 节目名称（文件名），如 {@code Playlist8314}。
     */
    private String programName;
}
