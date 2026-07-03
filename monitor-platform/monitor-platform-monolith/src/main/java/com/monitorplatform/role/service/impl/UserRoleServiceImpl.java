package com.monitorplatform.role.service.impl;


import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.entity.Permission;
import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.mapper.AppUserMapper;
import com.monitorplatform.role.mapper.PermissionMapper;
import com.monitorplatform.role.mapper.RoleMapper;
import com.monitorplatform.role.service.UserRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
/*
用户角色服务接口实现

*/
@Service
public class UserRoleServiceImpl implements UserRoleService {

    private final AppUserMapper appUserMapper;

    private final RoleMapper roleMapper;

    private final PermissionMapper permissionMapper;

    //  构造函数
    public UserRoleServiceImpl(AppUserMapper appUserMapper, RoleMapper roleMapper, PermissionMapper permissionMapper) {
        this.appUserMapper = appUserMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    //  用户绑定角色
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindUserRole(Long userId, Long roleId) {

        AppUser user = appUserMapper.selectById(userId);

        if (user == null) throw new RuntimeException("用户不存在：" + userId);
        Role role = roleMapper.selectById(roleId);

        if (role == null) throw new RuntimeException("角色不存在：" + roleId);

        appUserMapper.updateRoleId(userId, roleId, role.getName());
    }


    //  查询用户权限
    @Override
    @Transactional(readOnly = true)
    public List<Permission> listUserPermissions(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在：" + userId);
        if (user.getRoleId() == null) return Collections.emptyList();
        return permissionMapper.selectByRoleId(user.getRoleId());
    }

}
