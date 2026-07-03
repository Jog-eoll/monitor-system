package com.monitorplatform.role.service.impl;

import com.monitorplatform.role.entity.SystemParameters;
import com.monitorplatform.role.entity.dto.SystemParametersCreateReq;
import com.monitorplatform.role.entity.dto.SystemParametersListItemResp;
import com.monitorplatform.role.entity.dto.SystemParametersPageResp;
import com.monitorplatform.role.entity.dto.SystemParametersUpdateReq;
import com.monitorplatform.role.mapper.SystemParametersMapper;
import com.monitorplatform.role.service.SystemParametersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
/*
系统参数模块接口实现

*/

@Service
public class SystemParametersServiceImpl implements SystemParametersService {

    private final SystemParametersMapper systemParametersMapper;
    public SystemParametersServiceImpl(SystemParametersMapper systemParametersMapper) {
        this.systemParametersMapper = systemParametersMapper;
    }

    //  创建系统模块参数
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemParameters create(SystemParametersCreateReq req) {
        String name = trim(req.getName());
        String code = trim(req.getCode());
        String parameters = trimToEmpty(req.getParameters());
        validateRequired(name, "模块名称不能为空");
        validateRequired(code, "模块编码不能为空");

        if (systemParametersMapper.selectByCode(code) != null) {
            throw new RuntimeException("模块编码已存在：" + code);
        }

        SystemParameters entity = new SystemParameters();
        entity.setName(name);
        entity.setCode(code);
        entity.setParameters(parameters);

        systemParametersMapper.insert(entity);
        return systemParametersMapper.selectById(entity.getId());
    }

    //  根据id查询
    @Override
    public SystemParameters getById(Long id) {
        if (id == null) {
            throw new RuntimeException("模块id不能为空");
        }
        SystemParameters entity = systemParametersMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("模块不存在：" + id);
        }
        return entity;
    }

    //  根据code查询
    @Override
    public SystemParameters getByCode(String code) {
        String normCode = trim(code);
        validateRequired(normCode, "模块编码不能为空");
        SystemParameters entity = systemParametersMapper.selectByCode(normCode);
        if (entity == null) {
            throw new RuntimeException("模块不存在，code=" + normCode);
        }
        return entity;
    }

    //  更新参数
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemParameters update(SystemParametersUpdateReq req) {
        if (req.getId() == null) {
            throw new RuntimeException("模块id不能为空");
        }

        SystemParameters old = systemParametersMapper.selectById(req.getId());

        if (old == null) {
            throw new RuntimeException("模块不存在：" + req.getId());
        }

        String name = trim(req.getName());
        String code = trim(req.getCode());
        String parameters = trimToEmpty(req.getParameters());
        validateRequired(name, "模块名称不能为空");
        validateRequired(code, "模块编码不能为空");
        SystemParameters sameCode = systemParametersMapper.selectByCode(code);

        if (sameCode != null && !sameCode.getId().equals(old.getId())) {
            throw new RuntimeException("模块编码已存在：" + code);
        }

        SystemParameters toUpdate = new SystemParameters();
        toUpdate.setId(old.getId());
        toUpdate.setName(name);
        toUpdate.setCode(code);
        toUpdate.setParameters(parameters);

        int rows = systemParametersMapper.updateById(toUpdate);
        if (rows != 1) {
            throw new RuntimeException("更新模块失败：" + old.getId());
        }

        return systemParametersMapper.selectById(old.getId());
    }

    //  删除参数
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {

        if (id == null) {
            throw new RuntimeException("模块id不能为空");
        }

        SystemParameters old = systemParametersMapper.selectById(id);

        if (old == null) {
            throw new RuntimeException("模块不存在：" + id);
        }

        int rows = systemParametersMapper.deleteById(id);

        if (rows != 1) {
            throw new RuntimeException("删除模块失败：" + id);
        }
    }

    //  分页
    @Override
    public SystemParametersPageResp page(String name, String code, Integer page, Integer pageSize) {

        int pNo = (page == null || page < 1) ? 1 : page;
        int pSize = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        int offset = (pNo - 1) * pSize;
        List<SystemParameters> list = systemParametersMapper.selectPage(trim(name), trim(code), offset, pSize);
        long total = systemParametersMapper.count(trim(name), trim(code));
        List<SystemParametersListItemResp> records = new ArrayList<>();

        for (SystemParameters entity : list) {
            SystemParametersListItemResp item = new SystemParametersListItemResp();
            item.setId(entity.getId());
            item.setName(entity.getName());
            item.setCode(entity.getCode());
            item.setParameters(entity.getParameters());
            item.setCreateTime(entity.getCreateTime());
            item.setUpdateTime(entity.getUpdateTime());
            records.add(item);
        }

        SystemParametersPageResp resp = new SystemParametersPageResp();
        resp.setTotal(total);
        resp.setRecords(records);
        return resp;
    }

    private void validateRequired(String val, String msg) {
        if (val == null || val.trim().isEmpty()) {
            throw new RuntimeException(msg);
        }
    }

    private String trim(String val) {
        return val == null ? null : val.trim();
    }

    private String trimToEmpty(String val) {
        return val == null ? "" : val.trim();
    }
}
