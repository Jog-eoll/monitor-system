package com.publishgateway.udpproxy.jna;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * VAuthSDK JNA 接口映射（发布加密网关）
 * Windows: VAuthSDK.dll
 * Linux:   libvauthsdk.so
 */
public interface VAuthSDKLibrary extends Library {

    String OS_NAME = System.getProperty("os.name").toLowerCase();
    String LIB_NAME = OS_NAME.contains("win") ? "VAuthSDK" : "vauthsdk";
    VAuthSDKLibrary INSTANCE = Native.load(LIB_NAME, VAuthSDKLibrary.class);

    // ========== 密钥/证书查询回调 ==========

    /**
     * 密钥查询回调（解密侧使用，加密侧无需关注但需声明完整接口）
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
     * 密钥返回回调（服务端用于下发加密密钥）
     */
    interface KeyCallback extends Callback {
        int invoke(String id, String ver, String key, Pointer dwUser);
    }

    // ========== 初始化 ==========

    /**
     * 初始化SDK
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_Init();

    /**
     * 释放SDK资源
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_Cleanup();

    /**
     * 释放SDK内部分配的内存
     * @param pObj 待释放指针
     */
    void VAuth_Free(Pointer pObj);

    /**
     * 获取最后一次错误码
     * @return 错误码
     */
    int VAuth_GetLastError();

    /**
     * 根据错误码获取错误描述
     * @param nError 错误码
     * @return 错误描述字符串指针（SDK 可能返回 GBK 编码，需手动解码）
     */
    Pointer VAuth_GetErrorText(int nError);

    // ========== 设备操作 ==========

    /**
     * 列举已插入的 UKey 设备信息
     * @param pReply 返回 JSON 数组，包含 name/label/sn/cerSn/cerId/path，需要 VAuth_Free 释放
     * @return true-成功 false-失败
     */
    boolean VAuth_ListUkeyInfos(PointerByReference pReply);

    /**
     * 打开SDF密码卡
     * @param password 设备密码
     * @param authId   本端认证ID
     * @return 设备句柄，>=0表示成功，<0表示错误码
     */
    int VAuth_OpenSDF(String password, String authId);

    /**
     * 打开UKey
     * @param path     UKey路径（来自VAuth_ListUkeyInfos）
     * @param password UKey密码
     * @param authId   本端认证ID
     * @return 设备句柄，>=0表示成功，<0表示错误码
     */
    int VAuth_OpenUkey(String path, String password, String authId);

    /**
     * 关闭设备句柄
     * @param handle 设备句柄
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_CloseHandle(int handle);

    // ========== 客户端认证（发布网关作为认证方） ==========

    /**
     * 设置认证服务信息
     * @param handle   设备句柄
     * @param id       认证服务端ID
     * @param signCer  认证服务端签名证书内容
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetAuthServerInfo(int handle, String id, String signCer);

    /**
     * 生成认证请求（第一步）
     * @param handle  设备句柄
     * @param pReply  输出：认证请求消息（SDK分配内存，需VAuth_Free释放）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_BuildAuthReq(int handle, PointerByReference pReply);

    /**
     * 生成认证消息（第二步）
     * @param handle   设备句柄
     * @param respInfo 认证服务返回的第一步回复
     * @param pReply   输出：认证消息（SDK分配内存，需VAuth_Free释放）
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_BuildAuthInfo(int handle, String respInfo, PointerByReference pReply);

    /**
     * 检查认证结果（第三步）
     * @param handle   设备句柄
     * @param respInfo 认证服务返回的最终结果
     * @param pError   输出：错误信息（非空时为认证失败信息，需VAuth_Free释放）
     * @return TRUE-认证成功 FALSE-认证失败
     */
    boolean VAuth_CheckAuthResult(int handle, String respInfo, PointerByReference pError);

    // ========== 解密回调注册（发布网关解密终端响应时需要） ==========

    /**
     * 注册密钥查询回调
     * SDK 在解密时若缺少对端会话密钥，会触发此回调；回调内须调用 VAuth_SetKey 提供密钥
     */
    boolean VAuth_SetQueryKeyCallback(QueryKeyCallback callback, Pointer dwUser);

    /**
     * 注册证书查询回调
     * SDK 在验签时若缺少对端证书，会触发此回调；回调内须调用 VAuth_SetCer 提供证书
     */
    boolean VAuth_SetQueryCerCallback(QueryCerCallback callback, Pointer dwUser);

    /**
     * 向 SDK 提供会话密钥（密钥查询回调内调用）
     * @param handle 设备句柄
     * @param id     认证ID
     * @param ver    密钥版本
     * @param key    加密后的密钥 Base64 字符串
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetKey(int handle, String id, String ver, String key);

    /**
     * 向 SDK 提供证书（证书查询回调内调用）
     * @param id   认证ID
     * @param type 证书类型（1=签名证书）
     * @param cer  证书内容 PEM 字符串
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetCer(String id, int type, String cer);

    // ========== 加解密（发布网关主要使用加密） ==========

    /**
     * 数据加密（发送方使用）
     * 前提：必须在双向认证成功后调用，否则无法获取会话密钥
     * @param handle   设备句柄
     * @param isSign   是否附带SM2签名（建议true，接收方可验签）
     * @param pData    待加密数据
     * @param dataLen  待加密数据长度
     * @param pOutData 输出：加密后数据（SDK分配内存，需VAuth_Free释放）
     * @param outLen   输出：加密后数据长度
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_EncryptData(int handle, boolean isSign,
                              byte[] pData, int dataLen,
                              PointerByReference pOutData, IntByReference outLen);

    /**
     * 数据解密（接收方使用，发布网关通常不需要，保留完整接口）
     * @param handle   设备句柄
     * @param isVerify 是否验证SM2签名
     * @param pData    待解密数据
     * @param dataLen  待解密数据长度
     * @param pOutData 输出：解密后数据（SDK分配内存，需VAuth_Free释放）
     * @param outLen   输出：解密后数据长度
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_DecryptData(int handle, boolean isVerify,
                              byte[] pData, int dataLen,
                              PointerByReference pOutData, IntByReference outLen);

    // ========== SVAC2 编码加解密（Linux 专用） ==========

    /**
     * SVAC2 流数据加密（发送方使用）
     * 将数据以 SVAC2 编码格式加密，输出符合 SVAC 国标的加密码流。
     * 前提：必须在双向认证成功后调用。
     * 注意：仅 Linux 平台可用，Windows 下调用将返回失败。
     *
     * @param handle   设备句柄
     * @param isSign   是否附带 SM2 签名
     * @param pData    待加密数据
     * @param dataLen  待加密数据长度
     * @param pOutData 输出：SVAC2 格式加密数据（SDK分配内存，需 VAuth_Free 释放）
     * @param outLen   输出：加密后数据长度
     * @return TRUE-成功 FALSE-失败
     */
    int VAuth_EncryptPackData(int handle, int isSign,
                                  byte[] pData, int dataLen,
                                  PointerByReference pOutData, IntByReference outLen);

    /**
     * SVAC file-content encryption. This is intended for complete file bytes,
     * unlike the normal JSON/control-package encryption path.
     */
    boolean VAuth_EncryptFileData(int handle, boolean isSign,
                                  byte[] pData, int dataLen,
                                  PointerByReference pOutData, IntByReference outLen);

    /**
     * SVAC2 流数据解密（接收方使用）
     * 解密 SVAC2 编码格式的加密数据，还原原始数据。
     * 注意：仅 Linux 平台可用。
     *
     * @param handle   设备句柄
     * @param isVerify 是否验证 SM2 签名
     * @param pData    SVAC2 格式加密数据
     * @param dataLen  加密数据长度
     * @param pOutData 输出：解密后原始数据（SDK分配内存，需 VAuth_Free 释放）
     * @param outLen   输出：解密后数据长度
     * @return TRUE-成功 FALSE-失败
     */
    int VAuth_DecryptPackData(int handle, int isVerify,
                                  byte[] pData, int dataLen,
                                  PointerByReference pOutData, IntByReference outLen);

    /**
     * SVAC file-content decryption counterpart for {@link #VAuth_EncryptFileData}.
     */
    boolean VAuth_DecryptFileData(int handle, boolean isVerify,
                                  byte[] pData, int dataLen,
                                  PointerByReference pOutData, IntByReference outLen);


    /**
     * 设置Ukey事件回调
     * @param callback 回调函数
     * @return TRUE-成功 FALSE-失败
     */
    boolean VAuth_SetUkeyEventCallback(UkeyEventCallback callback, Pointer dwUser);


    /**
     * Ukey硬件事件回调（插拔/状态变化）
     * type: 事件类型 1-插入 2-拔出 3-状态变化
     * name: 设备名称
     * msg: 附加消息
     */
    interface UkeyEventCallback extends Callback {
        int invoke(int type, String name, String msg, Pointer dwUser);
    }
}
