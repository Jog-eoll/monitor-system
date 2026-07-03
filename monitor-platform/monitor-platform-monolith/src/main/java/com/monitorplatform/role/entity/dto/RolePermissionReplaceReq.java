package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/*

权限配置入参

*/
@Data
public class RolePermissionReplaceReq {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    /** 最终权限 id 列表；null 或空列表表示清空该角色权限 */
    private List<Long> permissionIds;
}
