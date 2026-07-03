package com.gateway.auth.jna;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * VAuthSDK Mock实现
 * 用于在没有真实DLL时进行开发测试
 * 注意：此实现仅用于开发调试，没有真实的加密认证能力
 */
@Slf4j
public class VAuthSDKMock {
    
    private boolean initialized = false;
    private final Map<Integer, String> handleMap = new ConcurrentHashMap<>();
    private int handleCounter = 1;
    
    // Ukey事件监听器
    private UkeyEventListener ukeyEventListener;
    
    // 模拟Ukey插拔的定时任务
    private ScheduledExecutorService mockEventScheduler;
    
    // 模拟Ukey是否插入
    private volatile boolean mockUkeyInserted = false;
    
    /**
     * Ukey事件监听器接口
     */
    @FunctionalInterface
    public interface UkeyEventListener {
        void onEvent(int type, String name, String msg);
    }
    
    /**
     * 设置Ukey事件监听器
     */
    public void setUkeyEventListener(UkeyEventListener listener) {
        this.ukeyEventListener = listener;
        log.info("[Mock] Ukey事件监听器已设置");
    }
    
    /**
     * 模拟Ukey插入事件
     */
    public void simulateUkeyInsert(String deviceName) {
        if (!mockUkeyInserted) {
            mockUkeyInserted = true;
            log.info("[Mock] 模拟Ukey插入: {}", deviceName);
            if (ukeyEventListener != null) {
                ukeyEventListener.onEvent(1, deviceName, "Ukey已插入");
            }
        }
    }
    
    /**
     * 模拟Ukey拔出事件
     */
    public void simulateUkeyRemove(String deviceName) {
        if (mockUkeyInserted) {
            mockUkeyInserted = false;
            log.info("[Mock] 模拟Ukey拔出: {}", deviceName);
            if (ukeyEventListener != null) {
                ukeyEventListener.onEvent(2, deviceName, "Ukey已拔出");
            }
        }
    }
    
    /**
     * 获取模拟Ukey插入状态
     */
    public boolean isMockUkeyInserted() {
        return mockUkeyInserted;
    }
    
    // ========== 初始化相关 ==========
    
    public boolean VAuth_Init() {
        log.warn("[Mock] VAuth_Init - SDK初始化（Mock模式）");
        initialized = true;
        return true;
    }
    
    public boolean VAuth_Cleanup() {
        log.warn("[Mock] VAuth_Cleanup - SDK清理（Mock模式）");
        initialized = false;
        handleMap.clear();
        
        // 停止模拟事件调度器
        if (mockEventScheduler != null && !mockEventScheduler.isShutdown()) {
            mockEventScheduler.shutdown();
        }
        return true;
    }
    
    public void VAuth_Free(Object pObj) {
        log.debug("[Mock] VAuth_Free - 释放内存（Mock模式）");
    }
    
    public int VAuth_GetLastError() {
        log.debug("[Mock] VAuth_GetLastError - 获取错误码（Mock模式）");
        return 0;
    }
    
    public String VAuth_GetZsnpErrorText(int nError) {
        log.debug("[Mock] VAuth_GetZsnpErrorText - 错误码: {}", nError);
        return "Mock Error: " + nError;
    }
    
    // ========== UKey操作 ==========
    
    public String VAuth_ListUkeyInfos() {
        log.info("[Mock] VAuth_ListUkeyInfos - 列出UKey设备");
        return "[" +
                "{\"name\":\"MockUKey-Gateway\",\"label\":\"发布网关UKey\",\"sn\":\"MOCK-SN-GATEWAY-001\"," +
                "\"cerSn\":\"CERT-GATEWAY-001\",\"cerId\":\"publish_gateway\",\"path\":\"/mock/ukey/gateway\"}," +
                "{\"name\":\"MockUKey-Test\",\"label\":\"测试UKey\",\"sn\":\"MOCK-SN-002\"," +
                "\"cerSn\":\"CERT-002\",\"cerId\":\"test_device\",\"path\":\"/mock/ukey2\"}" +
                "]";
    }
    
    public int VAuth_OpenUkey(String path, String password) {
        log.info("[Mock] VAuth_OpenUkey - 打开UKey: path={}, password=***", path);
        int handle = handleCounter++;
        handleMap.put(handle, "ukey:" + path);
        return handle;
    }
    
    public int VAuth_OpenSDF(String password) {
        log.info("[Mock] VAuth_OpenSDF - 打开SDF加密卡: password=***");
        int handle = handleCounter++;
        handleMap.put(handle, "sdf:server");
        return handle;
    }
    
    public boolean VAuth_CloseHandle(int handle) {
        log.info("[Mock] VAuth_CloseHandle - 关闭句柄: {}", handle);
        handleMap.remove(handle);
        return true;
    }
    
    // ========== 客户端认证相关 ==========
    
    public boolean VAuth_SetAuthServerInfo(int handle, String id, String signCer) {
        log.info("[Mock] VAuth_SetAuthServerInfo - 设置服务器信息: handle={}, id={}", handle, id);
        return true;
    }
    
    public String VAuth_BuildAuthReq(int handle) {
        log.info("[Mock] VAuth_BuildAuthReq - 生成认证请求: handle={}", handle);
        String reqId = UUID.randomUUID().toString();
        return "{\"type\":\"authReq\",\"reqId\":\"" + reqId + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
    }
    
    public String VAuth_BuildAuthInfo(int handle, String respInfo) {
        log.info("[Mock] VAuth_BuildAuthInfo - 生成认证信息: handle={}", handle);
        return "{\"type\":\"authInfo\",\"authId\":\"" + UUID.randomUUID() + "\",\"status\":\"success\"}";
    }
    
    public boolean VAuth_CheckAuthResult(int handle, String respInfo) {
        log.info("[Mock] VAuth_CheckAuthResult - 检查认证结果: handle={}", handle);
        // Mock 总是返回认证成功
        return true;
    }
    
    public String VAuth_CheckAuthResultError(int handle, String respInfo) {
        log.info("[Mock] VAuth_CheckAuthResultError - 认证错误信息: handle={}", handle);
        return ""; // 空字符串表示没有错误
    }
    
    // ========== 服务端认证相关 ==========
    
    public String VAuth_ParseAuthReq(int handle, String authId, String reqInfo) {
        log.info("[Mock] VAuth_ParseAuthReq - 解析认证请求: handle={}, authId={}", handle, authId);
        return "{\"type\":\"authReqResp\",\"respId\":\"" + UUID.randomUUID() + "\",\"status\":\"ok\"}";
    }
    
    public String VAuth_ParseAuthInfo(int handle, String authId, String signCer, String reqInfo) {
        log.info("[Mock] VAuth_ParseAuthInfo - 解析认证信息: handle={}, authId={}", handle, authId);
        return "{\"type\":\"authResult\",\"result\":\"success\",\"sessionKey\":\"" + UUID.randomUUID() + "\"}";
    }
    
    public String VAuth_ParseAuthError(int handle, String respInfo) {
        log.info("[Mock] VAuth_ParseAuthError - 解析认证错误: handle={}", handle);
        return "{\"error\":\"none\",\"message\":\"Mock认证无错误\"}";
    }
    
    // ========== 加解密操作 ==========
    
    public byte[] VAuth_EncryptData(int handle, boolean isSign, byte[] pData) {
        log.info("[Mock] VAuth_EncryptData - 数据加密: handle={}, isSign={}, dataLen={}", 
                handle, isSign, pData.length);
        
        // Mock实现：简单地将数据反转并添加标记
        byte[] encrypted = new byte[pData.length + 8];
        encrypted[0] = (byte) 0xAA; // Mock加密标记
        encrypted[1] = (byte) 0xBB;
        encrypted[2] = (byte) (isSign ? 0x01 : 0x00);
        encrypted[3] = (byte) 0xFF;
        
        // 反转数据（模拟加密）
        for (int i = 0; i < pData.length; i++) {
            encrypted[i + 4] = (byte) (~pData[i]);
        }
        
        encrypted[encrypted.length - 4] = (byte) 0xCC;
        encrypted[encrypted.length - 3] = (byte) 0xDD;
        encrypted[encrypted.length - 2] = (byte) 0xEE;
        encrypted[encrypted.length - 1] = (byte) 0xFF;
        
        return encrypted;
    }
    
    public byte[] VAuth_DecryptData(int handle, boolean isVerify, byte[] pData) {
        log.info("[Mock] VAuth_DecryptData - 数据解密: handle={}, isVerify={}, dataLen={}", 
                handle, isVerify, pData.length);
        
        // Mock实现：检查标记并反转数据
        if (pData.length < 8 || pData[0] != (byte) 0xAA || pData[1] != (byte) 0xBB) {
            log.warn("[Mock] 数据格式不正确");
            return null;
        }
        
        byte[] decrypted = new byte[pData.length - 8];
        for (int i = 0; i < decrypted.length; i++) {
            decrypted[i] = (byte) (~pData[i + 4]);
        }
        
        return decrypted;
    }
    
    // ========== 密钥管理 ==========
    
    public boolean VAuth_SetKey(String id, String ver, String key) {
        log.info("[Mock] VAuth_SetKey - 设置密钥: id={}, ver={}", id, ver);
        return true;
    }
    
    public boolean VAuth_SetCer(String id, int type, String cer) {
        log.info("[Mock] VAuth_SetCer - 设置证书: id={}, type={}", id, type);
        return true;
    }
    
    public String VAuth_EncryptKey(String key, String cer) {
        log.info("[Mock] VAuth_EncryptKey - 密钥加密");
        // 返回Base64编码的Mock加密密钥
        return "MOCK_ENCRYPTED_KEY_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    // ========== 辅助方法 ==========
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public int getHandleCount() {
        return handleMap.size();
    }
}
