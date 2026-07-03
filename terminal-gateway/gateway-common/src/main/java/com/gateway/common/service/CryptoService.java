package com.gateway.common.service;

/**
 * 数据加解密服务接口
 *
 * <p>publish-gateway（加密侧）和 terminal-gateway（解密侧）共用同一套接口命名规范：
 * - encrypt：发送方在转发前加密原始数据
 * - decrypt：接收方收到数据后解密
 */
public interface CryptoService {

    /**
     * 加密数据（SM4 + 可选 SM2 签名 / Mock AES）
     *
     * @param data 原始数据
     * @return 加密后数据；未认证或加密失败时降级返回原始数据（透传）
     */
    byte[] encrypt(byte[] data);

    /**
     * 解密数据（SM4 解密 + 可选 SM2 验签 / Mock AES）
     *
     * @param data 加密数据
     * @return 解密后数据；未认证或解密失败时降级返回原始数据（透传）
     */
    byte[] decrypt(byte[] data);

    /**
     * 查询当前认证状态（UKey 是否已完成双向认证）
     * Mock 模式始终返回 true；真实模式取决于 UKey 认证是否成功。
     *
     * @return true=可正常加解密，false=UKey 未认证（数据将透传）
     */
    default boolean isAuthenticated() {
        return true;
    }

    /**
     * true 表示 SDK 当前按 SVAC 包接口处理数据，调用方应传入原始 SVAC 包。
     */
    default boolean isSvacMode() {
        return false;
    }

    /**
     * 手动触发重新认证（UKey 重新插入后可调用，无需等待插拔事件）
     * Mock 模式为空操作。
     */
    default void triggerReAuthenticate() {
        // 默认空实现（Mock模式）
    }
}
