package com.gateway.crypto.service.impl;

import com.gateway.common.service.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES 模拟加解密服务（终端解密网关 Mock 模式）
 *
 * <p>启用条件：vauth.mock-mode=true（默认，不指定时也激活）
 * <p>算法：AES-ECB-128（与 publish-gateway/SimpleCryptoServiceImpl 完全对称）
 * <p>密钥：{@code "PublishGateway16"}（两侧必须使用相同密钥，否则解密失败）
 *
 * <p>此实现无需 UKey 硬件，适用于开发调试和集成测试场景。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "vauth.mock-mode", havingValue = "true", matchIfMissing = true)
public class SimpleCryptoServiceImpl implements CryptoService {

    /**
     * AES 密钥（16字节/128位），必须与 publish-gateway SimpleCryptoServiceImpl 的 SECRET_KEY 一致
     */
    private static final String SECRET_KEY = "PublishGateway16";
    private static final String ALGORITHM = "AES";

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void triggerReAuthenticate() {
        // mock no-op
    }

    @Override
    public boolean reAuthenticate(String reason) {
        log.info("[Mock] reAuthenticate accepted, reason={}", reason);
        return true;
    }

    /**
     * AES 加密（对应发布网关加密，终端网关通常用于回传情报板响应给发布网关）
     */
    @Override
    public byte[] encrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(data);
            log.debug("[Mock] encrypt: {}字节 -> {}字节", data.length, encrypted.length);
            return encrypted;
        } catch (Exception e) {
            log.error("[Mock] encrypt 失败，数据透传", e);
            return data;
        }
    }

    /**
     * AES 解密（核心：将发布网关加密的 UDP 包还原为明文）
     */
    @Override
    public byte[] decrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(data);
            log.debug("[Mock] decrypt: {}字节 -> {}字节", data.length, decrypted.length);
            return decrypted;
        } catch (Exception e) {
            log.error("[Mock] decrypt 失败", e);
            return null; // 解密失败返回 null，调用方丢弃此包
        }
    }
}
