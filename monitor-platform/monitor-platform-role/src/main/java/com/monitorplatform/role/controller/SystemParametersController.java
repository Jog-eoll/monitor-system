package com.monitorplatform.role.controller;


import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.SystemParameters;
import com.monitorplatform.role.entity.dto.SystemParametersCreateReq;
import com.monitorplatform.role.entity.dto.SystemParametersDeleteReq;
import com.monitorplatform.role.entity.dto.SystemParametersPageResp;
import com.monitorplatform.role.entity.dto.SystemParametersUpdateReq;
import com.monitorplatform.role.service.SystemParametersService;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
系统模块参数接口
*/
@RestController
@RequestMapping("/system-parameters")
public class SystemParametersController {

    private final SystemParametersService systemParametersService;
    public SystemParametersController(SystemParametersService systemParametersService) {
        this.systemParametersService = systemParametersService;
    }

    // 创建
    @ApiOperation("创建系统参数")
    @PostMapping("/create")
    public Result<?> create(@Validated @RequestBody SystemParametersCreateReq req) {
        try {
            SystemParameters entity = systemParametersService.create(req);
            return Result.success("创建成功", entity);
        } catch (Exception e) {
            return Result.error("创建模块参数失败", e);
        }
    }

    // 按id查询
    @GetMapping("/get")
    public Result<?> get(@RequestParam Long id) {
        try {
            return Result.success(systemParametersService.getById(id));
        } catch (Exception e) {
            return Result.error("查询模块参数失败", e);
        }
    }

    // 按code查询（重点）
    @GetMapping("/getByCode")
    public Result<?> getByCode(@RequestParam String code) {
        try {
            return Result.success(systemParametersService.getByCode(code));
        } catch (Exception e) {
            return Result.error("按编码查询模块参数失败", e);
        }
    }

    // 更新
    @ApiOperation("更新系统参数")
    @PostMapping("/update")
    public Result<?> update(@Validated @RequestBody SystemParametersUpdateReq req) {
        try {
            SystemParameters entity = systemParametersService.update(req);
            return Result.success("更新成功", entity);
        } catch (Exception e) {
            return Result.error("更新模块参数失败", e);
        }
    }

    // 删除
    @ApiOperation("删除系统参数")
    @PostMapping("/delete")
    public Result<?> delete(@Validated @RequestBody SystemParametersDeleteReq req) {
        try {
            systemParametersService.delete(req.getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("删除模块参数失败", e);
        }
    }

    // 分页
    @GetMapping("/page")
    public Result<?> page(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            SystemParametersPageResp resp = systemParametersService.page(name, code, page, pageSize);
            return Result.success(resp);
        } catch (Exception e) {
            return Result.error("分页查询模块参数失败", e);
        }
    }
}
