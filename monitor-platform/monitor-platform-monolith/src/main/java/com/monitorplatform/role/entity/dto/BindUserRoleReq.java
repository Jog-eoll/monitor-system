package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
/*

用户绑定角色入参

*/
@Data
public class BindUserRoleReq {

    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotNull(message = "roleId不能为空")
    private Long roleId;
}
