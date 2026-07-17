package com.gateway.auth.service.impl;

import com.gateway.auth.service.GatewayAuthService;
import com.gateway.common.service.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 网关认证管理服务实现（精简版，仅负责管理接口暴露）
 *
 * <p>设计说明：
 * 认证核心逻辑（VAuth_OpenUkey + 三步握手 + 插拔回调）
 * 已完全内聚在 CryptoServiceImpl 中（自治模式），本类仅作代理转发：
 * - isAuthenticated() → 查询 CryptoService 的实际认证状态
 * - reAuthenticate()  → 触发 CryptoService 的重新认证
 *
 * <p>Mock 模式（SimpleCryptoServiceImpl）：isAuthenticated 始终返回 true，triggerReAuthenticate 为空操作。
 */
@Slf4j
@Service
public class GatewayAuthServiceImpl implements GatewayAuthService {

    @Resource
    private CryptoService cryptoService;

    @Override
    public boolean isAuthenticated() {
        return cryptoService.isAuthenticated();
    }

    @Override
    public boolean reAuthenticate() {
        log.info("=== 手动触发重新认证（等待握手完成）===");
        try {
            return cryptoService.reAuthenticate("HTTP_GATEWAY_AUTH");
        } catch (Exception e) {
            log.error("重新认证异常", e);
            return false;
        }
    }
}


