package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class RegionTreeReplaceReq {

    @NotEmpty(message = "roots不能为空")
    @Valid
    private List<RegionSnapshotNodeReq> roots;
}
