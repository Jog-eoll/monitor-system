package com.gateway.device.protocol.common.file;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FilenameUtils;

import java.util.List;

/**
 * 文件扩展名校验工具 — 大小写不敏感的扩展名匹配。
 *
 * <p>供 {@code ResourceUploadHandler}、{@code MediaMultiUploadHandler} 等 handler 共用。</p>
 */
public final class ExtensionValidator {

    private static final CharMatcher EXT_PREFIX = CharMatcher.anyOf("*.");

    private ExtensionValidator() {
    }

    /**
     * 检查文件扩展名是否在允许列表中（大小写不敏感）。
     *
     * @param fileName    文件名（含扩展名，如 {@code image.jpg}）
     * @param allowedList 允许的扩展名列表（格式如 {@code *.jpg}、{@code mp4}），空列表放行
     * @return true 如果扩展名命中允许列表或列表为空
     */
    public static boolean isAllowed(String fileName, List<String> allowedList) {
        String suffix = FilenameUtils.getExtension(fileName);
        if (suffix.isEmpty()) return false;
        if (allowedList == null || allowedList.isEmpty()) return true;
        String lowerSuffix = suffix.toLowerCase();
        return Iterables.any(allowedList, allowed -> allowed != null
                && lowerSuffix.equals(EXT_PREFIX.trimLeadingFrom(allowed).toLowerCase()));
    }
}
