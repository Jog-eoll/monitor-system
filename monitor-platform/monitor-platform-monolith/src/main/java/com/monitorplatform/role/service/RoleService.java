package com.monitorplatform.role.service;


import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.entity.dto.RoleBatchImportResp;
import com.monitorplatform.role.entity.dto.RoleCreateReq;
import com.monitorplatform.role.entity.dto.RolePageResp;
import com.monitorplatform.role.entity.dto.RoleUpdateReq;

import java.util.List;

/**
 * 角色服务
 **/
public interface RoleService {
    Role create(String code, String name, String description, String status, String permissionCodes);
    Role getById(Long id);
    Role update(Long id, String name, String description, String status, String permissionCodes);
    void delete(Long id);
    RolePageResp list(String name, String code, String status, Integer page, Integer pageSize);
    RoleBatchImportResp batchImport(List<RoleCreateReq> roles, boolean skipDuplicateCode);
    void replaceRolePermissions(Long roleId, List<Long> permissionIds);
    Role updateByReq(RoleUpdateReq req);
}
