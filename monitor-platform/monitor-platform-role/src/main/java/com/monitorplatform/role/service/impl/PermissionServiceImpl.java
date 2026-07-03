package com.monitorplatform.role.service.impl;

import com.monitorplatform.role.entity.Permission;
import com.monitorplatform.role.entity.dto.PermissionCreateReq;
import com.monitorplatform.role.entity.dto.PermissionPageResp;
import com.monitorplatform.role.entity.dto.PermissionTreeNodeResp;
import com.monitorplatform.role.entity.dto.PermissionUpdateReq;
import com.monitorplatform.role.mapper.PermissionMapper;
import com.monitorplatform.role.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/*

权限接口实现

*/
@Service
public class PermissionServiceImpl implements PermissionService {

    //  菜单类型定义
    private static final String TYPE_MENU = "MENU";
    private static final String TYPE_BUTTON = "BUTTON";

    private final PermissionMapper permissionMapper;

    public PermissionServiceImpl(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    //  权限创建接口实现
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission create(PermissionCreateReq req) {

        normalizeCreateReq(req);
        validateBusiness(req.getType(), req.getName(), req.getCode(), req.getRouteUrl());

        if (permissionMapper.selectByCode(req.getCode()) != null) {
            throw new RuntimeException("标识已存在：" + req.getCode());
        }

        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        int targetSort = normalizeTargetSort(parentId, req.getSort(), null);

        permissionMapper.increaseSortFrom(parentId, targetSort);

        Permission p = new Permission();
        p.setType(req.getType());
        p.setCode(req.getCode());
        p.setName(req.getName());
        p.setRouteUrl(req.getRouteUrl());
        p.setPluginUrl(req.getPluginUrl());
        p.setIconUrl(req.getIconUrl());
        p.setSort(targetSort);
        p.setIsEnable(req.getIsEnable() == null || req.getIsEnable().trim().isEmpty() ? "ENABLED" : req.getIsEnable().trim());
        p.setParentId(parentId);
        permissionMapper.insert(p);

        return permissionMapper.selectById(p.getId());
    }

    //  更新权限
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission update(PermissionUpdateReq req) {

        Permission old = permissionMapper.selectById(req.getId());

        if (old == null) {
            throw new RuntimeException("记录不存在：" + req.getId());
        }


        String newType = trimOrDefault(req.getType(), old.getType());
        String newName = trimOrDefault(req.getName(), old.getName());
        String newCode = trimOrDefault(req.getCode(), old.getCode());
        String newRouteUrl = trimOrDefault(req.getRouteUrl(), old.getRouteUrl());
        String newPluginUrl = trimOrDefault(req.getPluginUrl(), old.getPluginUrl());
        String newIconUrl = trimOrDefault(req.getIconUrl(), old.getIconUrl());
        String newEnable = trimOrDefault(req.getIsEnable(), old.getIsEnable());
        Long newParentId = req.getParentId() == null ? old.getParentId() : req.getParentId();

        validateBusiness(newType, newName, newCode, newRouteUrl);

        Permission sameCode = permissionMapper.selectByCode(newCode);

        if (sameCode != null && !sameCode.getId().equals(old.getId())) {
            throw new RuntimeException("标识已存在: " + newCode);
        }

        Integer rawNewSort = req.getSort() == null ? old.getSort() : req.getSort();
        int newSort = normalizeTargetSort(newParentId, rawNewSort, old.getId());

        if (Objects.equals(old.getParentId(), newParentId)) {
            if (newSort > old.getSort()) {
                permissionMapper.decreaseSortRange(newParentId, old.getSort(), newSort);
            } else if (newSort < old.getSort()) {
                permissionMapper.increaseSortRange(newParentId, old.getSort(), newSort);
            }
        } else {
            permissionMapper.decreaseSortAfter(old.getParentId(), old.getSort());
            permissionMapper.increaseSortFrom(newParentId, newSort);
        }

        Permission toUpdate = new Permission();
        toUpdate.setId(old.getId());
        toUpdate.setType(newType);
        toUpdate.setName(newName);
        toUpdate.setCode(newCode);
        toUpdate.setRouteUrl(newRouteUrl);
        toUpdate.setPluginUrl(newPluginUrl);
        toUpdate.setIconUrl(newIconUrl);
        toUpdate.setSort(newSort);
        toUpdate.setIsEnable(newEnable);
        toUpdate.setParentId(newParentId);

        permissionMapper.updateById(toUpdate);

        return permissionMapper.selectById(old.getId());
    }

    //  删除权限
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Permission old = permissionMapper.selectById(id);

        if (old == null) {
            throw new RuntimeException("记录不存在：" + id);
        }

        List<Permission> children = permissionMapper.selectByParentId(id);

        if (!children.isEmpty()) {
            throw new RuntimeException("请先删除子节点");
        }
        permissionMapper.deleteById(id);
        permissionMapper.decreaseSortAfter(old.getParentId(), old.getSort());
    }

    //  查询单个记录
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission getById(Long id) {
        Permission p = permissionMapper.selectById(id);
        if (p == null) {
            throw new RuntimeException("记录不存在："+id);
        }
        return p;
    }


    //  权限树形结构分页接口
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionPageResp pageTree(Integer page, Integer pageSize) {

        //  初始化分页参数
        int pNo = (page==null||page<1)?1:page;
        int pSize = (pageSize==null||pageSize<1)?10:Math.min(pageSize,100);

        List<Permission> all = permissionMapper.selectAllOrderByTree();
        Map<Long, PermissionTreeNodeResp> map = new LinkedHashMap<>();
        List<PermissionTreeNodeResp> roots = new ArrayList<>();


        for (Permission p : all) {
            PermissionTreeNodeResp node = toNode(p);
            map.put(node.getId(), node);
        }
        for (Permission p : all) {
            PermissionTreeNodeResp node = map.get(p.getId());
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            if (parentId == 0L) {
                roots.add(node);
            } else {
                PermissionTreeNodeResp parent = map.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        long total = roots.size();
        int from = (pNo - 1) * pSize;
        int to = Math.min(from + pSize, roots.size());
        List<PermissionTreeNodeResp> pageList = from >= roots.size() ? Collections.emptyList() : roots.subList(from, to);
        PermissionPageResp resp = new PermissionPageResp();
        resp.setTotal(total);
        resp.setRecords(pageList);
        return resp;
    }

    //  转化为节点
    private PermissionTreeNodeResp toNode(Permission p) {
        PermissionTreeNodeResp n = new PermissionTreeNodeResp();
        n.setId(p.getId());
        n.setType(p.getType());
        n.setName(p.getName());
        n.setCode(p.getCode());
        n.setRouteUrl(p.getRouteUrl());
        n.setPluginUrl(p.getPluginUrl());
        n.setIconUrl(p.getIconUrl());
        n.setSort(p.getSort());
        n.setIsEnable(p.getIsEnable());
        n.setParentId(p.getParentId());
        return n;
    }


    //  去空处理
    private void normalizeCreateReq(PermissionCreateReq req) {
        req.setType(trim(req.getType()));
        req.setName(trim(req.getName()));
        req.setCode(trim(req.getCode()));
        req.setRouteUrl(trim(req.getRouteUrl()));
        req.setPluginUrl(trim(req.getPluginUrl()));
        req.setIconUrl(trim(req.getIconUrl()));
        req.setIsEnable(trim(req.getIsEnable()));
    }

    //  必填校验
    private void validateBusiness(String type, String name, String code, String routeUrl) {
        if (!TYPE_MENU.equals(type) && !TYPE_BUTTON.equals(type)) {
            throw new RuntimeException("菜单类型只能是 MENU 或 BUTTON");
        }
        if (isBlank(name)) throw new RuntimeException("名称不能为空");
        if (isBlank(code)) throw new RuntimeException("标识不能为空");
        if (TYPE_MENU.equals(type) && isBlank(routeUrl)) {
            throw new RuntimeException("菜单类型为MENU时，路由地址不能为空");
        }
    }

    //  排序校验
    private int normalizeTargetSort(Long parentId, Integer reqSort, Long selfId) {
        List<Permission> siblings = permissionMapper.selectByParentId(parentId);
        int siblingCount = siblings.size();
        if (selfId != null) {
            for (Permission s : siblings) {
                if (selfId.equals(s.getId())) {
                    siblingCount = siblingCount - 1;
                    break;
                }
            }
        }
        int max = siblingCount + 1;
        int sort = (reqSort == null || reqSort < 1) ? max : reqSort;
        return Math.min(sort, max);
    }

    //  默认赋值
    private String trimOrDefault(String val, String def) {
        if (val == null) return def;
        return val.trim();
    }

    //  去空
    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    //  判断是否含空字符串
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }


}
