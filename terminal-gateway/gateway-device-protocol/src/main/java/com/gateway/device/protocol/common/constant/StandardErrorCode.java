package com.gateway.device.protocol.common.constant;

/**
 * 标准错误码 —— 屏蔽厂商差异，统一对外暴露。
 */
public final class StandardErrorCode {

    public static final String SUCCESS = "SUCCESS";
    /**
     * 筛选条件未匹配到任何设备
     */
    public static final String NO_TARGET_DEVICE = "NO_TARGET_DEVICE";
    /**
     * 设备未在注册表中
     */
    public static final String DEVICE_NOT_FOUND = "DEVICE_NOT_FOUND";
    /**
     * 设备离线
     */
    public static final String DEVICE_OFFLINE = "DEVICE_OFFLINE";
    /**
     * 设备不支持该能力
     */
    public static final String UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";
    /**
     * 参数无效
     */
    public static final String INVALID_PARAM = "INVALID_PARAM";
    /**
     * 设备响应超时
     */
    public static final String TIMEOUT = "TIMEOUT";
    /**
     * 网络传输错误
     */
    public static final String TRANSPORT_ERROR = "TRANSPORT_ERROR";
    /**
     * 协议解析错误
     */
    public static final String PROTOCOL_ERROR = "PROTOCOL_ERROR";
    /**
     * 设备未登录，拒绝执行功能命令
     */
    public static final String DEVICE_NOT_LOGGED_IN = "DEVICE_NOT_LOGGED_IN";
    /**
     * 系统内部错误
     */
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";

    private StandardErrorCode() {
    }
}
