package com.gateway.device.protocol.base.jetfileii.standard.helper;

import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.file.JetFileIIFileExtensions;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;

import java.util.Collections;
import java.util.List;

/**
 * JetFileII 资源映射表 — 集中管理 capability ↔ FileType 和 capability ↔ extensionList 映射。
 *
 * <p>消除 {@code ResourceUploadHandler}、{@code ResourceDownloadHandler} 等处重复的 if-else 链。</p>
 */
public final class JetFileIIResourceMapping {

    private JetFileIIResourceMapping() {
    }

    /**
     * DeviceCapability → JetFileII FileType 映射（upload/download 共用）。
     */
    public static FileType fileTypeOf(DeviceCapability<?> cap) {
        if (cap == CommonDeviceCapability.IMAGE_UPLOAD
                || cap == CommonDeviceCapability.IMAGE_DOWNLOAD
                || cap == CommonDeviceCapability.IMAGE_DELETE) {
            return FileType.PICTURE;
        }
        if (cap == CommonDeviceCapability.VIDEO_UPLOAD
                || cap == CommonDeviceCapability.VIDEO_DOWNLOAD
                || cap == CommonDeviceCapability.VIDEO_DELETE) {
            return FileType.FLW;
        }
        if (cap == CommonDeviceCapability.TEXT_UPLOAD
                || cap == CommonDeviceCapability.TEXT_DOWNLOAD
                || cap == CommonDeviceCapability.TEXT_DELETE) {
            return FileType.TEXT;
        }
        if (cap == JetFileIICapability.PMG_FILE_UPLOAD
                || cap == JetFileIICapability.PMG_FILE_DOWNLOAD
                || cap == JetFileIICapability.PMG_FILE_DELETE) {
            return FileType.ARRAY_PICTURE;
        }
        if (cap == JetFileIICapability.QST_FILE_UPLOAD
                || cap == JetFileIICapability.QST_FILE_DOWNLOAD
                || cap == JetFileIICapability.QST_FILE_DELETE) {
            return FileType.ARRAY_QST;
        }
        if (cap == JetFileIICapability.NMG_FILE_UPLOAD
                || cap == JetFileIICapability.NMG_FILE_DOWNLOAD
                || cap == JetFileIICapability.NMG_FILE_DELETE) {
            return FileType.TEXT;
        }
        throw new IllegalArgumentException("未找到 FileType 映射: " + cap);
    }

    /**
     * DeviceCapability → 扩展名允许列表。
     */
    public static List<String> extensionsOf(DeviceCapability<?> cap, JetFileIIFileExtensions exts) {
        if (cap == CommonDeviceCapability.IMAGE_UPLOAD
                || cap == CommonDeviceCapability.IMAGE_DOWNLOAD
                || cap == CommonDeviceCapability.IMAGE_DELETE
                || cap == CommonDeviceCapability.IMAGE_LIST_QUERY) {
            return exts.getImage();
        }
        if (cap == CommonDeviceCapability.VIDEO_UPLOAD
                || cap == CommonDeviceCapability.VIDEO_DOWNLOAD
                || cap == CommonDeviceCapability.VIDEO_DELETE
                || cap == CommonDeviceCapability.VIDEO_LIST_QUERY) {
            return exts.getVideo();
        }
        if (cap == CommonDeviceCapability.TEXT_UPLOAD
                || cap == CommonDeviceCapability.TEXT_DOWNLOAD
                || cap == CommonDeviceCapability.TEXT_DELETE
                || cap == CommonDeviceCapability.TEXT_LIST_QUERY) {
            return exts.getNmg();
        }
        if (cap == JetFileIICapability.PMG_FILE_UPLOAD
                || cap == JetFileIICapability.PMG_FILE_DOWNLOAD
                || cap == JetFileIICapability.PMG_FILE_DELETE
                || cap == JetFileIICapability.PMG_FILE_LIST_QUERY) {
            return exts.getPmg();
        }
        if (cap == JetFileIICapability.QST_FILE_UPLOAD
                || cap == JetFileIICapability.QST_FILE_DOWNLOAD
                || cap == JetFileIICapability.QST_FILE_DELETE
                || cap == JetFileIICapability.QST_FILE_LIST_QUERY) {
            return exts.getQst();
        }
        if (cap == JetFileIICapability.NMG_FILE_UPLOAD
                || cap == JetFileIICapability.NMG_FILE_DOWNLOAD
                || cap == JetFileIICapability.NMG_FILE_DELETE
                || cap == JetFileIICapability.NMG_FILE_LIST_QUERY) {
            return exts.getNmg();
        }
        return Collections.emptyList();
    }
}
