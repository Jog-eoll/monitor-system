package com.gateway.auth.controller;

import com.gateway.auth.service.GatewayAuthService;
import com.gateway.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 网关认证管理接口
 * 基础路径: /gateway/auth
 */
@Slf4j
@RestController
@RequestMapping("/gateway/auth")
public class GatewayAuthController {
    
    @Resource
    private GatewayAuthService gatewayAuthService;
    
    /**
     * 检查认证状态
     * 
     * 接口: GET /gateway/auth/status
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getAuthStatus() {
        try {
            boolean authenticated = gatewayAuthService.isAuthenticated();
            
            Map<String, Object> data = new HashMap<>();
            data.put("authenticated", authenticated);
            
            return Result.success(data);
        } catch (Exception e) {
            log.error("查询认证状态失败", e);
            return Result.error("查询认证状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 手动触发重新认证（UKey 重新插入后可调用，加快恢复认证状态）
     * 
     * 接口: POST /gateway/auth/reauth
     */
    @PostMapping("/reauth")
    public Result<Void> reAuthenticate() {
        try {
            log.info("收到重新认证请求");
            boolean success = gatewayAuthService.reAuthenticate();
            
            if (success) {
                return Result.success("重新认证已触发", null);
            } else {
                return Result.error("重新认证触发失败");
            }
        } catch (Exception e) {
            log.error("重新认证失败", e);
            return Result.error("重新认证失败: " + e.getMessage());
        }
    }
}

