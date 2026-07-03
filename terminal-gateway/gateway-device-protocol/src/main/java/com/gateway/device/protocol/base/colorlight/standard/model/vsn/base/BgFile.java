package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目页背景文件。
 *
 * <p>直接包含文件定位字段（非嵌套 FileSource），
 * 与 VSN JSON 结构一致。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgFile {
    /**
     * 是否相对路径: 0=绝对, 1=相对
     */
    @Builder.Default
    private Integer isRelative = 0;
    /**
     * 文件路径
     */
    private String filePath;
    /**
     * 文件 MD5（32位）
     */
    private String md5;
}
