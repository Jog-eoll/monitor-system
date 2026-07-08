package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.model.params.depend.DimensionedParams;
import lombok.Builder;
import lombok.Data;

/**
 * 媒体上传参数 — IMAGE_UPLOAD, VIDEO_UPLOAD, NMG/PMG/QST_FILE_UPLOAD。
 *
 * <p>Nova: 使用 data, fileName, width, height（节目管线）
 * <br>JetFileII: 使用 data, chunkSize, remotePath 或 partition + fileName</p>
 */
@Data
@Builder
public class MediaUploadParams implements DimensionedParams {

    /**
     * 文件二进制内容（必填）
     */
    private byte[] data;

    /**
     * 目标文件名含扩展名（Nova+JetFileII 共用），如 {@code image.jpg}
     */
    private String fileName;

    /**
     * 屏宽（Nova 用，可选回退到设备宽）
     */
    private Integer width;

    /**
     * 屏高（Nova 用，可选回退到设备高）
     */
    private Integer height;

    /**
     * 传输块大小（JetFileII 用）
     */
    @Builder.Default
    private int chunkSize = ProtocolConst.DEFAULT_CHUNK_SIZE;

    /**
     * 远端完整路径（JetFileII 用，与 partition + fileName 二选一）
     */
    private String remotePath;

    /**
     * 目标分区，默认 D 分区。
     */
    @Builder.Default
    private Partition partition = Partition.D;
}
