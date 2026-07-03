package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 文件下载参数 — IMAGE/VIDEO/TEXT/NMG/PMG/QST DOWNLOAD。
 *
 * <p>Nova: 使用 fileName
 * <br>JetFileII: 使用 remotePath 或 partition + fileName</p>
 */
@Data
@Builder
public class FileDownloadParams implements CommandParams {

    /**
     * 目标文件名含扩展名（Nova+JetFileII 共用），如 {@code default.nmg}
     */
    private String fileName;

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
