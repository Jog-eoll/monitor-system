package com.gateway.device.protocol.base.colorlight.standard.model.vsn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.Information;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VsnProgram {
    @JsonProperty("programId")
    private String programId;
    @JsonProperty("isBucketProgram")
    private Integer isBucketProgram;
    private Information information;
    private List<VsnPage> pages;
}
