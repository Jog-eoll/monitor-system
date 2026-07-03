package com.gateway.device.core.config.jetfileii;

import com.gateway.device.protocol.base.jetfileii.standard.file.DefaultFileExtensions;
import com.gateway.device.protocol.base.jetfileii.standard.file.JetFileIIFileExtensions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件扩展名过滤配置 — 可按资源类型覆盖默认后缀列表。
 *
 * <pre>
 * device:
 *   jetfileii:
 *     file-extension:
 *       image:
 *         - "*.jpg"
 *         - "*.png"
 *         - "*.bmp"
 *       video:
 *         - "*.mp4"
 *         - "*.avi"
 *       nmg:
 *         - "*.nmg"
 *       pmg:
 *         - "*.pmg"
 *       qst:
 *         - "*.qst"
 * </pre>
 */
@Setter
@Getter
@ConfigurationProperties("device.jetfileii.file-extension")
public class JetFileIIFileExtensionProperties implements JetFileIIFileExtensions {

    /**
     * 图片扩展名，默认 {@link DefaultFileExtensions#IMAGE}
     */
    private List<String> image = new ArrayList<>(DefaultFileExtensions.IMAGE);

    /**
     * 视频扩展名，默认 {@link DefaultFileExtensions#VIDEO}
     */
    private List<String> video = new ArrayList<>(DefaultFileExtensions.VIDEO);

    /**
     * NMG 扩展名，默认 {@link DefaultFileExtensions#NMG}
     */
    private List<String> nmg = new ArrayList<>(DefaultFileExtensions.NMG);

    /**
     * PMG 扩展名，默认 {@link DefaultFileExtensions#PMG}
     */
    private List<String> pmg = new ArrayList<>(DefaultFileExtensions.PMG);

    /**
     * QST 扩展名，默认 {@link DefaultFileExtensions#QST}
     */
    private List<String> qst = new ArrayList<>(DefaultFileExtensions.QST);
}
