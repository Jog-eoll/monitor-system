package com.monitorplatform.role.service;

import com.monitorplatform.role.entity.SystemParameters;
import com.monitorplatform.role.entity.dto.SystemParametersCreateReq;
import com.monitorplatform.role.entity.dto.SystemParametersPageResp;
import com.monitorplatform.role.entity.dto.SystemParametersUpdateReq;

public interface SystemParametersService {
    SystemParameters create(SystemParametersCreateReq req);
    SystemParameters getById(Long id);
    SystemParameters getByCode(String code);
    SystemParameters update(SystemParametersUpdateReq req);
    void delete(Long id);
    SystemParametersPageResp page(String name, String code, Integer page, Integer pageSize);
}
