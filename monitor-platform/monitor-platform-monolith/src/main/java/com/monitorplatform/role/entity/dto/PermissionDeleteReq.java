package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
/*
权限删除入参

*/

@Data
public class PermissionDeleteReq {

    @NotNull(message = "id不能为空")
    private Long id;
}
