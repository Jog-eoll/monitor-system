package com.publishgateway.udpproxy.service;


/**
 * 加密服务接口
 */
public interface CryptoService {

    /**
     * 加密数据
     * @param data 原始数据
     * @return 加密后的数据
     */
    byte[] encrypt(byte[] data);

    /**
     * 解密数据
     * @param data 加密数据
     * @return 解密后的数据
     */
    byte[] decrypt(byte[] data);

    /**
     * true 表示 SDK 当前按 SVAC 包接口处理数据，调用方应传入原始 SVAC 包。
     */
    default boolean isSvacMode() {
        return false;
    }
}
