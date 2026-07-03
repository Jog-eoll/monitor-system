package com.monitorplatform.role.controller;


import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.entity.dto.*;
import com.monitorplatform.role.service.RoleService;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*

角色服务控制器

*/
@RestController
@RequestMapping("/role")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    //  创建角色
    @ApiOperation("创建角色")
    @PostMapping("/create")
    public Result<?> create(@Validated @RequestBody RoleCreateReq req) {
        try {
            Role role = roleService.create(req.getCode(), req.getName(), req.getDescription(), req.getStatus(), req.getPermissionCodes());
            return Result.success("创建成功", role);
        } catch (Exception e) {
            return Result.error("创建角色失败", e);
        }
    }

    //  根据id查询角色
    @GetMapping("/get")
    public Result<?> get(@RequestParam Long id) {
        try {
            Role role = roleService.getById(id);
            return Result.success(role);
        } catch (Exception e) {
            return Result.error("查询角色失败", e);
        }
    }

    //  根据id更新角色
    @ApiOperation("更新角色")
    @PostMapping("/update")
    public Result<?> update(@Validated @RequestBody RoleUpdateReq req) {
        try {
            return Result.success("更新成功", roleService.updateByReq(req));
        } catch (Exception e) {
            return Result.error("更新角色失败", e);
        }
    }

    //  角色删除
    @ApiOperation("删除角色")
    @PostMapping("/delete")
    public Result<?> delete(@Validated @RequestBody RoleDeleteReq req) {
        try {
            roleService.delete(req.getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("删除角色失败", e);
        }
    }

    //  分页接口
    @GetMapping("/page")
    public Result<?> page(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            RolePageResp resp = roleService.list(name, code, status, page, pageSize);
            return Result.success(resp);
        } catch (Exception e) {
            return Result.error("分页查询角色失败", e);
        }
    }

    //  批量导入
    @ApiOperation("批量导入角色")
    @PostMapping("/import")
    public Result<?> batchImport(@Validated @RequestBody RoleBatchImportReq req) {
        try {
            return Result.success("导入完成", roleService.batchImport(req.getRoles(), req.isSkipDuplicateCode()));
        } catch (Exception e) {
            return Result.error("批量导入角色失败", e);
        }
    }

    //  替换权限
    @ApiOperation("替换角色权限")
    @PostMapping("/permission/replace")
    public Result<?> replacePermission(@Validated @RequestBody RolePermissionReplaceReq req) {
        try {
            roleService.replaceRolePermissions(req.getRoleId(), req.getPermissionIds());
            return Result.success("角色权限保存成功", null);
        } catch (Exception e) {
            return Result.error("角色权限保存失败", e);
        }
    }
}

