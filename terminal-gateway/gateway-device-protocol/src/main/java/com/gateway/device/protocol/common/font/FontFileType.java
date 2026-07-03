package com.gateway.device.protocol.common.font;

import lombok.Getter;
import org.apache.commons.lang3.Strings;

/**
 * 字体文件类型枚举 —— 定义支持的字体文件扩展名。
 * 做大小写无关匹配，覆盖 Windows/Linux 文件系统差异。</p>
 */
@Getter
public enum FontFileType {

    /**
     * JetFileII 点阵字库 (.fnt)
     */
    FNT("fnt"),

    /**
     * TrueType Font 矢量字体 (.ttf)
     */
    TTF("ttf"),

    /**
     * TrueType Collection 矢量字体集合 (.ttc)
     */
    TTC("ttc");

    private final String[] extensions;

    FontFileType(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * 判断文件名是否匹配本类型（扩展名大小写无关）。
     */
    public boolean matches(String fileName) {
        for (String ext : extensions) {
            if (Strings.CI.endsWith(fileName, "." + ext)) {
                return true;
            }
        }
        return false;
    }
}
