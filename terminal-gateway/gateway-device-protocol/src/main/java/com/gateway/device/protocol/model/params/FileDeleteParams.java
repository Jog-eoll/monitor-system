package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 文件删除参数 — 所有 DELETE（除 MEDIA_CLEAR）。
 *
 * <p>JetFileII: partition + fileName</p>
 */
@Data
@Builder
public class FileDeleteParams implements CommandParams {

    /**
     * 目标分区，默认 D 分区。
     */
    @Builder.Default
    private Partition partition = Partition.D;

    /**
     * 设备端文件名（必填），如 {@code default.nmg}
     */
    private String fileName;
}
