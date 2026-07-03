package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.model.params.depend.DimensionedParams;
import lombok.Builder;
import lombok.Data;

/**
 * 文本上传参数 — TEXT_UPLOAD（Nova+JetFileII 共用）。
 *
 * <p>Nova: 使用 text, width, height, textStyle(NovaViplexCoreTextStyle)
 * <br>JetFileII: 使用 text/data, partition, textStyle(NmgTextStyle)</p>
 */
@Data
@Builder
public class TextUploadParams implements DimensionedParams {

    /**
     * 文本内容（字符串模式）
     */
    private String text;

    /**
     * 预建 NMG 二进制数据（二进制模式，兼容旧用法）
     */
    private byte[] data;

    /**
     * 屏宽（Nova 用）
     */
    private Integer width;

    /**
     * 屏高（Nova 用）
     */
    private Integer height;

    /**
     * 目标分区，默认 D 分区。
     */
    @Builder.Default
    private Partition partition = Partition.D;

    /**
     * 文本样式覆盖
     * <br>Nova: NovaViplexCoreTextStyle 对象或 Map
     * <br>JetFileII: NmgTextStyle 对象
     */
    private Object textStyle;
}
