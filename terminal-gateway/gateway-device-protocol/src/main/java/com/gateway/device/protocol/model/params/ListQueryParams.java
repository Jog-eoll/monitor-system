package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 资源列表查询参数 — 所有 LIST_QUERY。
 *
 * <p>JetFileII: filter(文件扩展名过滤), path(直接路径), partition(分区，默认 D:=3)</p>
 */
@Data
@Builder
public class ListQueryParams implements CommandParams {

    /**
     * 文件扩展名过滤器（String 或 List&lt;String&gt;）
     */
    private Object filter;

    /**
     * 直接查询路径（与 partition 二选一）
     */
    private String path;

    /**
     * 目标分区，默认 D 分区。
     */
    @Builder.Default
    private Partition partition = Partition.D;
}
