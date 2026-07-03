package com.monitorplatform.role.controller;



import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.entity.dto.AppUserCreateReq;
import com.monitorplatform.role.entity.dto.AppUserDeleteReq;
import com.monitorplatform.role.entity.dto.AppUserPageResp;
import com.monitorplatform.role.entity.dto.AppUserUpdateReq;
import com.monitorplatform.role.service.AppUserService;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
用户服务接口

*/

@RestController
@RequestMapping("/user")
public class AppUserController {

    private final AppUserService appUserService;
    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    //  创建用户接口
    @ApiOperation("创建用户")
    @PostMapping("/create")
    public Result<?> create(@Validated @RequestBody AppUserCreateReq req) {
        try {
            AppUser user = appUserService.create(req);
            return Result.success("创建成功", user);
        } catch (Exception e) {
            return Result.error("创建用户失败", e);
        }
    }

    //  更新用户接口
    @ApiOperation("更新用户")
    @PostMapping("/update")
    public Result<?> update(@Validated @RequestBody AppUserUpdateReq req) {
        try {
            AppUser user = appUserService.update(req);
            return Result.success("更新成功", user);
        } catch (Exception e) {
            return Result.error("更新用户失败", e);
        }
    }

    //  删除用户接口
    @ApiOperation("删除用户")
    @PostMapping("/delete")
    public Result<?> delete(@Validated @RequestBody AppUserDeleteReq req) {
        try {
            appUserService.delete(req.getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("删除用户失败", e);
        }
    }

    //  查询用户接口
    @GetMapping("/get")
    public Result<?> get(@RequestParam Long id) {
        try {
            return Result.success(appUserService.getById(id));
        } catch (Exception e) {
            return Result.error("查询用户失败", e);
        }
    }

    //  用户分页接口
    @GetMapping("/page")
    public Result<?> page(@RequestParam(required = false) String username,
                                    @RequestParam(required = false) Long roleId,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            AppUserPageResp resp = appUserService.page(username, roleId, page, pageSize);
            return Result.success(resp);
        } catch (Exception e) {
            return Result.error("分页查询用户失败", e);
        }
    }

    //  根据 ukeyId 查询用户
    @GetMapping("/getByUkeyId")
    public Result<?> getByUkeyId(@RequestParam String ukeyId) {
        try {
            return Result.success(appUserService.getByUkeyId(ukeyId));
        } catch (Exception e) {
            return Result.error("根据ukeyId查询用户失败", e);
        }
    }
}
