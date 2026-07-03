package com.monitorplatform.ukey.jna;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * VAuthSDK JNA 接口映射（服务端，运行于 Linux 上的管控平台）
 * 支持 SDF 密码卡和 UKey 两种硬件模式（SDK >= 20260302）
 */
public interface VAuthServerSDKLibrary extends Library {

    // Linux 下库名是 libvauthsdk.so
    String os = System.getProperty("os.name").toLowerCase();
    String libName = os.contains("win") ? "VAuthSDK" : "vauthsdk";
    VAuthServerSDKLibrary INSTANCE = Native.load(libName, VAuthServerSDKLibrary.class);

    // ==================== 基础接口 ====================

    /** 初始化 SDK */
    boolean VAuth_Init();

    /** 释放 SDK 资源 */
    boolean VAuth_Cleanup();

    /** 释放 SDK 分配的内存 */
    void VAuth_Free(Pointer pObj);

    /** 获取错误码 */
    int VAuth_GetLastError();

    /** 根据错误码获取错误描述 */
    Pointer VAuth_GetErrorText(int nError);

    // ==================== 设备打开/关闭 ====================

    /** 打开 SDF 密码卡 */
    int VAuth_OpenSDF(String password, String authId);

    /**
     * 列举 UKey 设备信息（SDK >= 20260302 在 Linux 上可用）
     * @param pReply 返回 JSON 数组，包含 name/label/sn/cerSn/cerId/path，需要 VAuth_Free 释放
     * @return true-成功 false-失败
     */
    boolean VAuth_ListUkeyInfos(PointerByReference pReply);

    /**
     * 打开 UKey 设备
     * @param path UKey 路径，由 VAuth_ListUkeyInfos 返回的 JSON 中的 path 字段
     * @param password UKey 密码
     * @param authId 认证 ID
     * @return 设备句柄，>=0 成功，<0 失败
     */
    int VAuth_OpenUkey(String path, String password, String authId);

    /** 关闭句柄（UKey 或 SDF 通用） */
    boolean VAuth_CloseHandle(int handle);

    // ==================== 认证服务端接口 ====================

    /** 解析认证请求（第一步） */
    boolean VAuth_ParseAuthReq(int handle, String authId, String reqInfo, PointerByReference pReply);

    /** 解析认证信息（第二步） */
    boolean VAuth_ParseAuthInfo(int handle, String authId, String signCer, String reqInfo, PointerByReference pReply);

    /** 解析认证错误（第三步，可选） */
    boolean VAuth_ParseAuthError(int handle, String respInfo, PointerByReference pError);

    // ==================== 加解密辅助接口 ====================

    /** 密钥加密（服务端用客户端公钥加密会话密钥） */
    boolean VAuth_EncryptKey(int handle, String key, String cer, PointerByReference pReply);

    /** 设置密钥（服务端在 QueryKeyCallback 中调用） */
    boolean VAuth_SetKey(int handle, String id, String ver, String key);

    /** 设置证书（服务端在 QueryCerCallback 中调用） */
    boolean VAuth_SetCer(String id, int type, String cer);

    // ==================== 回调注册接口 ====================

    /** 注册查询密钥回调（服务端 ParseAuthInfo 过程中 SDK 主动调用） */
    boolean VAuth_SetQueryKeyCallback(FnQueryKeyCallBack fnCallBack, Pointer dwUser);

    /** 注册查询证书回调（服务端 ParseAuthInfo 过程中 SDK 主动调用） */
    boolean VAuth_SetQueryCerCallback(FnQueryCerCallBack fnCallBack, Pointer dwUser);

    /** 注册密钥结果回调（加解密场景，认证场景可不注册） */
    boolean VAuth_SetKeyCallback(FnKeyCallBack fnCallBack, Pointer dwUser);

    // ==================== 回调接口 ====================

    /** 查询密钥回调：SDK 在 ParseAuthInfo 时触发，需在回调中调用 VAuth_SetKey 提供加密密钥 */
    interface FnQueryKeyCallBack extends Callback {
        int invoke(int handle, String id, String ver, Pointer dwUser);
    }

    /** 查询证书回调：SDK 在 ParseAuthInfo 时触发，需在回调中调用 VAuth_SetCer 提供客户端证书 */
    interface FnQueryCerCallBack extends Callback {
        int invoke(String id, int type, Pointer dwUser);
    }

    /** 密钥结果回调（加解密场景） */
    interface FnKeyCallBack extends Callback {
        int invoke(String id, String ver, String key, Pointer dwUser);
    }

    /** UKey 事件回调（插入/拔出） */
    interface FnUkeyEventCallBack extends Callback {
        int invoke(int type, String name, String msg, Pointer dwUser);
    }

    /** 注册 UKey 事件回调 */
    boolean VAuth_SetUkeyEventCallback(FnUkeyEventCallBack fnEventCallBack, Pointer dwUser);
}
