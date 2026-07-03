package com.gateway.device.protocol.base.novastar.viplexcore;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * ViplexCore SDK 抽象通道 —— 解耦 protocol 模块与 JNA 原生库。
 *
 * <p>所有 SDK 调用通过 {@link #execute(SdkFunction, String, Duration)} 进行，
 * 由 transport 模块的 {@code ViplexCoreLifecycleManager} 实现，内部映射到 JNA 调用。</p>
 *
 * <p>protocol 模块无 {@code com.sun.jna} 依赖。</p>
 */
public interface ViplexCoreChannel {

    /**
     * SDK 是否已初始化
     */
    boolean isInitialized();

    /**
     * 通用 SDK 执行（内部自动包装为同步等待）。
     *
     * @param function   SDK 函数枚举
     * @param jsonParams JSON 参数字符串
     * @param timeout    超时时间
     * @return ViplexResponse，success 取决于回调 code == 0
     */
    ViplexResponse execute(SdkFunction function, String jsonParams, Duration timeout);

    /**
     * 带进度回调的 SDK 执行（SDK 多次回调直至完成判定器返回 true）。
     *
     * @param function   SDK 函数枚举
     * @param jsonParams JSON 参数字符串
     * @param timeout    超时时间
     * @param isComplete 完成判定器
     * @return ViplexResponse 最终响应
     */
    ViplexResponse executeWithProgress(SdkFunction function, String jsonParams,
                                       Duration timeout, Predicate<ViplexResponse> isComplete);

    /**
     * 登录终端（指定 loginType）
     */
    ViplexResponse login(String sn, String username, String password,
                         Duration timeout, LoginType loginType);

    /**
     * 判断指定 SN + 屏体管理登录类型是否已登录
     */
    boolean isLoggedIn(String sn);

    /**
     * 判断指定 SN + loginType 是否已登录
     */
    boolean isLoggedIn(String sn, LoginType loginType);

    /**
     * 广播搜索单台设备
     */
    ViplexResponse searchDevice(Duration timeout);

    /**
     * 广播搜索所有设备
     */
    ViplexResponse searchAllDevices(Duration timeout);

    /**
     * 获取已知可用账号
     */
    ViplexCoreAccount getKnownAccount(String sn);

    /**
     * 记录可用账号
     */
    void rememberAccount(String sn, ViplexCoreAccount account);
}
