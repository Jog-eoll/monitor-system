package com.monitorplatform.role.service.impl;

import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.entity.dto.AppUserCreateReq;
import com.monitorplatform.role.entity.dto.AppUserListItemResp;
import com.monitorplatform.role.entity.dto.AppUserPageResp;
import com.monitorplatform.role.entity.dto.AppUserUpdateReq;
import com.monitorplatform.role.mapper.AppUserMapper;
import com.monitorplatform.role.mapper.RoleMapper;
import com.monitorplatform.role.service.AppUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
用户服务实现
*/

@Service
public class AppUserServiceImpl implements AppUserService {


    private final AppUserMapper appUserMapper;

    private final RoleMapper roleMapper;

    public AppUserServiceImpl(AppUserMapper appUserMapper, RoleMapper roleMapper) {
        this.appUserMapper = appUserMapper;
        this.roleMapper = roleMapper;
    }

    //  用户创建
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser create(AppUserCreateReq req){

        String username = trim(req.getUsername());

        validateRequired(username,"用户名不能为空");

        if (appUserMapper.selectByUsername(username) != null){
            throw new RuntimeException("用户名已存在："+username);
        }
        String ukeyId = trimToNull(req.getUkeyId());
        if (ukeyId != null) {
            AppUser ukeyDup = appUserMapper.selectByUkeyId(ukeyId);
            if (ukeyDup != null) {
                throw new RuntimeException("UKey已被绑定：" + ukeyId);
            }
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(req.getPassword()==null?"":req.getPassword().trim());
        user.setDescription(trimToNull(req.getDescription()));
        user.setUkeyId(ukeyId);
        user.setEmployeeNo(trimToNull(req.getEmployeeNo()));
        user.setPost(trimToNull(req.getPost()));
        user.setAge(req.getAge());
        user.setGender(trimToNull(req.getGender()));
        user.setAvatarUrl(trimToNull(req.getAvatarUrl()));
        user.setPhone(trimToNull(req.getPhone()));
        user.setIsAllowChange(req.getIsAllowChange() == null ? 1 : req.getIsAllowChange());
        fillRole(user,req.getRoleId());

        appUserMapper.insert(user);
        return appUserMapper.selectById(user.getId());
    }

    //  用户更新
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser update(AppUserUpdateReq req){
        if(req.getId()==null){
            throw new RuntimeException("用户ID不能为空");
        }

        AppUser old = appUserMapper.selectById(req.getId());

        if (old == null) {
            throw new RuntimeException("用户不存在：" + req.getId());
        }

        if (old.getIsAllowChange() != null && old.getIsAllowChange() == 0) {
            throw new RuntimeException("当前用户不允许修改");
        }

        String newUsername = req.getUsername() == null ? old.getUsername() : trim(req.getUsername());
        validateRequired(newUsername, "用户名不能为空");

        if (!newUsername.equals(old.getUsername())) {
            AppUser dup = appUserMapper.selectByUsername(newUsername);
            if (dup != null && !dup.getId().equals(old.getId())) {
                throw new RuntimeException("用户名已存在：" + newUsername);
            }
        }
        String newUkeyId = req.getUkeyId() == null ? old.getUkeyId() : trimToNull(req.getUkeyId());
        if (newUkeyId != null) {
            AppUser ukeyDup = appUserMapper.selectByUkeyId(newUkeyId);
            if (ukeyDup != null && !ukeyDup.getId().equals(old.getId())) {
                throw new RuntimeException("UKey已被绑定：" + newUkeyId);
            }
        }

        AppUser toUpdate = new AppUser();
        toUpdate.setId(old.getId());
        toUpdate.setUsername(newUsername);

        if (req.getPassword() != null && !req.getPassword().trim().isEmpty()) {
            toUpdate.setPassword(req.getPassword().trim());
        } else {
            toUpdate.setPassword(old.getPassword());
        }

        toUpdate.setDescription(req.getDescription() == null ? old.getDescription() : trimToNull(req.getDescription()));
        toUpdate.setUkeyId(newUkeyId);
        toUpdate.setEmployeeNo(req.getEmployeeNo() == null ? old.getEmployeeNo() : trimToNull(req.getEmployeeNo()));
        toUpdate.setPost(req.getPost() == null ? old.getPost() : trimToNull(req.getPost()));
        toUpdate.setAge(req.getAge() == null ? old.getAge() : req.getAge());
        toUpdate.setGender(req.getGender() == null ? old.getGender() : trimToNull(req.getGender()));
        toUpdate.setAvatarUrl(req.getAvatarUrl() == null ? old.getAvatarUrl() : trimToNull(req.getAvatarUrl()));
        toUpdate.setPhone(req.getPhone() == null ? old.getPhone() : trimToNull(req.getPhone()));
        toUpdate.setIsAllowChange(req.getIsAllowChange() == null ? old.getIsAllowChange() : req.getIsAllowChange());
        Long newRoleId = req.getRoleId() == null ? old.getRoleId() : req.getRoleId();
        fillRole(toUpdate, newRoleId);

        int rows = appUserMapper.updateById(toUpdate);

        if (rows != 1) {
            throw new RuntimeException("更新用户失败：" + req.getId());
        }

        return appUserMapper.selectById(req.getId());
    }

    //  删除用户
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        AppUser old = appUserMapper.selectById(id);
        if (old == null) {
            throw new RuntimeException("用户不存在：" + id);
        }
        appUserMapper.deleteById(id);
    }

    //  查询用户
    @Override
    public AppUser getById(Long id) {
        AppUser u = appUserMapper.selectById(id);
        if (u == null) {
            throw new RuntimeException("用户不存在：" + id);
        }
        return u;
    }

    //  通过ukeyId查询用户
    @Override
    public AppUser getByUkeyId(String ukeyId){
        String key = trimToNull(ukeyId);
        if (key == null) {
            throw new RuntimeException("ukeyId不能为空");
        }

        //  查询是否有绑定
        AppUser user = appUserMapper.selectByUkeyId(key);
        if (user == null) {
            throw new RuntimeException("用户不存在：ukeyId:" + ukeyId);
        }
        return user;
    }

    //  用户分页
    @Override
    public AppUserPageResp page(String username, Long roleId, Integer pageNo, Integer pageSize) {
        int pNo = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int pSize = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        int offset = (pNo - 1) * pSize;
        String nameLike = username == null || username.trim().isEmpty() ? null : username.trim();
        List<AppUser> list = appUserMapper.selectPage(nameLike, roleId, offset, pSize);
        long total = appUserMapper.count(nameLike, roleId);
        List<AppUserListItemResp> records = new ArrayList<>();
        for (AppUser u : list) {
            records.add(toListItem(u));
        }
        AppUserPageResp resp = new AppUserPageResp();
        resp.setTotal(total);
        resp.setRecords(records);
        return resp;
    }

    //  查找角色名
    private void fillRole(AppUser u, Long roleId) {
        u.setRoleId(roleId);
        if (roleId == null) {
            u.setRoleName(null);
            return;
        }
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new RuntimeException("角色不存在：" + roleId);
        }
        u.setRoleName(role.getName());
    }


    //  转换item
    private AppUserListItemResp toListItem(AppUser u) {
        AppUserListItemResp r = new AppUserListItemResp();
        r.setId(u.getId());
        r.setUsername(u.getUsername());
        r.setRoleId(u.getRoleId());
        r.setRoleName(u.getRoleName());
        r.setDescription(u.getDescription());
        r.setCreateTime(u.getCreateTime());
        r.setUpdateTime(u.getUpdateTime());
        r.setUkeyId(u.getUkeyId());
        r.setEmployeeNo(u.getEmployeeNo());
        r.setPost(u.getPost());
        r.setAge(u.getAge());
        r.setGender(u.getGender());
        r.setAvatarUrl(u.getAvatarUrl());
        r.setPhone(u.getPhone());
        r.setIsAllowChange(u.getIsAllowChange());
        return r;
    }

    //  非空校验
    private void validateRequired(String val, String msg) {
        if (val == null || val.isEmpty()) {
            throw new RuntimeException(msg);
        }
    }

    //  去空操作
    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    //  空转null
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

}
