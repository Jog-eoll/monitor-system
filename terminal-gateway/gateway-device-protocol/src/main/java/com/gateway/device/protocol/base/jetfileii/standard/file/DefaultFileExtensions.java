package com.gateway.device.protocol.base.jetfileii.standard.file;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 文件扩展名过滤常量 — 按资源类型分组的默认后缀列表。
 */
public final class DefaultFileExtensions {

    public static final List<String> IMAGE =
            Collections.unmodifiableList(Arrays.asList("*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"));

    public static final List<String> VIDEO =
            Collections.unmodifiableList(Arrays.asList("*.mp4", "*.flv", "*.avi"));

    public static final List<String> NMG =
            Collections.singletonList("*.nmg");

    public static final List<String> PMG =
            Collections.singletonList("*.pmg");

    public static final List<String> QST =
            Collections.singletonList("*.qst");

    private DefaultFileExtensions() {
    }
}
