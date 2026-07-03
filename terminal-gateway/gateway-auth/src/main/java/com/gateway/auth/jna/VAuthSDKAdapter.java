package com.gateway.auth.jna;

import com.gateway.auth.config.VAuthConfig;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * VAuthSDK 适配器
 * 根据配置自动切换Mock模式或真实DLL模式
 */
@Slf4j
@Component
public class VAuthSDKAdapter {


    @Resource
    private VAuthConfig vAuthConfig;

    
    private VAuthSDKLibrary realSdk;
    private VAuthSDKMock mockSdk;


    @PostConstruct
    public void init() {
        if (vAuthConfig.getMockMode()) {
            log.warn("========================================");
            log.warn("  VAuthSDK 运行在 Mock 模式（加密网关）");
            log.warn("========================================");
            mockSdk = new VAuthSDKMock();
            mockSdk.VAuth_Init();
        } else {
            // 真实模式下仅获取 SDK 实例，不调用 VAuth_Init()
            // SDK 生命周期（Init/Cleanup）由 CryptoServiceImpl 统一管理，
            // 此处重复调用会重置已注册的回调导致解密失败
            log.info("VAuthSDKAdapter 运行在真实模式（解密网关），SDK 实例已就绪");
            realSdk = VAuthSDKLibrary.INSTANCE;
        }

    }

    /**
     * 打开SDF加密卡
     */
    public int openSDF(String password, String authId) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_OpenSDF(password);
        } else {
            return realSdk.VAuth_OpenSDF(password, authId);
        }
    }

    /**
     * 关闭句柄
     */
    public boolean closeHandle(int handle) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_CloseHandle(handle);
        } else {
            return realSdk.VAuth_CloseHandle(handle);
        }
    }

    /**
     * 设置认证服务器信息
     */
    public boolean setAuthServerInfo(int handle, String serverId, String serverCert) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_SetAuthServerInfo(handle, serverId, serverCert);
        } else {
            return realSdk.VAuth_SetAuthServerInfo(handle, serverId, serverCert);
        }
    }

    /**
     * 生成认证请求
     */
    public String buildAuthReq(int handle) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_BuildAuthReq(handle);
        } else {
            PointerByReference pReply = new PointerByReference();
            boolean success = realSdk.VAuth_BuildAuthReq(handle, pReply);
            if (!success || pReply.getValue() == null) {
                return null;
            }
            String result = pReply.getValue().getString(0, "UTF-8");
            realSdk.VAuth_Free(pReply.getValue());
            return result;
        }
    }

    /**
     * 生成认证信息
     */
    public String buildAuthInfo(int handle, String serverResponse) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_BuildAuthInfo(handle, serverResponse);
        } else {
            PointerByReference pReply = new PointerByReference();
            boolean success = realSdk.VAuth_BuildAuthInfo(handle, serverResponse, pReply);
            if (!success || pReply.getValue() == null) {
                return null;
            }
            String result = pReply.getValue().getString(0, "UTF-8");
            realSdk.VAuth_Free(pReply.getValue());
            return result;
        }
    }

    /**
     * 检查认证结果
     */
    public AuthCheckResult checkAuthResult(int handle, String serverResponse) {
        if (vAuthConfig.getMockMode()) {
            boolean success = mockSdk.VAuth_CheckAuthResult(handle, serverResponse);
            return new AuthCheckResult(success, success ? "" : "Mock认证失败");
        } else {
            PointerByReference pError = new PointerByReference();
            boolean success = realSdk.VAuth_CheckAuthResult(handle, serverResponse, pError);
            String error = "";
            if (pError.getValue() != null) {
                error = pError.getValue().getString(0, "UTF-8");
                realSdk.VAuth_Free(pError.getValue());
            }
            return new AuthCheckResult(success, error);
        }
    }

    /**
     * 数据加密
     * @param handle 设备句柄（SDF或UKey）
     * @param isSign 是否需要签名
     * @param data 待加密的数据
     * @return 加密后的数据，失败返回null
     */
    public byte[] encryptData(int handle, boolean isSign, byte[] data) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_EncryptData(handle, isSign, data);
        } else {
            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();

            boolean success = realSdk.VAuth_EncryptData(
                    handle, isSign, data, data.length, pOutData, outLen
            );

            if (!success || pOutData.getValue() == null) {
                int errorCode = realSdk.VAuth_GetLastError();
                String errorMsg = realSdk.VAuth_GetErrorText(errorCode);
                log.error("数据加密失败: errorCode={}, msg={}", errorCode, errorMsg);
                return null;
            }

            // 从C内存中复制数据到Java字节数组
            int length = outLen.getValue();
            byte[] result = pOutData.getValue().getByteArray(0, length);

            // 释放C分配的内存
            realSdk.VAuth_Free(pOutData.getValue());

            log.debug("数据加密成功: 原始长度={}, 加密后长度={}", data.length, length);
            return result;
        }
    }

    /**
     * 数据解密
     * @param handle 设备句柄（SDF或UKey）
     * @param isVerify 是否需要验签
     * @param encryptedData 加密的数据
     * @return 解密后的数据，失败返回null
     */
    public byte[] decryptData(int handle, boolean isVerify, byte[] encryptedData) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_DecryptData(handle, isVerify, encryptedData);
        } else {
            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();

            boolean success = realSdk.VAuth_DecryptData(
                    handle, isVerify, encryptedData, encryptedData.length, pOutData, outLen
            );

            if (!success || pOutData.getValue() == null) {
                int errorCode = realSdk.VAuth_GetLastError();
                String errorMsg = realSdk.VAuth_GetErrorText(errorCode);
                log.error("数据解密失败: errorCode={}, msg={}", errorCode, errorMsg);
                return null;
            }

            // 从C内存中复制数据到Java字节数组
            int length = outLen.getValue();
            byte[] result = pOutData.getValue().getByteArray(0, length);

            // 释放C分配的内存
            realSdk.VAuth_Free(pOutData.getValue());

            log.debug("数据解密成功: 加密长度={}, 解密后长度={}", encryptedData.length, length);
            return result;
        }
    }

    /**
     * 注册密钥查询回调（解密侧必须注册）
     * SDK 在解密时若缺少会话密钥，会触发此回调，
     * 回调内须调用 VAuth_SetKey 将密钥提供给 SDK
     *
     * @param callback 密钥查询回调实现
     * @return TRUE-注册成功 FALSE-失败
     */
    public boolean setQueryKeyCallback(VAuthSDKLibrary.QueryKeyCallback callback) {
        if (vAuthConfig.getMockMode()) {
            log.debug("[Mock] setQueryKeyCallback - 跳过（Mock模式不需要回调）");
            return true;
        }
        return realSdk.VAuth_SetQueryKeyCallback(callback, null);
    }

    /**
     * 注册证书查询回调（解密验签时必须注册）
     * SDK 在验签时若缺少发送方证书，会触发此回调，
     * 回调内须调用 VAuth_SetCer 将证书提供给 SDK
     *
     * @param callback 证书查询回调实现
     * @return TRUE-注册成功 FALSE-失败
     */
    public boolean setQueryCerCallback(VAuthSDKLibrary.QueryCerCallback callback) {
        if (vAuthConfig.getMockMode()) {
            log.debug("[Mock] setQueryCerCallback - 跳过（Mock模式不需要回调）");
            return true;
        }
        return realSdk.VAuth_SetQueryCerCallback(callback, null);
    }

    /**
     * 向 SDK 提供会话密钥（密钥查询回调内调用）
     *
     * @param handle 设备句柄
     * @param id     认证ID
     * @param ver    密钥版本
     * @param key    密钥 Base64 字符串
     * @return TRUE-成功 FALSE-失败
     */
    public boolean setKey(int handle, String id, String ver, String key) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_SetKey(id, ver, key);
        }
        return realSdk.VAuth_SetKey(handle, id, ver, key);
    }

    /**
     * 向 SDK 提供证书（证书查询回调内调用）
     *
     * @param id   认证ID
     * @param type 证书类型（1=签名证书）
     * @param cer  证书内容字符串
     * @return TRUE-成功 FALSE-失败
     */
    public boolean setCer(String id, int type, String cer) {
        if (vAuthConfig.getMockMode()) {
            return mockSdk.VAuth_SetCer(id, type, cer);
        }
        return realSdk.VAuth_SetCer(id, type, cer);
    }

    public static class AuthCheckResult {
        public final boolean success;
        public final String error;

        public AuthCheckResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }
    

}
