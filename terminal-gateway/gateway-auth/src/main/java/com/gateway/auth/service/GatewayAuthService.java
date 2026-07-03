package com.gateway.auth.service;

/**
 * 网关认证管理服务（对外暴露状态查询和重新认证入口）
 *
 * <p>加解密逻辑由 CryptoService 的实现类完全自治管理；
 * GatewayAuthService 仅作为管理接口层（供 /gateway/auth/status、/gateway/auth/reauth 使用）。
 */
public interface GatewayAuthService {

    /**
     * 查询当前认证状态（UKey 是否已完成双向认证）
     *
     * @return true=已认证，false=未认证（UKey 拔出或认证失败）
     */
    boolean isAuthenticated();

    /**
     * 手动触发重新认证（UKey 重新插入后可通过此接口立即恢复，无需等待插拔事件）
     *
     * @return true=重新认证成功
     */
    boolean reAuthenticate();
}
