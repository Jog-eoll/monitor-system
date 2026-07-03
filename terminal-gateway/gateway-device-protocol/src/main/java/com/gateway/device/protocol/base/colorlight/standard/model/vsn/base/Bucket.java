package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 桶节目配置，支持 Item 轮流播放。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bucket {
    /**
     * Item 索引列表
     */
    private List<Integer> itemIndexs;
    private Schedule bucketSchedule;
    @JsonProperty("mPlayIndex")
    private Integer mPlayIndex;
}
