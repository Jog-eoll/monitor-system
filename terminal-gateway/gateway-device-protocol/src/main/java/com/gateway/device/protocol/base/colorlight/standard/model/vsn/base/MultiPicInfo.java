package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多图片素材配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiPicInfo {
    private Integer picCount;
    private FileSource filePath;
    private Long onePicDuration;
}
