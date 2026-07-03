package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/*

角色更新入参
*/
@Data
public class RoleUpdateReq {
    @NotNull(message = "角色id不能为空")
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100,message = "角色名称最长为100")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    @Size(max=64,message = "角色编码最长64")
    private String code;

    @Size(max = 20,message = "状态最长20")
    private String status;

    @Size(max = 255,message = "角色描述最长255")
    private String description;

    private String permissionCodes;
}
