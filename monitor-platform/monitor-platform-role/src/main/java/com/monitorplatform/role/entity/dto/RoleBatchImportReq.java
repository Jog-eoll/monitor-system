package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;
/*

批量导入角色入参

*/
@Data
public class RoleBatchImportReq {
    @NotEmpty(message = "导入角色列表不能为空")
    private List<RoleCreateReq> roles;
    private boolean skipDuplicateCode = true;
}