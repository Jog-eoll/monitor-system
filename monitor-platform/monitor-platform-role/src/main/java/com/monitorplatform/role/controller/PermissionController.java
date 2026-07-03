package com.monitorplatform.role.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.Permission;
import com.monitorplatform.role.entity.dto.PermissionCreateReq;
import com.monitorplatform.role.entity.dto.PermissionDeleteReq;
import com.monitorplatform.role.entity.dto.PermissionPageResp;
import com.monitorplatform.role.entity.dto.PermissionUpdateReq;
import com.monitorplatform.role.service.PermissionService;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
权限接口路径

*/

@RestController
@RequestMapping("/permission")
public class PermissionController {

    private final PermissionService permissionService;

    //  构造
    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    //  权限创建接口路径
    @ApiOperation("创建权限")
    @PostMapping("/create")
    public Result<?> create(@Validated @RequestBody PermissionCreateReq req){
        try {
            Permission permission = permissionService.create(req);
            return Result.success("创建成功",permission);
        } catch (Exception e) {
            return Result.error("创建失败",e);
        }
    }

    //  查询单个权限数据接口路径
    @GetMapping("/get")
    public Result<?> get(@RequestParam Long id){
        try{
            return Result.success(permissionService.getById(id));
        } catch (Exception e){
            return Result.error("查询失败",e);
        }
    }

    //  更新单个权限数据接口路径
    @ApiOperation("更新权限")
    @PostMapping("/update")
    public Result<?> update(@Validated @RequestBody PermissionUpdateReq req){
        try {
            Permission permission = permissionService.update(req);
            return  Result.success("更新成功",permission);
        } catch (Exception e) {
            return Result.error("删除失败",e);
        }
    }

    //  删除单个权限数据接口路径
    @ApiOperation("删除权限")
    @PostMapping("/delete")
    public Result<?> delete(@Validated @RequestBody PermissionDeleteReq req){
        try {
            permissionService.delete(req.getId());
            return Result.success("删除成功",null);
        } catch (Exception e) {
            return Result.error("删除失败",e);
        }
    }

    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer page,@RequestParam(defaultValue = "10") Integer pageSize){
        try {
            PermissionPageResp resp = permissionService.pageTree(page,pageSize);
            return Result.success(resp);
        } catch (Exception e) {
            return Result.error("分页查询失败",e);
        }
    }
}
