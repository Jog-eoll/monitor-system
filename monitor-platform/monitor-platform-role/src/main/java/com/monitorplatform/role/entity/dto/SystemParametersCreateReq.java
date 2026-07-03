package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/*
系统模块参数创建入参

*/
@Data
public class SystemParametersCreateReq {

    // 模块名称
    @NotBlank(message = "模块名称不能为空")
    @Size(max = 100, message = "模块名称最长为100")
    private String name;

    // 模块编码
    @NotBlank(message = "模块编码不能为空")
    @Size(max = 64, message = "模块编码最长64")
    private String code;

    // 模块参数（JSON字符串）
    private String parameters;
}
