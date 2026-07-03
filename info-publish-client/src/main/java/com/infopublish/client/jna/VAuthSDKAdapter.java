package com.infopublish.client.jna;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.infopublish.client.jna.VAuthSDKLibrary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * VAuthSDK适配器（监管程序-客户端）
 */
@Slf4j
@Component
public class VAuthSDKAdapter {

    @Value("${vauth.mock-mode:true}")
    private Boolean mockMode;

    @Value("${vauth.auth-id:}")
    private String authId;

    @Value("${vauth.password:88888888}")
    private String password;

    private VAuthSDKLibrary realSdk;
    private VAuthSDKMock mockSdk;
    private volatile Boolean initializedMockMode;

    // Ukey事件监听器
    private final List<UkeyEventListener> ukeyEventListeners = new CopyOnWriteArrayList<>();
    private VAuthSDKLibrary.UkeyEventCallback ukeyEventCallback;

    public interface UkeyEventListener {
        void onUkeyEvent(int eventType, String deviceName, String message);
    }

    @PostConstruct
    public void init() {
        initializeSdk();
    }

    public synchronized void refreshRuntimeMode() {
        initializeSdk();
    }

    private synchronized void initializeSdk() {
        if (initializedMockMode != null && initializedMockMode.equals(mockMode)) {
            return;
        }
        if (mockMode) {
            log.warn("========================================");
            log.warn("  VAuthSDK 运行在 Mock 模式（监管程序）");
            log.warn("========================================");
            mockSdk = new VAuthSDKMock();
            mockSdk.VAuth_Init();
            mockSdk.setUkeyEventListener(this::notifyUkeyEvent);
            realSdk = null;
        } else {
            log.info("VAuthSDK 运行在真实模式（监管程序）");
            realSdk = VAuthSDKLibrary.INSTANCE;
            boolean success = realSdk.VAuth_Init();
            if (!success) {
                throw new RuntimeException("VAuthSDK初始化失败");
            }
            registerUkeyEventCallback();
            mockSdk = null;
        }
        initializedMockMode = mockMode;
    }

    private void registerUkeyEventCallback() {
        ukeyEventCallback = (type, name, msg, dwUser) -> {
            log.info("Ukey事件: type={}, name={}, msg={}", type, name, msg);
            notifyUkeyEvent(type, name, msg);
            return 0;
        };
        realSdk.VAuth_SetUkeyEventCallback(ukeyEventCallback, null);
    }

    private void notifyUkeyEvent(int eventType, String deviceName, String message) {
        for (UkeyEventListener listener : ukeyEventListeners) {
            try {
                listener.onUkeyEvent(eventType, deviceName, message);
            } catch (Exception e) {
                log.error("通知Ukey事件失败", e);
            }
        }
    }

    public void addUkeyEventListener(UkeyEventListener listener) {
        ukeyEventListeners.add(listener);
    }

    public void setMockMode(Boolean mockMode) {
        this.mockMode = mockMode;
    }

    public void setAuthId(String authId) {
        this.authId = authId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 列出UKey设备
     */
    public String listUkeyInfos() {
        if (mockMode) {
            return mockSdk.VAuth_ListUkeyInfos();
        } else {
            PointerByReference pReply = new PointerByReference();
            boolean success = realSdk.VAuth_ListUkeyInfos(pReply);
            if (!success || pReply.getValue() == null) {
                return null;
            }
            String result = pReply.getValue().getString(0, "UTF-8");
            realSdk.VAuth_Free(pReply.getValue());
            return result;
        }
    }

    /**
     * 打开UKey（修正版：添加authId参数）
     */
    public int openUkey(String path, String password, String authId) {
        if (mockMode) {
            return mockSdk.VAuth_OpenUkey(path, password);
        } else {
            return realSdk.VAuth_OpenUkey(path, password, authId);
        }
    }

    /**
     * 关闭句柄
     */
    public boolean closeHandle(int handle) {
        if (mockMode) {
            return mockSdk.VAuth_CloseHandle(handle);
        } else {
            return realSdk.VAuth_CloseHandle(handle);
        }
    }

    /**
     * 设置认证服务器信息
     */
    public boolean setAuthServerInfo(int handle, String serverId, String serverCert) {
        if (mockMode) {
            return mockSdk.VAuth_SetAuthServerInfo(handle, serverId, serverCert);
        } else {
            return realSdk.VAuth_SetAuthServerInfo(handle, serverId, serverCert);
        }
    }

    /**
     * 生成认证请求
     */
    public String buildAuthReq(int handle) {
        if (mockMode) {
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
        if (mockMode) {
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
        if (mockMode) {
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
     * Build a signed/encrypted VAuth envelope. The caller owns the opened handle.
     */
    public byte[] encryptData(int handle, boolean isSign, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("encrypt data is empty");
        }
        if (mockMode) {
            return mockSdk.VAuth_EncryptData(handle, isSign, data);
        }

        PointerByReference pOutData = new PointerByReference();
        IntByReference outLen = new IntByReference();
        boolean success = realSdk.VAuth_EncryptData(handle, isSign, data, data.length, pOutData, outLen);
        Pointer pointer = pOutData.getValue();
        if (!success || pointer == null || outLen.getValue() <= 0) {
            throw new IllegalStateException("VAuth_EncryptData failed: " + getLastErrorText());
        }
        try {
            return pointer.getByteArray(0, outLen.getValue());
        } finally {
            realSdk.VAuth_Free(pointer);
        }
    }

    /**
     * Verify/decrypt a VAuth envelope. Kept here for symmetric SDK access.
     */
    public byte[] decryptData(int handle, boolean isVerify, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("decrypt data is empty");
        }
        if (mockMode) {
            return mockSdk.VAuth_DecryptData(handle, isVerify, data);
        }

        PointerByReference pOutData = new PointerByReference();
        IntByReference outLen = new IntByReference();
        boolean success = realSdk.VAuth_DecryptData(handle, isVerify, data, data.length, pOutData, outLen);
        Pointer pointer = pOutData.getValue();
        if (!success || pointer == null || outLen.getValue() <= 0) {
            throw new IllegalStateException("VAuth_DecryptData failed: " + getLastErrorText());
        }
        try {
            return pointer.getByteArray(0, outLen.getValue());
        } finally {
            realSdk.VAuth_Free(pointer);
        }
    }

    private String getLastErrorText() {
        if (mockMode) {
            return "mock";
        }
        try {
            int errorCode = realSdk.VAuth_GetLastError();
            String text = realSdk.VAuth_GetErrorText(errorCode);
            return errorCode + (text == null ? "" : (": " + text));
        } catch (Exception e) {
            return "unknown error";
        }
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
