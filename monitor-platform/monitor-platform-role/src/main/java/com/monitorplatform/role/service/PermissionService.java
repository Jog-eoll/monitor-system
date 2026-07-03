package com.monitorplatform.role.service;

import com.monitorplatform.role.entity.Permission;
import com.monitorplatform.role.entity.dto.PermissionCreateReq;
import com.monitorplatform.role.entity.dto.PermissionPageResp;
import com.monitorplatform.role.entity.dto.PermissionUpdateReq;

/*
权限服务

*/

public interface PermissionService {

    Permission create(PermissionCreateReq req);

    Permission update(PermissionUpdateReq req);

    void delete(Long id);

    Permission getById(Long id);

    PermissionPageResp pageTree(Integer page, Integer pageSize);
}
