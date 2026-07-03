package com.monitorplatform.role.service.impl;

import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.entity.dto.*;
import com.monitorplatform.role.mapper.RoleMapper;
import com.monitorplatform.role.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/*

角色服务实现

*/
@Service
public class RoleServiceImpl implements RoleService {
    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    //  角色创建
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role create(String code, String name, String description, String status,String permissionCodes ) {

        //  校验是否为空
        validateRequired(code, "角色编码不能为空");
        validateRequired(name,"角色名称不能为空");
        validateStatus(status);

        //  判断是否已经存在code
        String normCode = code.trim();
        if (roleMapper.selectByCode(normCode) != null) {
            throw new RuntimeException("角色编码已存在：" + normCode);
        }

        Role r = new Role();
        r.setCode(normCode);
        r.setName(name.trim());
        r.setDescription(description == null ? null : description.trim());
        r.setStatus(status == null || status.trim().isEmpty() ? "ENABLED" : status.trim());
        r.setPermissionCodes(permissionCodes == null ? "" : permissionCodes.trim());
        roleMapper.insert(r);
        return roleMapper.selectById(r.getId());
    }

    //  角色更新
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role update(Long id,String name,String description,String status,String permissionCodes ){

        if (id == null) {
            throw new RuntimeException("角色ID不能为空");
        }

        Role old = roleMapper.selectById(id);
        if (old == null) {
            throw new RuntimeException("角色不存在："+id);
        }

        validateRequired(name,"角色名称不能为空");
        validateStatus(status);

        Role toUpdate = new Role();
        toUpdate.setId(id);
        toUpdate.setName(name.trim());
        toUpdate.setDescription(description == null ? null : description.trim());
        toUpdate.setStatus((status == null || status.trim().isEmpty()) ? old.getStatus() : status.trim());
        toUpdate.setPermissionCodes(permissionCodes == null ? old.getPermissionCodes() : permissionCodes.trim());

        int rows = roleMapper.updateById(toUpdate);
        if(rows != 1){
            throw new RuntimeException("更新角色失败："+id);
        }
        return roleMapper.selectById(id);
    }

    //  更新
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role updateByReq(RoleUpdateReq req) {
        if(req.getId()==null){
            throw new RuntimeException("角色ID不能为空");
        }

        Role old = roleMapper.selectById(req.getId());
        if (old == null) {
            throw new RuntimeException("角色不存在："+req.getId());
        }

        //  赋值
        String name = req.getName() == null ? old.getName() : req.getName().trim();
        String code = req.getCode() == null ? old.getCode() : req.getCode().trim();
        String desc = req.getDescription() == null ? old.getDescription() : req.getDescription().trim();
        String status = req.getStatus() == null ? old.getStatus() : req.getStatus().trim();
        String permissionCodes = req.getPermissionCodes() == null ? old.getPermissionCodes() : req.getPermissionCodes().trim();

        //  校验
        validateRequired(name,"角色名称不能为空");
        validateRequired(code, "角色编码不能为空");
        validateStatus(status);

        Role sameCode = roleMapper.selectByCode(code);
        if(sameCode!=null && !sameCode.getId().equals(old.getId())){
            throw new RuntimeException("角色编码已存在："+code);
        }

        //  更新库
        Role toUpdate = new Role();
        toUpdate.setId(old.getId());
        toUpdate.setCode(code);
        toUpdate.setName(name);
        toUpdate.setDescription(desc);
        toUpdate.setStatus(status);
        toUpdate.setPermissionCodes(permissionCodes);

        roleMapper.updateById(toUpdate);
        return roleMapper.selectById(old.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id){
        if(id == null) {
            throw new RuntimeException("角色ID不能为空");
        }

        Role old = roleMapper.selectById(id);
        if (old == null) {
            throw new RuntimeException("角色不存在："+id);
        }
        int userCount = roleMapper.countUsersByRoleId(id);
        if (userCount > 0) {
            throw new RuntimeException("该角色已关联用户，无法删除");
        }
        roleMapper.deleteById(id);
    }

    //  角色查询
    @Override
    public Role getById(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) {
            throw new RuntimeException("角色不存在：" + id);
        }
        return r;
    }

    //  角色分页查询
    @Override
    public RolePageResp list(String name, String code, String status, Integer pageNo, Integer pageSize){
        int pNo = (pageNo==null||pageNo<1)?1:pageNo;
        int pSize = (pageSize==null||pageSize<1)?10:Math.min(pageSize,100);
        int offset = (pNo-1)*pSize;

        List<Role> roles = roleMapper.selectPage(name,code,status,offset,pSize);
        long total = roleMapper.count(name,code,status);

        List<RoleListItemResp> records = new ArrayList<>();
        for (Role role : roles) {
            RoleListItemResp item = new RoleListItemResp();
            item.setId(role.getId());
            item.setName(role.getName());
            item.setCode(role.getCode());
            item.setDescription(role.getDescription());
            item.setStatus(role.getStatus());
            item.setCreateTime(role.getCreateTime());
            item.setUpdateTime(role.getUpdateTime());
            item.setPermissionCodes(role.getPermissionCodes());
            item.setPermissionCount(roleMapper.countPermissionsByRoleId(role.getId()));
            item.setUserCount(roleMapper.countUsersByRoleId(role.getId()));
            records.add(item);
        }

        RolePageResp resp = new RolePageResp();
        resp.setRecords(records);
        resp.setTotal(total);
        return  resp;

    }

    //  批量导入角色
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleBatchImportResp batchImport(List<RoleCreateReq> roles, boolean skipDuplicateCode) {
        if (roles == null || roles.isEmpty()) {
            throw new RuntimeException("导入数据不能为空");
        }
        int success = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            RoleCreateReq req = roles.get(i);
            try {
                validateRequired(req.getCode(), "角色编码不能为空");
                validateRequired(req.getName(), "角色名称不能为空");
                validateStatus(req.getStatus());
                String code = req.getCode().trim();
                if (roleMapper.selectByCode(code) != null) {
                    if (skipDuplicateCode) {
                        skipped++;
                        continue;
                    }
                    throw new RuntimeException("角色编码重复：" + code);
                }
                create(code, req.getName(), req.getDescription(), req.getStatus(), req.getPermissionCodes());
                success++;
            } catch (Exception e) {
                errors.add("第" + (i + 1) + "条失败: " + e.getMessage());
            }
        }
        RoleBatchImportResp resp = new RoleBatchImportResp();
        resp.setTotal(roles.size());
        resp.setSuccess(success);
        resp.setSkipped(skipped);
        resp.setFailed(roles.size() - success - skipped);
        resp.setErrors(errors);
        return resp;
    }

    //  替换角色权限
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) throw new RuntimeException("角色ID不能为空");
        Role role = roleMapper.selectById(roleId);
        if (role == null) throw new RuntimeException("角色不存在：" + roleId);
        String permissionCodes = "";
        if (permissionIds != null && !permissionIds.isEmpty()) {
            permissionCodes = permissionIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
        roleMapper.updatePermissionCodesById(roleId, permissionCodes);
    }

    //  检验是否为空
    private void validateRequired(String val, String msg) {
        if (val == null || val.trim().isEmpty()) {
            throw new RuntimeException(msg);
        }
    }

    //  状态检验
    private void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return; // create时允许为空，默认ENABLED
        }
        String s = status.trim();
        if (!"ENABLED".equals(s) && !"DISABLED".equals(s)) {
            throw new RuntimeException("状态只能是 ENABLED 或 DISABLED");
        }
    }
}
