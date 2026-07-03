package com.monitorplatform.ukey.feign;

import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.entity.Permission;
import com.monitorplatform.role.entity.Role;
import com.monitorplatform.role.service.AppUserService;
import com.monitorplatform.role.service.RoleService;
import com.monitorplatform.role.service.UserRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Role 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service
public class RoleFeignClient {

    @Resource
    private AppUserService appUserService;

    @Resource
    private RoleService roleService;

    @Resource
    private UserRoleService userRoleService;

    public Map<String, Object> getAuthContextByUkeyId(String ukeyId) {
        Map<String, Object> result = new HashMap<>();
        try {
            AppUser user = appUserService.getByUkeyId(ukeyId);
            if (user == null || user.getRoleId() == null) {
                result.put("code", 403);
                result.put("msg", user == null ? "user not found" : "user role is not bound");
                return result;
            }

            Role role = roleService.getById(user.getRoleId());
            if (role == null || !"ENABLED".equals(role.getStatus())) {
                result.put("code", 403);
                result.put("msg", "user role is disabled");
                return result;
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

            result.put("code", 200);
            result.put("data", data);
        } catch (RuntimeException e) {
            log.warn("获取认证上下文失败: ukeyId={}", ukeyId, e);
            result.put("code", 404);
            result.put("msg", e.getMessage());
        } catch (Exception e) {
            log.error("获取认证上下文异常: ukeyId={}", ukeyId, e);
            result.put("code", 500);
            result.put("msg", "查询认证上下文失败: " + e.getMessage());
        }
        return result;
    }
}
