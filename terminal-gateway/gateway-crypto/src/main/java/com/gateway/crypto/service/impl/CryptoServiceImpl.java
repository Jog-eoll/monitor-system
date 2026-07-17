package com.gateway.crypto.service.impl;

import com.gateway.auth.config.VAuthConfig;
import com.gateway.auth.jna.VAuthPackBridgeLibrary;
import com.gateway.auth.jna.VAuthSDKLibrary;
import com.gateway.auth.ukey.UkeyEventHandler;
import com.gateway.common.service.CryptoService;
import com.gateway.common.service.SvacFileCryptoService;
import com.gateway.crypto.service.SvacModulePresenceGuard;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基于 VAuthSDK 的真实国密加解密服务（终端解密网关）
 *
 * <p>启用条件：vauth.mock-mode=false
 * <p>算法：SM4 对称加密 + SM2 签名（国密）
 * <p>认证流程与监管程序客户端（info-publish-client/ClientAuthService）完全一致：
 * VAuth_OpenUkey → 三步握手双向认证 → 注册解密回调 → 加解密就绪
 *
 * <p>终端网关为解密侧，必须在认证成功后注册 QueryKeyCallback + QueryCerCallback，
 * 否则 VAuth_DecryptData 因缺少会话密钥而失败。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "vauth.mock-mode", havingValue = "false")
public class CryptoServiceImpl implements CryptoService, SvacFileCryptoService {

    private static final long AUTH_RETRY_INTERVAL_MS = 10_000L;
    private static final int PACK_OUTPUT_BUFFER_BYTES = 256 * 1024;
    private static final int PACK_OUTPUT_OVERHEAD_BYTES = 1024 * 1024;
    private static final int PACK_BRIDGE_OK = 1;
    private static final String[] SVAC_RUNTIME_FILES = {
            "libvauthsdk.so",
            "libvauthpackbridge.so",
            "libCommonLib.so",
            "libGmsslUtility.so",
            "libzbaselib.so",
            "libZxTransRepackSDK.so",
            "libavcodec.so.62",
            "libavformat.so.62",
            "libavutil.so.60",
            "libswresample.so.6",
            "libswscale.so.9",
            "StreamAnalyzer.zl",
            "SVAC2Encoder.zl",
            "SVAC2Decoder.zl",
            "FFMPEGUtility.zl",
            "pack.svac"
    };

    @Resource
    private VAuthConfig vAuthConfig;

    /**
     * SDK 实例（JNA 直接调用，Linux 下加载 libvauthsdk.so）
     */
    private VAuthSDKLibrary sdk;
    private VAuthPackBridgeLibrary packBridge;

    /**
     * 当前打开的 UKey 句柄，-1 表示未打开
     */
    private volatile int deviceHandle = -1;

    /**
     * 认证状态
     */
    private volatile boolean authenticated = false;

    private volatile long lastAuthRetryTimeMs = 0L;

    /**
     * JNA 回调对象必须持有强引用，否则会被 GC 回收导致 SDK 回调时崩溃
     */
    private VAuthSDKLibrary.UkeyEventCallback ukeyEventCallback;
    private VAuthSDKLibrary.QueryKeyCallback queryKeyCallback;
    private VAuthSDKLibrary.QueryCerCallback queryCerCallback;
    /**
     * KeyCallback 强引用：接收管控平台下发的会话密钥，存入 keyCache
     */
    private VAuthSDKLibrary.KeyCallback keyCallback;

    @Resource
    private UkeyEventHandler ukeyEventHandler;

    @Resource
    private SvacModulePresenceGuard svacModulePresenceGuard;

    /**
     * 解密侧密钥/证书缓存：SDK 触发 QueryKeyCallback 时从此缓存取值
     * key = "authId_ver"（密钥）或 "authId_type"（证书）
     */
    private final Map<String, String> keyCache = new ConcurrentHashMap<>();
    private final Map<String, String> cerCache = new ConcurrentHashMap<>();

    /**
     * UKey 插拔处理线程池。
     * 禁止在 SDK 回调线程内直接调用 SDK 函数（会死锁），必须异步执行。
     */
    private final ScheduledExecutorService ukeyOpenExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ukey-open-thread");
                t.setDaemon(true);
                return t;
            });

    // ===================== 生命周期 =====================

    @PostConstruct
    public synchronized void init() {
        log.info("=== CryptoServiceImpl 初始化（UKey 国密模式）===");
        log.info("=== 加密模式: {} ===",
                Boolean.TRUE.equals(vAuthConfig.getSvacMode()) ? "SVAC2编码加密" : "国密整包加密");
        sdk = VAuthSDKLibrary.INSTANCE;
        if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
            validateSvacRuntimeFiles();
            packBridge = VAuthPackBridgeLibrary.INSTANCE;
            log.info("SVAC PackData bridge loaded: libvauthpackbridge");
        }

        boolean initOk = sdk.VAuth_Init();
        if (!initOk) {
            int err = sdk.VAuth_GetLastError();
            throw new RuntimeException("VAuthSDK 初始化失败: errorCode=" + err
                    + ", msg=" + sdk.VAuth_GetErrorText(err));
        }
        log.info("VAuthSDK 初始化成功");

        // 注册 UKey 插拔回调（强引用保存在字段中，防止 GC）
        registerUkeyEventCallback();

        // 尝试扫描并打开已插入的 UKey
        int handle = openUkeyByList();
        if (handle >= 0) {
            deviceHandle = handle;
            tryAuthenticate();
        } else {
            log.warn("启动时未检测到 UKey，等待 UKey 插入后自动认证...");
        }
    }

    @PreDestroy
    public synchronized void destroy() {
        ukeyOpenExecutor.shutdownNow();
        if (deviceHandle >= 0) {
            sdk.VAuth_CloseHandle(deviceHandle);
            log.info("VAuthSDK UKey 句柄已释放: handle={}", deviceHandle);
        }
        sdk.VAuth_Cleanup();
        log.info("VAuthSDK 已清理");
    }

    // ===================== CryptoService 管理接口实现 =====================

    @Override
    public boolean isAuthenticated() {
        return authenticated && deviceHandle >= 0;
    }

    @Override
    public boolean isSvacMode() {
        return Boolean.TRUE.equals(vAuthConfig.getSvacMode());
    }

    @Override
    public boolean isSvacModuleReady() {
        return svacModulePresenceGuard.isReady();
    }

    @Override
    public String getSvacModuleStatus() {
        return svacModulePresenceGuard.getStatus();
    }

    @Override
    public boolean isReady() {
        return authenticated && deviceHandle >= 0 && svacModulePresenceGuard.isReady();
    }

    @Override
    public synchronized byte[] encryptFileData(byte[] data) {
        return doFileDataCrypto("encrypt", data, Boolean.TRUE.equals(vAuthConfig.getSignEnabled()));
    }

    @Override
    public synchronized byte[] decryptFileData(byte[] data) {
        return doFileDataCrypto("decrypt", data, Boolean.TRUE.equals(vAuthConfig.getSignEnabled()));
    }

    @Override
    public synchronized byte[] encryptPackData(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("encryptPackData")) {
            return null;
        }
        if (!authenticated || deviceHandle < 0) {
            scheduleReAuthenticateIfNecessary();
            log.error("SVAC pack encrypt refused: authenticated={}, handle={}, dataLen={}",
                    authenticated, deviceHandle, data.length);
            return null;
        }
        if (!ensurePackBridgeReady()) {
            return null;
        }
        return encryptPackDataDirectNoFree(data, Boolean.TRUE.equals(vAuthConfig.getSignEnabled()));
    }

    @Override
    public synchronized byte[] decryptPackData(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("decryptPackData")) {
            return null;
        }
        if (!authenticated || deviceHandle < 0) {
            scheduleReAuthenticateIfNecessary();
            log.error("SVAC pack decrypt refused: authenticated={}, handle={}, dataLen={}",
                    authenticated, deviceHandle, data.length);
            return null;
        }
        if (!ensurePackBridgeReady()) {
            return null;
        }
        return decryptPackDataDirectNoFree(data, Boolean.TRUE.equals(vAuthConfig.getSignEnabled()));
    }

    @Override
    public void triggerReAuthenticate() {
        log.info("[手动触发] 重新认证(async)...");
        ukeyOpenExecutor.submit(this::reInitUkey);
    }

    @Override
    public boolean reAuthenticate(String reason) {
        log.info("[REAUTH] start waitable re-auth, reason={}", reason);
        // Synchronous path so MQTT REAUTH SUCCESS means handshake finished.
        reInitUkey();
        return isAuthenticated();
    }

    // ===================== CryptoService 加解密实现 =====================

    /**
     * 加密数据（SM4 + 可选 SM2 签名）
     * 终端网关通常作为解密侧，此方法用于将情报板响应加密后回传给发布网关。
     */
    @Override
    public synchronized byte[] encrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("encrypt")) {
            return null;
        }
        if (!authenticated || deviceHandle < 0) {
            scheduleReAuthenticateIfNecessary();
            log.warn("encrypt: UKey 未认证，数据透传（未加密）");
            return isSvacMode() ? null : data;
        }
        try {
            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();
            boolean isSign = Boolean.TRUE.equals(vAuthConfig.getSignEnabled());
            if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
                return encryptPackDataDirectNoFree(data, isSign);
            }
            boolean success;

            if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
                // SVAC2 编码加密模式
                success = sdk.VAuth_EncryptPackData(
                        deviceHandle, boolToNative(isSign),
                        data, data.length,
                        pOutData, outLen
                ) != 0;
            } else {
                // 国密整包加密模式（默认）
                success = sdk.VAuth_EncryptData(
                        deviceHandle, isSign,
                        data, data.length,
                        pOutData, outLen
                );
            }

            if (!success || pOutData.getValue() == null) {
                int err = sdk.VAuth_GetLastError();
                log.error("encrypt 失败: errorCode={}, msg={}", err, sdk.VAuth_GetErrorText(err));
                return isSvacMode() ? null : data;
            }

            int length = outLen.getValue();
            if (length <= 0) {
                log.error("encrypt 失败: invalidOutputLength={}", length);
                sdk.VAuth_Free(pOutData.getValue());
                return isSvacMode() ? null : data;
            }
            byte[] encrypted = pOutData.getValue().getByteArray(0, length);
            sdk.VAuth_Free(pOutData.getValue());
            log.debug("encrypt 成功: 原始{}字节 -> 加密{}字节", data.length, length);
            return encrypted;

        } catch (Exception e) {
            log.error("encrypt 异常，数据透传", e);
            return isSvacMode() ? null : data;
        }
    }

    /**
     * 解密数据（SM4 解密 + 可选 SM2 验签）
     * 终端网关核心功能：收到发布网关的加密 UDP 包后调用此方法还原原始数据。
     */
    @Override
    public synchronized byte[] decrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("decrypt")) {
            return null;
        }
        if (!authenticated || deviceHandle < 0) {
            scheduleReAuthenticateIfNecessary();
            log.warn("decrypt: UKey 未认证，数据透传（未解密）");
            return isSvacMode() ? null : data;
        }
        try {
            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();

            // 普通整包模式保持不验签；SVAC PackData 按签名配置解析封装。
            boolean isVerify = Boolean.TRUE.equals(vAuthConfig.getSvacMode())
                    && Boolean.TRUE.equals(vAuthConfig.getSignEnabled());
            if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
                return decryptPackDataDirectNoFree(data, isVerify);
            }
            boolean success;

            if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
                // SVAC2 编码解密模式
                success = sdk.VAuth_DecryptPackData(
                        deviceHandle, boolToNative(isVerify),
                        data, data.length,
                        pOutData, outLen
                ) != 0;
            } else {
                // 国密整包解密模式（默认）
                success = sdk.VAuth_DecryptData(
                        deviceHandle, isVerify,
                        data, data.length,
                        pOutData, outLen
                );
            }

            if (!success || pOutData.getValue() == null) {
                int err = sdk.VAuth_GetLastError();
                String errMsg = sdk.VAuth_GetErrorText(err);
                log.error("decrypt 失败: errorCode={}, msg={}", err, errMsg);
                return null;
            }

            int length = outLen.getValue();
            if (length <= 0) {
                log.error("decrypt 失败: invalidOutputLength={}", length);
                sdk.VAuth_Free(pOutData.getValue());
                return null;
            }
            byte[] decrypted = pOutData.getValue().getByteArray(0, length);
            sdk.VAuth_Free(pOutData.getValue());
            log.debug("decrypt 成功: 加密{}字节 -> 解密{}字节", data.length, length);
            return decrypted;

        } catch (Exception e) {
            log.error("decrypt 异常", e);
            return null;
        }
    }

    // ===================== 认证核心流程 =====================

    private byte[] encryptPackDataDirectNoFree(byte[] data, boolean isSign) {
        if (!ensurePackBridgeReady()) {
            return null;
        }
        byte[] outBuffer = new byte[packOutputBufferBytes(data.length)];
        IntByReference outLen = new IntByReference();
        int result = packBridge.VAuthBridge_EncryptPackData(
                deviceHandle, boolToNative(isSign),
                data, data.length,
                outBuffer, outBuffer.length,
                outLen
        );
        return handlePackDataBridgeResult("encrypt", "sign", isSign, data, outBuffer, outLen.getValue(), result);
    }

    private byte[] doFileDataCrypto(String operation, byte[] data, boolean flag) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("file-" + operation)) {
            return null;
        }
        if (!authenticated || deviceHandle < 0) {
            scheduleReAuthenticateIfNecessary();
            log.error("SVAC file {} refused: authenticated={}, handle={}, dataLen={}",
                    operation, authenticated, deviceHandle, data.length);
            return null;
        }

        PointerByReference pOutData = new PointerByReference();
        IntByReference outLen = new IntByReference();
        Pointer outPtr = null;
        String apiName = "encrypt".equals(operation)
                ? "VAuth_EncryptFileData" : "VAuth_DecryptFileData";
        try {
            boolean success;
            if ("encrypt".equals(operation)) {
                success = sdk.VAuth_EncryptFileData(deviceHandle, flag, data, data.length, pOutData, outLen);
            } else {
                success = sdk.VAuth_DecryptFileData(deviceHandle, flag, data, data.length, pOutData, outLen);
            }

            outPtr = pOutData.getValue();
            if (!success || outPtr == null) {
                int err = sdk.VAuth_GetLastError();
                log.error("SVAC file {} failed: api={}, handle={}, flag={}, dataLen={}, outLen={}, errorCode={}, msg={}",
                        operation, apiName, deviceHandle, flag, data.length, outLen.getValue(), err, getErrorText(err));
                return null;
            }
            int length = outLen.getValue();
            if (length <= 0) {
                log.error("SVAC file {} failed: api={}, handle={}, flag={}, dataLen={}, outLen={}, reason=invalidOutputLength",
                        operation, apiName, deviceHandle, flag, data.length, length);
                return null;
            }
            byte[] output = outPtr.getByteArray(0, length);
            log.info("SVAC file {} success: api={}, inputBytes={}, outputBytes={}, flag={}",
                    operation, apiName, data.length, length, flag);
            return output;
        } catch (Exception e) {
            log.error("SVAC file {} exception: api={}, handle={}, dataLen={}",
                    operation, apiName, deviceHandle, data.length, e);
            return null;
        } finally {
            if (outPtr != null) {
                sdk.VAuth_Free(outPtr);
            }
        }
    }

    private byte[] decryptPackDataDirectNoFree(byte[] data, boolean isVerify) {
        if (!ensurePackBridgeReady()) {
            return null;
        }
        byte[] outBuffer = new byte[packOutputBufferBytes(data.length)];
        IntByReference outLen = new IntByReference();
        int result = packBridge.VAuthBridge_DecryptPackData(
                deviceHandle, boolToNative(isVerify),
                data, data.length,
                outBuffer, outBuffer.length,
                outLen
        );
        return handlePackDataBridgeResult("decrypt", "verify", isVerify, data, outBuffer, outLen.getValue(), result);
    }

    private byte[] handlePackDataBridgeResult(String operation, String flagName, boolean flagValue,
                                              byte[] input, byte[] outBuffer, int length, int result) {
        String apiName = "encrypt".equals(operation) ? "VAuth_EncryptPackData" : "VAuth_DecryptPackData";
        if (result != PACK_BRIDGE_OK) {
            int err = sdk.VAuth_GetLastError();
            log.error("{} failed: api={}, bridgeResult={}, handle={}, {}={}, dataLen={}, outLen={}, outCapacity={}, errorCode={}, msg={}",
                    operation, apiName, result, deviceHandle, flagName, flagValue,
                    input.length, length, outBuffer.length, err, getErrorText(err));
            return null;
        }
        if (length <= 0 || length > outBuffer.length) {
            log.error("{} failed: api={}, bridgeResult={}, handle={}, {}={}, dataLen={}, outLen={}, outCapacity={}, reason=invalidOutputLength",
                    operation, apiName, result, deviceHandle, flagName, flagValue, input.length, length, outBuffer.length);
            return null;
        }

        byte[] output = new byte[length];
        System.arraycopy(outBuffer, 0, output, 0, length);
        log.info("{} success: api={}, inputBytes={}, outputBytes={}, {}={}, bridge=true",
                operation, apiName, input.length, length, flagName, flagValue);
        return output;
    }

    private void scheduleReAuthenticateIfNecessary() {
        long now = System.currentTimeMillis();
        if (now - lastAuthRetryTimeMs < AUTH_RETRY_INTERVAL_MS) {
            return;
        }
        lastAuthRetryTimeMs = now;
        log.info("[自动触发] UKey 未认证，提交异步重新认证任务");
        ukeyOpenExecutor.submit(this::reInitUkey);
    }

    /**
     * 尝试认证：设置服务端信息 + 三步握手（与 info-publish-client/ClientAuthService 完全一致）
     */
    private synchronized void tryAuthenticate() {
        try {
            log.info("=== 开始与管控平台双向认证 ===");

            // Step1: 读取并设置服务端证书
            String serverCert = loadCertificate(vAuthConfig.getServerCertPath());
            boolean setOk = sdk.VAuth_SetAuthServerInfo(
                    deviceHandle, vAuthConfig.getServerId(), serverCert
            );
            if (!setOk) {
                log.error("VAuth_SetAuthServerInfo 失败");
                return;
            }

            // Step2: 生成认证请求
            PointerByReference pReq = new PointerByReference();
            if (!sdk.VAuth_BuildAuthReq(deviceHandle, pReq) || pReq.getValue() == null) {
                log.error("VAuth_BuildAuthReq 失败");
                return;
            }
            String authReq = pReq.getValue().getString(0, "UTF-8");
            sdk.VAuth_Free(pReq.getValue());

            // Step3: POST /auth/server/request
            Map<String, Object> resp1 = sendToControlPlatform("/auth/server/request", authReq, null);
            if (!isSuccess(resp1)) {
                log.error("管控平台拒绝认证请求: {}", resp1);
                return;
            }

            // Step4: 生成认证信息
            PointerByReference pInfo = new PointerByReference();
            String resp1Data = (String) resp1.get("data");
            log.debug("管控平台返回的认证数据(resp1Data): length={}, content={}",
                    resp1Data != null ? resp1Data.length() : 0, resp1Data);
            if (!sdk.VAuth_BuildAuthInfo(deviceHandle, resp1Data, pInfo) || pInfo.getValue() == null) {
                int errCode = sdk.VAuth_GetLastError();
                log.error("VAuth_BuildAuthInfo 失败, errCode={}, errMsg={}", errCode, getErrorText(errCode));
                return;
            }
            String authInfo = pInfo.getValue().getString(0, "UTF-8");
            sdk.VAuth_Free(pInfo.getValue());

            // Step5: POST /auth/server/verify（携带本端UKey签名证书）
            String clientCert = "";
            try {
                clientCert = loadCertificate(vAuthConfig.getClientCertPath());
                log.debug("客户端证书已加载，长度={}", clientCert.length());
            } catch (Exception e) {
                log.warn("加载客户端证书失败（管控平台 ParseAuthInfo 验签可能失败）: {}", e.getMessage());
            }
            Map<String, Object> resp2 = sendToControlPlatform("/auth/server/verify", authInfo, clientCert);
            if (!isSuccess(resp2)) {
                log.error("管控平台拒绝认证信息: {}", resp2);
                return;
            }

            // Step6: 检查认证结果
            PointerByReference pError = new PointerByReference();
            String resp2Data = (String) resp2.get("data");
            boolean authOk = sdk.VAuth_CheckAuthResult(deviceHandle, resp2Data, pError);
            Pointer errPtr = pError.getValue();
            if (errPtr != null) {
                String errMsg = errPtr.getString(0, "UTF-8");
                sdk.VAuth_Free(errPtr);
                if (!authOk) {
                    log.error("认证结果校验失败: {}", errMsg);
                    return;
                }
            }

            if (!authOk) {
                log.error("VAuth_CheckAuthResult 返回失败");
                return;
            }

            authenticated = true;
            log.info("=== 与管控平台双向认证成功，国密解密就绪 ===");

            // 认证成功后立即注册解密所需回调（必须步骤，否则 DecryptData 缺少会话密钥）
            registerDecryptCallbacks();

        } catch (Exception e) {
            log.error("认证过程异常", e);
            authenticated = false;
        }
    }

    /**
     * 重新初始化 UKey（UKey 插入后在异步线程中调用）
     */
    private synchronized void reInitUkey() {
        log.info("[UKey插入] 重新扫描并认证...");
        if (deviceHandle >= 0) {
            sdk.VAuth_CloseHandle(deviceHandle);
            deviceHandle = -1;
        }
        authenticated = false;
        int handle = openUkeyByList();
        if (handle >= 0) {
            deviceHandle = handle;
            tryAuthenticate();
        } else {
            log.warn("[UKey插入] 扫描到设备但打开失败，等待下次插入事件");
        }
    }

    // ===================== 回调注册 =====================

    /**
     * 注册 UKey 插拔事件回调
     * 插入→异步重新认证；拔出→清除句柄和认证状态（数据自动降级透传）
     */
    private void registerUkeyEventCallback() {
        ukeyEventCallback = (type, name, msg, dwUser) -> {
            ukeyEventHandler.handleUkeyEvent(type, name, msg);
            if (type == 1) {
                // 插入：禁止在回调线程内直接调用 SDK（会死锁），延迟 500ms 异步执行
                log.info("[UKey插拔] 检测到 UKey 插入: name={}", name);
                ukeyOpenExecutor.schedule(this::reInitUkey, 500, TimeUnit.MILLISECONDS);
            } else {
                // 拔出：仅清内存状态，无需调用 SDK
                log.warn("[UKey插拔] UKey 已拔出: name={}，认证状态清除", name);
                synchronized (CryptoServiceImpl.this) {
                    authenticated = false;
                    deviceHandle = -1;
                }
            }
            return 0;
        };
        boolean ok = sdk.VAuth_SetUkeyEventCallback(ukeyEventCallback, null);
        log.info("UKey 插拔回调注册: {}", ok ? "OK" : "FAIL(code=" + sdk.VAuth_GetLastError() + ")");
    }

    /**
     * 注册密钥/证书查询回调（解密侧必须注册）
     *
     * <p>VAuthSDK 解密时采用"按需提供"机制（参考 C++ Demo: VAuthDemo.cpp）：
     * 0. 认证完成后管控平台通过 KeyCallback 缓存了各客户端的会话密钥
     * 1. SDK 调用 DecryptData 时触发 QueryKeyCallback → 回调内从管控平台获取密钥 → 调用 VAuth_SetKey
     * 2. SDK 验签时触发 QueryCerCallback → 回调内从管控平台获取证书 → 调用 VAuth_SetCer
     */
    private void registerDecryptCallbacks() {
        // KeyCallback：接收本端认证时产生的会话密钥（保留兼容，实际解密时使用加密方的密钥）
        keyCallback = (id, ver, key, dwUser) -> {
            String cacheKey = id + "_" + ver;
            keyCache.put(cacheKey, key);
            log.info("[KeyCallback] 收到会话密钥: id={}, ver={}, 已存入缓存", id, ver);
            return 0;
        };
        boolean keyCallbackOk = sdk.VAuth_SetKeyCallback(keyCallback, null);
        log.info("KeyCallback 注册（接收会话密钥）: {}", keyCallbackOk ? "OK" : "FAIL(code=" + sdk.VAuth_GetLastError() + ")");

        // QueryKeyCallback：SDK 解密时请求会话密钥
        // 必须从管控平台获取加密方的密钥，然后调用 VAuth_SetKey 提供给 SDK
        queryKeyCallback = (handle, id, ver, dwUser) -> {
            log.info("[QueryKeyCallback] SDK 请求密钥: id={}, ver={}", id, ver);
            try {
                // 先检查本地缓存（加密后的密钥缓存）
                String localCacheKey = id + "_" + ver + "_encrypted";
                String cachedEncKey = keyCache.get(localCacheKey);
                if (cachedEncKey != null) {
                    boolean setOk = sdk.VAuth_SetKey(handle, id, ver, cachedEncKey);
                    log.info("[QueryKeyCallback] 从本地缓存设置密钥: id={}, ver={}, result={}", id, ver, setOk);
                    return setOk ? 0 : -1;
                }

                // 调用管控平台 /auth/key/query 获取加密后的会话密钥
                RestTemplate restTemplate = new RestTemplate();
                Map<String, String> body = new HashMap<>();
                body.put("srcAuthId", id);          // 加密方的 authId
                body.put("ver", ver);               // 密钥版本
                body.put("requestAuthId", vAuthConfig.getAuthId()); // 本端 authId
                // 【关键】直接携带本端证书，让平台用此证书加密密钥，避免平台从 DB 查到错误证书
                try {
                    String ownCert = loadCertificate(vAuthConfig.getClientCertPath());
                    body.put("requestCert", ownCert);
                    log.debug("[QueryKeyCallback] 已携带本端证书，长度={}", ownCert.length());
                } catch (Exception certLoadEx) {
                    log.warn("[QueryKeyCallback] 加载本端证书失败，由平台自行查找: {}", certLoadEx.getMessage());
                }
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String url = vAuthConfig.getControlPlatformUrl() + "/auth/server/key/query";
                log.debug("[QueryKeyCallback] 请求管控平台: {}", url);

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(
                        url, new HttpEntity<>(body, headers), Map.class);

                if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                    String encryptedKey = (String) resp.get("data");
                    // 缓存加密后的密钥，避免重复请求
                    keyCache.put(localCacheKey, encryptedKey);
                    log.info("[QueryKeyCallback] 收到加密密钥: id={}, ver={}, 长度={}", id, ver,
                            encryptedKey != null ? encryptedKey.length() : 0);
                    boolean setOk = sdk.VAuth_SetKey(handle, id, ver, encryptedKey);
                    log.info("[QueryKeyCallback] VAuth_SetKey: id={}, ver={}, result={}", id, ver, setOk);

                    return setOk ? 0 : -1;
                } else {
                    log.error("[QueryKeyCallback] 管控平台返回失败: {}", resp);
                    return -1;
                }
            } catch (Exception e) {
                log.error("[QueryKeyCallback] 获取密钥异常: id={}, ver={}", id, ver, e);
                return -1;
            }
        };

        // QueryCerCallback：SDK 请求签名证书（如解密密钥时需要本端证书）
        queryCerCallback = (id, type, dwUser) -> {
            log.info("[QueryCerCallback] SDK 请求证书: id={}, type={}", id, type);
            try {
                // 如果查询的是本端自身证书，直接从本地文件加载
                if (vAuthConfig.getAuthId() != null && vAuthConfig.getAuthId().equalsIgnoreCase(id)) {
                    log.info("[QueryCerCallback] 查询本端证书，直接从本地加载: id={}", id);
                    String ownCert = loadCertificate(vAuthConfig.getClientCertPath());
                    boolean setOk = sdk.VAuth_SetCer(id, type, ownCert);
                    log.info("[QueryCerCallback] 本端证书已注入: id={}, type={}, result={}", id, type, setOk);
                    return setOk ? 0 : -1;
                }

                // 其他 authId：先检查本地缓存，再调管控平台
                String localCacheKey = id + "_" + type;
                String cachedCert = cerCache.get(localCacheKey);
                if (cachedCert != null) {
                    boolean setOk = sdk.VAuth_SetCer(id, type, cachedCert);
                    log.info("[QueryCerCallback] 从本地缓存设置证书: id={}, type={}, result={}", id, type, setOk);
                    return setOk ? 0 : -1;
                }

                // 调用管控平台获取证书
                RestTemplate restTemplate = new RestTemplate();
                Map<String, String> body = new HashMap<>();
                body.put("authId", id);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String url = vAuthConfig.getControlPlatformUrl() + "/auth/server/cert/query";
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(
                        url, new HttpEntity<>(body, headers), Map.class);
                if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                    String cert = (String) resp.get("data");
                    cerCache.put(localCacheKey, cert);
                    boolean setOk = sdk.VAuth_SetCer(id, type, cert);
                    log.info("[QueryCerCallback] VAuth_SetCer: id={}, type={}, result={}", id, type, setOk);
                    return setOk ? 0 : -1;
                } else {
                    log.error("[QueryCerCallback] 管控平台返回失败: id={}, resp={}", id, resp);
                    return -1;
                }
            } catch (Exception e) {
                log.error("[QueryCerCallback] 获取证书异常: id={}, type={}", id, type, e);
                return -1;
            }
        };

        boolean keyOk = sdk.VAuth_SetQueryKeyCallback(queryKeyCallback, null);
        boolean cerOk = sdk.VAuth_SetQueryCerCallback(queryCerCallback, null);
        log.info("解密回调注册: QueryKeyCallback={}, QueryCerCallback={}", keyOk, cerOk);
    }

    // ===================== 工具方法 =====================

    /**
     * 扫描已插入的 UKey，取第一个打开，返回句柄（<0 表示失败）
     */
    private synchronized int openUkeyByList() {
        try {
            PointerByReference pList = new PointerByReference();
            if (!sdk.VAuth_ListUkeyInfos(pList) || pList.getValue() == null) {
                log.debug("VAuth_ListUkeyInfos 未找到 UKey 设备");
                return -1;
            }
            String json = pList.getValue().getString(0, "UTF-8");
            sdk.VAuth_Free(pList.getValue());
            log.debug("VAuth_ListUkeyInfos 返回: {}", json);

            UkeyDeviceInfo selectedDevice = selectUkeyDevice(json);
            String path = selectedDevice == null ? null : selectedDevice.path;
            if (path == null || path.isEmpty()) {
                log.warn("UKey 设备列表中未找到 path 字段");
                return -1;
            }

            int handle = sdk.VAuth_OpenUkey(path, vAuthConfig.getPassword(), vAuthConfig.getAuthId());
            if (handle < 0) {
                int err = sdk.VAuth_GetLastError();
                log.error("VAuth_OpenUkey 失败: path={}, errorCode={}, msg={}",
                        path, err, sdk.VAuth_GetErrorText(err));
            } else {
                log.info("VAuth_OpenUkey 成功: path={}, handle={}", path, handle);
            }
            return handle;
        } catch (Exception e) {
            log.error("openUkeyByList 异常", e);
            return -1;
        }
    }

    /**
     * 从 SDK 返回的错误码读取错误描述（兼容 GBK/UTF-8）
     * SDK 在 Linux 下返回的中文可能是 GBK 编码，JNA 默认 UTF-8 会乱码
     */
    private String getErrorText(int errCode) {
        try {
            // VAuth_GetErrorText 返回 String，JNA 内部已做编码处理
            // 同时尝试 GBK 解码作为备用
            String text = sdk.VAuth_GetErrorText(errCode);
            if (text == null) return "(null)";
            // 如果含乱码，尝试以 GBK 重新解码
            byte[] raw = text.getBytes(StandardCharsets.ISO_8859_1);
            String gbk = new String(raw, Charset.forName("GBK"));
            return text + " | GBK=" + gbk;
        } catch (Exception e) {
            return "errCode=" + errCode;
        }
    }

    /**
     * 从文件加载证书内容（PEM 格式）
     */
    private String loadCertificate(String certPath) throws IOException {
        File f = new File(certPath);
        if (!f.exists()) {
            throw new IOException("证书文件不存在: " + certPath);
        }
        String content = new String(Files.readAllBytes(Paths.get(certPath)), StandardCharsets.UTF_8);
        if (!content.contains("-----BEGIN CERTIFICATE-----")) {
            content = "-----BEGIN CERTIFICATE-----\n" + content.trim() + "\n-----END CERTIFICATE-----";
        }
        return content;
    }

    /**
     * 向管控平台发送认证 HTTP 请求
     *
     * @param endpoint    接口路径（/auth/server/request 或 /auth/server/verify）
     * @param requestData 认证数据
     * @param clientCert  本端签名证书（verify 接口使用，request 接口传 null）
     */
    private void validateSvacRuntimeFiles() {
        File baseDir = new File(System.getProperty("user.dir", "."));
        StringBuilder missing = new StringBuilder();
        for (String fileName : SVAC_RUNTIME_FILES) {
            if (!new File(baseDir, fileName).isFile()) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(fileName);
            }
        }
        if (missing.length() > 0) {
            throw new IllegalStateException("SVAC PackData runtime files missing under "
                    + baseDir.getAbsolutePath() + ": " + missing);
        }
    }

    private boolean ensurePackBridgeReady() {
        if (packBridge != null) {
            return true;
        }
        try {
            validateSvacRuntimeFiles();
            packBridge = VAuthPackBridgeLibrary.INSTANCE;
            log.info("SVAC PackData bridge loaded on demand: libvauthpackbridge");
            return true;
        } catch (Throwable e) {
            log.error("SVAC PackData bridge load failed", e);
            return false;
        }
    }

    private int packOutputBufferBytes(int inputBytes) {
        long capacity = Math.max((long) PACK_OUTPUT_BUFFER_BYTES,
                (long) inputBytes * 4L + PACK_OUTPUT_OVERHEAD_BYTES);
        return capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    private Map<String, Object> sendToControlPlatform(String endpoint, String requestData, String clientCert) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> body = new HashMap<>();
        body.put("authId", vAuthConfig.getAuthId());
        body.put("requestData", requestData);
        if (clientCert != null && !clientCert.isEmpty()) {
            body.put("clientCert", clientCert);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = vAuthConfig.getControlPlatformUrl() + endpoint;
        log.debug("发送请求到管控平台: {}", url);
        return restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
    }

    private boolean isSuccess(Map<String, Object> response) {
        return response != null && Integer.valueOf(200).equals(response.get("code"));
    }

    private int boolToNative(boolean value) {
        return value ? 1 : 0;
    }

    /**
     * 从 JSON 数组字符串中提取第一个对象的指定字段值
     * 简单字符串解析，避免引入额外 JSON 库依赖
     */
    private UkeyDeviceInfo selectUkeyDevice(String ukeyJson) {
        UkeyDeviceInfo[] devices = parseUkeyDevices(ukeyJson);
        if (devices.length == 0) {
            log.warn("UKey 列表为空");
            return null;
        }

        UkeyDeviceInfo selected = findUniqueByPath(devices);
        if (selected != null) {
            return selected;
        }

        if (hasExplicitUkeyBinding()) {
            selected = findUniqueByExplicitBinding(devices);
            if (selected != null) {
                return selected;
            }
            log.warn("未找到匹配的终端网关 UKey，绑定条件: sn={}, cerSn={}, cerId={}",
                    vAuthConfig.getUkeySn(), vAuthConfig.getUkeyCerSn(), vAuthConfig.getUkeyCerId());
            return null;
        }

        selected = findUniqueByAuthId(devices);
        if (selected != null) {
            return selected;
        }

        if (devices.length == 1) {
            log.warn("未配置 UKey 绑定条件，当前仅发现一个 UKey，兼容使用该设备");
            return devices[0];
        }

        log.warn("发现 {} 个 UKey，但未能按 authId={} 唯一匹配，拒绝默认选择第一个设备",
                devices.length, vAuthConfig.getAuthId());
        return null;
    }

    private UkeyDeviceInfo findUniqueByPath(UkeyDeviceInfo[] devices) {
        if (isBlank(vAuthConfig.getUkeyPath())) {
            return null;
        }
        UkeyDeviceInfo match = null;
        for (UkeyDeviceInfo device : devices) {
            if (equalsIgnoreCase(vAuthConfig.getUkeyPath(), device.path)) {
                if (match != null) {
                    log.warn("UKey path 匹配到多个设备: {}", vAuthConfig.getUkeyPath());
                    return null;
                }
                match = device;
            }
        }
        if (match == null) {
            log.warn("未找到 path={} 对应的 UKey", vAuthConfig.getUkeyPath());
        }
        return match;
    }

    private UkeyDeviceInfo findUniqueByExplicitBinding(UkeyDeviceInfo[] devices) {
        UkeyDeviceInfo match = null;
        for (UkeyDeviceInfo device : devices) {
            if (!matchesExplicitBinding(device)) {
                continue;
            }
            if (match != null) {
                log.warn("显式 UKey 绑定条件匹配到多个设备，请补充 sn/cerSn/cerId 约束");
                return null;
            }
            match = device;
        }
        return match;
    }

    private UkeyDeviceInfo findUniqueByAuthId(UkeyDeviceInfo[] devices) {
        UkeyDeviceInfo match = null;
        for (UkeyDeviceInfo device : devices) {
            if (!matchesAuthId(vAuthConfig.getAuthId(), device.cerId)) {
                continue;
            }
            if (match != null) {
                log.warn("authId={} 匹配到多个 UKey", vAuthConfig.getAuthId());
                return null;
            }
            match = device;
        }
        return match;
    }

    private boolean hasExplicitUkeyBinding() {
        return !isBlank(vAuthConfig.getUkeySn())
                || !isBlank(vAuthConfig.getUkeyCerSn())
                || !isBlank(vAuthConfig.getUkeyCerId());
    }

    private boolean matchesExplicitBinding(UkeyDeviceInfo device) {
        if (!isBlank(vAuthConfig.getUkeySn()) && !equalsIgnoreCase(vAuthConfig.getUkeySn(), device.sn)) {
            return false;
        }
        if (!isBlank(vAuthConfig.getUkeyCerSn()) && !equalsIgnoreCase(vAuthConfig.getUkeyCerSn(), device.cerSn)) {
            return false;
        }
        if (!isBlank(vAuthConfig.getUkeyCerId()) && !equalsIgnoreCase(vAuthConfig.getUkeyCerId(), device.cerId)) {
            return false;
        }
        return true;
    }

    private boolean matchesAuthId(String authId, String cerId) {
        if (isBlank(authId) || isBlank(cerId)) {
            return false;
        }
        return cerId.equalsIgnoreCase(authId) || cerId.toLowerCase().startsWith(authId.toLowerCase() + "_");
    }

    private UkeyDeviceInfo[] parseUkeyDevices(String json) {
        if (isBlank(json)) {
            return new UkeyDeviceInfo[0];
        }
        java.util.List<UkeyDeviceInfo> devices = new java.util.ArrayList<>();
        int index = 0;
        while (index < json.length()) {
            int start = json.indexOf('{', index);
            if (start < 0) {
                break;
            }
            int end = json.indexOf('}', start + 1);
            if (end < 0) {
                break;
            }
            String objectJson = json.substring(start, end + 1);
            UkeyDeviceInfo device = new UkeyDeviceInfo();
            device.name = extractJsonField(objectJson, "name");
            device.label = extractJsonField(objectJson, "label");
            device.sn = extractJsonField(objectJson, "sn");
            device.cerSn = extractJsonField(objectJson, "cerSn");
            device.cerId = extractJsonField(objectJson, "cerId");
            device.path = extractJsonField(objectJson, "path");
            if (!isBlank(device.path)) {
                devices.add(device);
            }
            index = end + 1;
        }
        return devices.toArray(new UkeyDeviceInfo[0]);
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private boolean equalsIgnoreCase(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class UkeyDeviceInfo {
        private String name;
        private String label;
        private String sn;
        private String cerSn;
        private String cerId;
        private String path;
    }
}
