package com.publishgateway.udpproxy.service.impl;

import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.SignedEnvelopeCryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 简单加密服务实现（AES加密用于模拟）
 * 启用条件：vauth.mock-mode=true（默认）
 * 无需SDF/UKey硬件，适用于开发调试场景
 */

@Slf4j
@Service
@ConditionalOnProperty(name = "vauth.mock-mode", havingValue = "true", matchIfMissing = true)
public class SimpleCryptoServiceImpl implements CryptoService, SignedEnvelopeCryptoService {


    // 密钥（16字节，AES-128）
    private static final String SECRET_KEY = "PublishGateway16"; // 16字符
    private static final String ALGORITHM = "AES";




    @Override
    public byte[] encrypt(byte[] data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(data);
            log.debug("数据加密成功：原始{}字节 -> 加密{}字节", data.length, encrypted.length);
            return encrypted;
        } catch (Exception e) {
            log.error("加密失败", e);
            return data; // 加密失败返回原数据
        }
    }

    @Override
    public byte[] decrypt(byte[] data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(data);
            log.debug("数据解密成功：加密{}字节 -> 原始{}字节", data.length, decrypted.length);
            return decrypted;
        } catch (Exception e) {
            log.error("解密失败", e);
            return data; // 解密失败返回原数据
        }
    }

    @Override
    public byte[] signEnvelope(byte[] data) {
        return encrypt(data);
    }

    @Override
    public byte[] verifyEnvelope(byte[] signedEnvelope) {
        return decrypt(signedEnvelope);
    }
}
