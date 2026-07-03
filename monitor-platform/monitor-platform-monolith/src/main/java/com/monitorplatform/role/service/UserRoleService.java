package com.monitorplatform.role.service;


import com.monitorplatform.role.entity.Permission;

import java.util.List;
/*
用户角色服务

*/
public interface UserRoleService {

    void bindUserRole(Long userId, Long roleId);

    List<Permission> listUserPermissions(Long userId);

}
