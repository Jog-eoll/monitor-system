package com.gateway.device.protocol.common.capability.expand;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.params.*;

/**
 * JetFileII 协议私有能力 —— NMG/PMG/QST 自有格式的文件操作。
 *
 * <p>这些能力仅 JetFileII 协议支持，其他厂商协议不应使用。
 */
public final class JetFileIICapability {
    // ── NMG (JetFileII 自有文本格式) ──
    public static final DeviceCapability<ListQueryParams> NMG_FILE_LIST_QUERY
            = DeviceCapability.of("NMG_FILE_LIST_QUERY", ListQueryParams.class);
    public static final DeviceCapability<MediaUploadParams> NMG_FILE_UPLOAD
            = DeviceCapability.of("NMG_FILE_UPLOAD", MediaUploadParams.class);
    public static final DeviceCapability<FileDownloadParams> NMG_FILE_DOWNLOAD
            = DeviceCapability.of("NMG_FILE_DOWNLOAD", FileDownloadParams.class);
    public static final DeviceCapability<FileDeleteParams> NMG_FILE_DELETE
            = DeviceCapability.of("NMG_FILE_DELETE", FileDeleteParams.class);
    // ── PMG (JetFileII 自有图片格式) ──
    public static final DeviceCapability<ListQueryParams> PMG_FILE_LIST_QUERY
            = DeviceCapability.of("PMG_FILE_LIST_QUERY", ListQueryParams.class);
    public static final DeviceCapability<MediaUploadParams> PMG_FILE_UPLOAD
            = DeviceCapability.of("PMG_FILE_UPLOAD", MediaUploadParams.class);
    public static final DeviceCapability<FileDownloadParams> PMG_FILE_DOWNLOAD
            = DeviceCapability.of("PMG_FILE_DOWNLOAD", FileDownloadParams.class);
    public static final DeviceCapability<FileDeleteParams> PMG_FILE_DELETE
            = DeviceCapability.of("PMG_FILE_DELETE", FileDeleteParams.class);
    // ── QST (JetFileII 自有区域文本格式) ──
    public static final DeviceCapability<ListQueryParams> QST_FILE_LIST_QUERY
            = DeviceCapability.of("QST_FILE_LIST_QUERY", ListQueryParams.class);
    public static final DeviceCapability<MediaUploadParams> QST_FILE_UPLOAD
            = DeviceCapability.of("QST_FILE_UPLOAD", MediaUploadParams.class);
    public static final DeviceCapability<FileDownloadParams> QST_FILE_DOWNLOAD
            = DeviceCapability.of("QST_FILE_DOWNLOAD", FileDownloadParams.class);
    public static final DeviceCapability<FileDeleteParams> QST_FILE_DELETE
            = DeviceCapability.of("QST_FILE_DELETE", FileDeleteParams.class);

    /**
     * 色彩测试
     */
    public static final DeviceCapability<ColorTestParams> COLOR_TEST
            = DeviceCapability.of("COLOR_TEST", ColorTestParams.class);

    private JetFileIICapability() {
    }
}
