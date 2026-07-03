package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
/*
系统模块参数删除入参

*/

@Data
public class SystemParametersDeleteReq {

    @NotNull(message = "模块id不能为空")
    private Long id;
}
