package com.gateway.device.protocol.base.colorlight.standard.model.vsn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.Bucket;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.DisplayRect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VsnRegion {
    private String name;
    @JsonProperty("isScheduleRegion")
    private Integer isScheduleRegion;
    private DisplayRect rect;
    private List<VsnItem> items;
    @JsonProperty("buckets")
    private List<Bucket> buckets;
    private Integer layer;
}
