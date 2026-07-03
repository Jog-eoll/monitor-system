package com.infopublish.client.jna;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * VAuthSDK JNA接口映射
 */
public interface VAuthSDKLibrary extends Library {

    // Linux下库名是libvauthsdk.so，Windows下是VAuthSDK.dll
    String os = System.getProperty("os.name").toLowerCase();
    String libName = os.contains("win") ? "VAuthSDK" : "vauthsdk";
    VAuthSDKLibrary INSTANCE = Native.load(libName,VAuthSDKLibrary.class);



    // ========== Ukey事件回调接口 ==========

    /**
     * Ukey硬件事件回调（插拔/状态变化）
     * type: 事件类型 1-插入 2-拔出 3-状态变化
     * name: 设备名称
     * msg: 附加消息
     */
    interface UkeyEventCallback extends Callback {
        int invoke(int type, String name, String msg, Pointer dwUser);
    }
    /**
     * 密钥查询回调
     */
    interface QueryKeyCallback extends Callback {
        int invoke(int handle, String id, String ver, Pointer dwUser);
    }

    /**
     * 证书查询回调
     */
    interface QueryCerCallback extends Callback {
        int invoke(String id, int type, Pointer dwUser);
    }

    /**
     * 密钥返回回调
     */
    interface KeyCallback extends Callback {
        int invoke(String id, String ver, String key, Pointer dwUser);
    }



    // ========== 初始化相关 ==========

    /**
     * 初始化SDK
     * @return TRUE-成功，FALSE-失败
     */
    boolean VAuth_Init();

    /**
     * 释放SDK资源
     * @return TRUE-成功，FALSE-失败
     */
    boolean VAuth_Cleanup();

    /**
     * 释放内存
     * @param pObj 释放的对象指针
     */
    void VAuth_Free(Pointer pObj);

    /**
     * 获取错误代码
     * @return 错误码
     */
    int VAuth_GetLastError();

    /**
     * 根据错误代码获取错误信息
     * @param nError 错误代码
     * @return 错误信息
     */
    String VAuth_GetErrorText(int nError);

    // ========== UKey操作 ==========

    /**
     * 设置Ukey事件回调
     * @param callback 回调函数
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetUkeyEventCallback(UkeyEventCallback callback, Pointer dwUser);

    /**
     * 列表UKey信息
     * @param pReply 返回结果指针（输出参数）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_ListUkeyInfos(PointerByReference pReply);

    /**
     * 打开UKey
     * @param path UKey路径
     * @param password UKey密码
     * @return 设备句柄。>=0: 句柄 < 0: 错误码
     */
    int VAuth_OpenUkey(String path, String password, String authId);

    // ========== SDF加密卡操作 ==========

    /**
     * 打开SDF密码卡
     * @param password 设备密码
     * @return 设备句柄。>=0: 句柄 < 0: 错误码
     */
    int VAuth_OpenSDF(String password, String authId);

    /**
     * 关闭句柄
     * @param handle 打开相关句柄的返回值
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_CloseHandle(int handle);

    // ========== 客户端认证相关 ==========

    /**
     * 设置认证服务信息（认证方使用）
     * @param handle 打开相关句柄的返回值
     * @param id 认证服务ID
     * @param signCer 认证服务签名证书信息
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetAuthServerInfo(int handle, String id, String signCer);

    /**
     * 生成认证请求（认证方使用）
     * @param handle 设备句柄
     * @param pReply 返回认证请求消息（输出参数）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_BuildAuthReq(int handle, PointerByReference pReply);

    /**
     * 生成认证消息（认证方使用）
     * @param handle 设备句柄
     * @param respInfo 认证服务返回的认证请求回复消息
     * @param pReply 返回认证消息（输出参数）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_BuildAuthInfo(int handle, String respInfo, PointerByReference pReply);

    /**
     * 检查认证结果（认证方使用）
     * @param handle 设备句柄
     * @param respInfo 认证服务返回的认证结果消息
     * @param pError 认证错误信息（输出参数）
     * @return TRUE-认证成功 FALSE-认证失败
     */
    boolean VAuth_CheckAuthResult(int handle, String respInfo, PointerByReference pError);

    // ========== 服务端认证相关 ==========

    /**
     * 解析认证请求（认证服务使用）
     * @param handle 设备句柄
     * @param authId 认证用户或设备ID
     * @param reqInfo 认证请求信息
     * @param pReply 返回认证请求回复消息（输出参数）
     * @return TRUE-认证成功 FALSE-认证失败
     */
    boolean VAuth_ParseAuthReq(int handle, String authId, String reqInfo, PointerByReference pReply);

    /**
     * 解析认证消息（认证服务使用）
     * @param handle 设备句柄
     * @param authId 认证用户或设备ID
     * @param signCer 认证用户或设备的签名证书信息
     * @param reqInfo 认证信息
     * @param pReply 返回认证结果（输出参数）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_ParseAuthInfo(int handle, String authId, String signCer, String reqInfo, PointerByReference pReply);

    /**
     * 解析认证错误
     * @param handle
     * @param respInfo
     * @param pError
     * @return
     */
    boolean VAuth_ParseAuthError(int handle, String respInfo, PointerByReference pError);

    // ========== 加解密操作 ==========

    /**
     * 数据加密
     * @param handle 设备句柄
     * @param isSign 是否数据签名
     * @param pData 待加密数据
     * @param dataLen 待加密数据长度
     * @param pOutData 返回加密数据（输出参数）
     * @param outLen 返回加密数据长度（输出参数）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_EncryptData(int handle, boolean isSign, byte[] pData, int dataLen,
                              PointerByReference pOutData, IntByReference outLen);

    /**
     * 数据解密
     * @param handle 设备句柄
     * @param isVerify 是否数据验签
     * @param pData 加密数据
     * @param dataLen 加密数据长度
     * @param pOutData 返回解密数据（输出参数）
     * @param outLen 返回解密数据长度（输出参数）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_DecryptData(int handle, boolean isVerify, byte[] pData, int dataLen,
                              PointerByReference pOutData, IntByReference outLen);

    // ========== 密钥管理 ==========

    boolean VAuth_SetQueryKeyCallback(QueryKeyCallback callback, Pointer dwUser);

    /**
     * 设置密钥
     * @param id 用户或设备认证ID
     * @param ver 密钥版本
     * @param key 密钥BASE64信息
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetKey(int handle, String id, String ver, String key);

    /**
     * 设置证书
     * @param id 用户或设备认证ID
     * @param type 证书类型 1: 签名证书
     * @param cer 证书信息
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetCer(String id, int type, String cer);

    boolean VAuth_SetQueryCerCallback(QueryCerCallback callback, Pointer dwUser);

    boolean VAuth_SetKeyCallback(KeyCallback callback, Pointer dwUser);

    /**
     *
     * @param handle
     * @param key
     * @param cer
     * @param pReply
     * @return
     */
    boolean VAuth_EncryptKey(int handle, String key, String cer, PointerByReference pReply);
}
