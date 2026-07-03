package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 全量树同步用节点（可嵌套 children）。
 * id 为空：插入时走自增；id 有值：按该 id 插入（需在全表删除之后、且无重复）。
 */
@Data
public class RegionSnapshotNodeReq {

    private Long id;

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100")
    private String name;

    @NotBlank(message = "编码不能为空")
    @Size(max = 100,message = "编码长度不能超过100")
    private String code;

    private Integer sort;

    @Valid
    private List<RegionSnapshotNodeReq> children = new ArrayList<>();
}
