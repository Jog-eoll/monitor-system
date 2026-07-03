package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.PathType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件资源定位信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSource {
    /**
     * 是否相对路径: 0=绝对路径, 1=相对路径
     */
    @Builder.Default
    private PathType isRelative = PathType.RELATIVE;
    /**
     * 文件路径
     */
    private String filePath;
    /**
     * 文件 MD5 码（32位）
     */
    private String md5;
    /**
     * 原始文件名
     */
    private String originName;
    /**
     * 文件转换路径
     */
    private String convertPath;
}
