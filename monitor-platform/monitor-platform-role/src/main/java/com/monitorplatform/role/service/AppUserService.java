package com.monitorplatform.role.service;


import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.entity.dto.AppUserCreateReq;
import com.monitorplatform.role.entity.dto.AppUserPageResp;
import com.monitorplatform.role.entity.dto.AppUserUpdateReq;

//  用户服务
public interface AppUserService {

    AppUser create(AppUserCreateReq req);

    AppUser update(AppUserUpdateReq req);

    void delete(Long id);

    AppUser getById(Long id);

    AppUserPageResp page(String username, Long roleId, Integer page, Integer pageSize);

    AppUser getByUkeyId(String ukeyId);

}
