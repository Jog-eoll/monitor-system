package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/*
区域更新入参

*/
@Data
public class RegionUpdateReq {

    @NotNull(message = "id不能为空")
    private Long id;

    @Size(max = 100,message = "名称长度不能超过100")
    private String name;

    @Size(max = 100,message = "编码长度不能超过100")
    private String code;

    private Long parentId;

    private Integer sort;
}
