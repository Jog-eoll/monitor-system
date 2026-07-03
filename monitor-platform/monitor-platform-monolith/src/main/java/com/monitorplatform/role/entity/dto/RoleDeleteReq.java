package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/*

角色删除入参

*/
@Data
public class RoleDeleteReq {
    @NotNull(message = "角色id不能为空")
    private Long id;
}
