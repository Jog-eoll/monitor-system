package com.monitorplatform.role.controller;


import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.dto.BindUserRoleReq;
import com.monitorplatform.role.service.UserRoleService;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*

用户角色接口

*/
@RestController
@RequestMapping("/user-role")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    //  用户绑定角色
    @ApiOperation("绑定用户角色")
    @PostMapping("/bind")
    public Result<?> bind(@Validated @RequestBody BindUserRoleReq req){
        try {
            userRoleService.bindUserRole(req.getUserId(),req.getRoleId());
            return Result.success("绑定成功",null);
        } catch (Exception e){
            return Result.error("绑定失败",e);
        }
    }

    //  查询用户权限
    @GetMapping("/permissions")
    public Result<?> permissions(@RequestParam Long userId){
        try {
            return Result.success(userRoleService.listUserPermissions(userId));
        } catch (Exception e){
            return Result.error("查询用户权限失败",e);
        }
    }

    // 通过用户ID查询菜单权限（别名接口）
    @GetMapping("/menu-permissions")
    public Result<?> menuPermissions(@RequestParam Long userId){
        try {
            return Result.success(userRoleService.listUserPermissions(userId));
        } catch (Exception e){
            return Result.error("查询用户菜单权限失败", e);
        }
    }
}
