package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
/*
区域创建入参

*/

@Data
public class RegionCreateReq {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100,message = "名称长度不能超过100")
    private String name;

    @NotBlank(message = "编码不能为空")
    @Size(max = 100,message = "编码长度不能超过100")
    private String code;

    private Integer sort;

    private Long parentId;
}
