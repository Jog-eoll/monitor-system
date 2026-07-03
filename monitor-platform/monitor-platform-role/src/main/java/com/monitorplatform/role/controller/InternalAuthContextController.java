package com.monitorplatform.role.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.entity.Permission;
import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.service.AppUserService;
import com.monitorplatform.role.service.RoleService;
import com.monitorplatform.role.service.UserRoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * monitor-ukey 在证书登录后使用的内部认证上下文接口。
 */
@RestController
@RequestMapping("/internal/auth-context")
public class InternalAuthContextController {

    private final AppUserService appUserService;
    private final RoleService roleService;
    private final UserRoleService userRoleService;

    public InternalAuthContextController(AppUserService appUserService,
                                         RoleService roleService,
                                         UserRoleService userRoleService) {
        this.appUserService = appUserService;
        this.roleService = roleService;
        this.userRoleService = userRoleService;
    }

    @GetMapping("/by-ukey-id")
    public Result<?> getByUkeyId(@RequestParam String ukeyId) {
        try {
            AppUser user = appUserService.getByUkeyId(ukeyId);
            if (user.getRoleId() == null) {
                return Result.build(403, "user role is not bound", null);
            }

            Role role = roleService.getById(user.getRoleId());
            if (!"ENABLED".equals(role.getStatus())) {
                return Result.build(403, "user role is disabled", null);
            }

            List<Permission> permissions = userRoleService.listUserPermissions(user.getId());
            List<String> permissionCodes = permissions.stream()
                    .map(Permission::getCode)
                    .filter(code -> code != null && !code.trim().isEmpty())
                    .collect(Collectors.toList());
            List<Long> permissionIds = permissions.stream()
                    .map(Permission::getId)
                    .collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("ukeyId", user.getUkeyId());
            data.put("roleId", role.getId());
            data.put("roleCode", role.getCode());
            data.put("roleName", role.getName());
            data.put("permissionCodes", permissionCodes);
            data.put("permissionIds", permissionIds);
            return Result.success(data);
        } catch (RuntimeException e) {
            return Result.build(404, e.getMessage(), null);
        } catch (Exception e) {
            return Result.error("query auth context failed", e);
        }
    }
}
