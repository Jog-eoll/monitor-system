package com.publishgateway.udpproxy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.config.VAuthConfig;
import com.publishgateway.udpproxy.jna.VAuthPackBridgeLibrary;
import com.publishgateway.udpproxy.jna.VAuthSDKLibrary;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.SignedEnvelopeCryptoService;
import com.publishgateway.udpproxy.service.SvacModulePresenceGuard;
import com.publishgateway.udpproxy.service.SvacFileCryptoService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 VAuthSDK 的真实国密加密服务（发布加密网关）
 *
 * <p>启用条件：vauth.mock-mode=false
 * <p>加密算法：SM4对称加密 + SM2签名（国密）
 * <p>前置要求：必须在双向认证成功后方可调用 encrypt/decrypt，否则SDK缺少会话密钥返回失败
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "vauth.mock-mode", havingValue = "false")
public class VAuthCryptoServiceImpl implements CryptoService, SignedEnvelopeCryptoService, SvacFileCryptoService {

    private static final long AUTH_RETRY_INTERVAL_MS = 10_000L;
    private static final int PAYLOAD_PREVIEW_BYTES = 32;
    private static final int PACK_OUTPUT_BUFFER_BYTES = 256 * 1024;
    private static final int PACK_OUTPUT_OVERHEAD_BYTES = 1024 * 1024;
    private static final int PACK_BRIDGE_OK = 1;
    private static final int[] SVAC_MATRIX_TEST_LENGTHS = {32, 40, 44, 48, 52, 64};
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

    @Resource
    private SvacModulePresenceGuard svacModulePresenceGuard;

    /** SDK 实例（JNA 直接调用） */
    private VAuthSDKLibrary sdk;
    private VAuthPackBridgeLibrary packBridge;

    /** 设备句柄，由 openDevice() 赋值 */
    private int deviceHandle = -1;

    /** 认证状态 */
    private volatile boolean authenticated = false;

    private final Object authLock = new Object();

    private volatile long lastAuthRetryTimeMs = 0L;

    // JNA 回调对象必须保持强引用，防止被 GC 回收
    private VAuthSDKLibrary.QueryKeyCallback queryKeyCallback;
    private VAuthSDKLibrary.QueryCerCallback queryCerCallback;

    /** 加密密钥缓存：key = "authId_ver_encrypted", value = 加密后密钥(BASE64) */
    private final Map<String, String> keyCache = new ConcurrentHashMap<>();

    /** 证书缓存：key = "authId_type", value = PEM证书 */
    private final Map<String, String> cerCache = new ConcurrentHashMap<>();

    // ===================== 生命周期 =====================

    @PostConstruct
    public synchronized void init() {
        log.info("=== VAuthCryptoServiceImpl 初始化（国密模式）===");
        log.info("=== 加密模式: {} ===",
                Boolean.TRUE.equals(vAuthConfig.getSvacMode()) ? "SVAC2编码加密" : "国密整包加密");
        sdk = VAuthSDKLibrary.INSTANCE;
        if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
            validateSvacRuntimeFiles();
            packBridge = VAuthPackBridgeLibrary.INSTANCE;
            log.info("SVAC PackData bridge loaded: libvauthpackbridge");
        }
        boolean success = sdk.VAuth_Init();
        if (!success) {
            int err = sdk.VAuth_GetLastError();
            throw new RuntimeException("VAuthSDK 初始化失败: errorCode=" + err
                    + ", msg=" + getErrorText(err));
        }
        log.info("VAuthSDK 初始化成功");

        // 打开设备
        deviceHandle = openDevice();
        if (deviceHandle < 0) {
            throw new RuntimeException("VAuthSDK 设备打开失败: handle=" + deviceHandle);
        }
        log.info("VAuthSDK 设备打开成功: handle={}", deviceHandle);

        authenticateIfNecessary(true);
        runSvacMatrixTestIfEnabled();
    }

    @PreDestroy
    public synchronized void destroy() {
        if (deviceHandle >= 0) {
            sdk.VAuth_CloseHandle(deviceHandle);
            log.info("VAuthSDK 设备句柄已释放: handle={}", deviceHandle);
        }
        sdk.VAuth_Cleanup();
        log.info("VAuthSDK 已清理");
    }

    // ===================== CryptoService 接口实现 =====================

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
        authenticateIfNecessary(false);
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
        authenticateIfNecessary(false);
        if (!authenticated || deviceHandle < 0) {
            log.error("SVAC pack encrypt refused: authenticated={}, handle={}, dataLen={}",
                    authenticated, deviceHandle, data.length);
            return null;
        }
        if (!ensurePackBridgeReady()) {
            return null;
        }
        return encryptPackDataDirectNoFree(data, Boolean.TRUE.equals(vAuthConfig.getSignEnabled()),
                "VAuth_EncryptPackData");
    }

    @Override
    public synchronized byte[] decryptPackData(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("decryptPackData")) {
            return null;
        }
        authenticateIfNecessary(false);
        if (!authenticated || deviceHandle < 0) {
            log.error("SVAC pack decrypt refused: authenticated={}, handle={}, dataLen={}",
                    authenticated, deviceHandle, data.length);
            return null;
        }
        if (!ensurePackBridgeReady()) {
            return null;
        }
        return decryptPackDataDirectNoFree(data, Boolean.TRUE.equals(vAuthConfig.getSignEnabled()),
                "VAuth_DecryptPackData");
    }

    /**
     * 加密数据（SM4 + 可选SM2签名）
     *
     * <p>安全策略：认证未就绪或加密失败时返回 null（拒绝转发），
     * 绝不返回原始明文数据，防止明文在加密通道上流转。
     *
     * @param data 原始数据
     * @return 加密后数据；认证未就绪或加密失败时返回 null（调用方须丢弃该数据包）
     */
    @Override
    public synchronized byte[] encrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("encrypt")) {
            return null;
        }
        authenticateIfNecessary(false);
        if (!authenticated || deviceHandle < 0) {
            log.error("encrypt refused: authenticated={}, handle={}, dataLen={}, thread={}, headHex={}, packetDropped=true",
                    authenticated, deviceHandle, data.length, Thread.currentThread().getName(), payloadHeadForLog(data));
            return null;
        }
        String apiName = Boolean.TRUE.equals(vAuthConfig.getSvacMode())
                ? "VAuth_EncryptPackData" : "VAuth_EncryptData";
        boolean isSign = Boolean.TRUE.equals(vAuthConfig.getSignEnabled());
        try {
            if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
                logVAuthInput("encrypt", apiName, "sign", isSign, data);
                return encryptPackDataDirectNoFree(data, isSign, apiName);
            }

            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();
            boolean success;
            logVAuthInput("encrypt", apiName, "sign", isSign, data);

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

            Pointer outPtr = pOutData.getValue();
            if (!success || outPtr == null) {
                int err = sdk.VAuth_GetLastError();
                log.error("encrypt failed: api={}, handle={}, sign={}, dataLen={}, outLen={}, thread={}, headHex={}, errorCode={}, msg={}, packetDropped=true",
                        apiName, deviceHandle, isSign, data.length, outLen.getValue(),
                        Thread.currentThread().getName(), payloadHeadForLog(data), err, getErrorText(err));
                return null;
            }

            int length = outLen.getValue();
            if (length <= 0) {
                safeFree(outPtr);
                log.error("encrypt failed: api={}, handle={}, sign={}, dataLen={}, outLen={}, thread={}, headHex={}, reason=invalidOutputLength, packetDropped=true",
                        apiName, deviceHandle, isSign, data.length, length,
                        Thread.currentThread().getName(), payloadHeadForLog(data));
                return null;
            }
            byte[] encrypted = outPtr.getByteArray(0, length);
            safeFree(outPtr);
            log.debug("encrypt success: api={}, inputBytes={}, outputBytes={}, thread={}",
                    apiName, data.length, length, Thread.currentThread().getName());
            return encrypted;

        } catch (Exception e) {
            log.error("encrypt exception: api={}, handle={}, sign={}, dataLen={}, thread={}, headHex={}, packetDropped=true",
                    apiName, deviceHandle, isSign, data.length, Thread.currentThread().getName(), payloadHeadForLog(data), e);
            return null;
        }
    }

    /**
     * 解密数据（用于发布网关解密来自终端网关的加密响应）
     *
     * <p>安全策略：认证未就绪或解密失败时返回 null（拒绝转发），
     * 绝不返回未解密的密文数据，防止无效数据回传给 Sigma。
     *
     * @param data 加密数据
     * @return 解密后数据；认证未就绪或解密失败时返回 null（调用方须丢弃该响应）
     */
    @Override
    public synchronized byte[] decrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("decrypt")) {
            return null;
        }
        authenticateIfNecessary(false);
        if (!authenticated || deviceHandle < 0) {
            log.error("decrypt refused: authenticated={}, handle={}, dataLen={}, thread={}, headHex={}, responseDropped=true",
                    authenticated, deviceHandle, data.length, Thread.currentThread().getName(), payloadHeadForLog(data));
            return null;
        }
        String apiName = Boolean.TRUE.equals(vAuthConfig.getSvacMode())
                ? "VAuth_DecryptPackData" : "VAuth_DecryptData";
        boolean isVerify = Boolean.TRUE.equals(vAuthConfig.getSvacMode())
                && Boolean.TRUE.equals(vAuthConfig.getSignEnabled());
        try {
            if (Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
                logVAuthInput("decrypt", apiName, "verify", isVerify, data);
                return decryptPackDataDirectNoFree(data, isVerify, apiName);
            }

            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();

            // 非 SVAC 整包模式保持 false；SVAC PackData 按签名配置走验签封装解析。
            boolean success;
            logVAuthInput("decrypt", apiName, "verify", isVerify, data);

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

            Pointer outPtr = pOutData.getValue();
            if (!success || outPtr == null) {
                int err = sdk.VAuth_GetLastError();
                log.error("decrypt failed: api={}, handle={}, verify={}, dataLen={}, outLen={}, thread={}, headHex={}, errorCode={}, msg={}, responseDropped=true",
                        apiName, deviceHandle, isVerify, data.length, outLen.getValue(),
                        Thread.currentThread().getName(), payloadHeadForLog(data), err, getErrorText(err));
                return null;
            }

            int length = outLen.getValue();
            if (length <= 0) {
                safeFree(outPtr);
                log.error("decrypt failed: api={}, handle={}, verify={}, dataLen={}, outLen={}, thread={}, headHex={}, reason=invalidOutputLength, responseDropped=true",
                        apiName, deviceHandle, isVerify, data.length, length,
                        Thread.currentThread().getName(), payloadHeadForLog(data));
                return null;
            }
            byte[] decrypted = outPtr.getByteArray(0, length);
            safeFree(outPtr);
            log.debug("decrypt success: api={}, inputBytes={}, outputBytes={}, thread={}",
                    apiName, data.length, length, Thread.currentThread().getName());
            return decrypted;

        } catch (Exception e) {
            log.error("decrypt exception: api={}, handle={}, verify={}, dataLen={}, thread={}, headHex={}, responseDropped=true",
                    apiName, deviceHandle, isVerify, data.length, Thread.currentThread().getName(), payloadHeadForLog(data), e);
            return null;
        }
    }

    // ===================== 私有方法 =====================

    private byte[] encryptPackDataDirectNoFree(byte[] data, boolean isSign, String apiName) {
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
        return handlePackDataBridgeResult("encrypt", apiName, "sign", isSign,
                data, outBuffer, outLen.getValue(), result, true);
    }

    private byte[] doFileDataCrypto(String operation, byte[] data, boolean flag) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady("file-" + operation)) {
            return null;
        }
        authenticateIfNecessary(false);
        if (!authenticated || deviceHandle < 0) {
            log.error("SVAC file {} refused: authenticated={}, handle={}, dataLen={}, headHex={}",
                    operation, authenticated, deviceHandle, data.length, payloadHeadForLog(data));
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
                log.error("SVAC file {} failed: api={}, handle={}, flag={}, dataLen={}, outLen={}, headHex={}, errorCode={}, msg={}",
                        operation, apiName, deviceHandle, flag, data.length, outLen.getValue(),
                        payloadHeadForLog(data), err, getErrorText(err));
                return null;
            }
            int length = outLen.getValue();
            if (length <= 0) {
                log.error("SVAC file {} failed: api={}, handle={}, flag={}, dataLen={}, outLen={}, reason=invalidOutputLength",
                        operation, apiName, deviceHandle, flag, data.length, length);
                return null;
            }
            byte[] output = outPtr.getByteArray(0, length);
            log.info("SVAC file {} success: api={}, inputBytes={}, outputBytes={}, flag={}, outputHeadHex={}",
                    operation, apiName, data.length, length, flag, payloadHeadForLog(output));
            return output;
        } catch (Exception e) {
            log.error("SVAC file {} exception: api={}, handle={}, dataLen={}",
                    operation, apiName, deviceHandle, data.length, e);
            return null;
        } finally {
            safeFree(outPtr);
        }
    }

    private byte[] decryptPackDataDirectNoFree(byte[] data, boolean isVerify, String apiName) {
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
        return handlePackDataBridgeResult("decrypt", apiName, "verify", isVerify,
                data, outBuffer, outLen.getValue(), result, false);
    }

    private byte[] handlePackDataBridgeResult(String operation, String apiName, String flagName, boolean flagValue,
                                              byte[] input, byte[] outBuffer, int length, int result,
                                              boolean packetDropped) {
        if (result != PACK_BRIDGE_OK) {
            int err = sdk.VAuth_GetLastError();
            log.error("{} failed: api={}, bridgeResult={}, handle={}, {}={}, dataLen={}, outLen={}, outCapacity={}, thread={}, headHex={}, errorCode={}, msg={}, {}=true",
                    operation, apiName, result, deviceHandle, flagName, flagValue, input.length, length,
                    outBuffer.length, Thread.currentThread().getName(), payloadHeadForLog(input), err, getErrorText(err),
                    packetDropped ? "packetDropped" : "responseDropped");
            return null;
        }
        if (length <= 0 || length > outBuffer.length) {
            log.error("{} failed: api={}, bridgeResult={}, handle={}, {}={}, dataLen={}, outLen={}, outCapacity={}, thread={}, headHex={}, reason=invalidOutputLength, {}=true",
                    operation, apiName, result, deviceHandle, flagName, flagValue, input.length, length,
                    outBuffer.length, Thread.currentThread().getName(), payloadHeadForLog(input),
                    packetDropped ? "packetDropped" : "responseDropped");
            return null;
        }
        byte[] output = new byte[length];
        System.arraycopy(outBuffer, 0, output, 0, length);
        log.info("{} success: api={}, inputBytes={}, outputBytes={}, {}={}, thread={}, bridge=true, outputHeadHex={}",
                operation, apiName, input.length, length, flagName, flagValue,
                Thread.currentThread().getName(), payloadHeadForLog(output));
        return output;
    }

    private synchronized boolean authenticateIfNecessary(boolean force) {
        if (authenticated && deviceHandle >= 0) {
            return true;
        }
        if (deviceHandle < 0) {
            log.warn("VAuthSDK 设备句柄无效，无法发起认证: handle={}", deviceHandle);
            return false;
        }

        long now = System.currentTimeMillis();
        if (!force && now - lastAuthRetryTimeMs < AUTH_RETRY_INTERVAL_MS) {
            return false;
        }

        synchronized (authLock) {
            if (authenticated && deviceHandle >= 0) {
                return true;
            }
            now = System.currentTimeMillis();
            if (!force && now - lastAuthRetryTimeMs < AUTH_RETRY_INTERVAL_MS) {
                return false;
            }
            lastAuthRetryTimeMs = now;

            try {
                log.info("开始执行 VAuthSDK 双向认证，force={}, handle={}", force, deviceHandle);
                boolean authOk = executeAuthHandshake();
                if (authOk) {
                    authenticated = true;
                    log.info("=== VAuthSDK 双向认证成功，国密加密就绪 ===");
                    registerDecryptCallbacks();
                    return true;
                }
                authenticated = false;
                log.warn("VAuthSDK 双向认证失败，加密功能不可用");
                return false;
            } catch (Exception e) {
                authenticated = false;
                log.warn("VAuthSDK 认证异常（加密功能不可用）: {}", e.getMessage());
                return false;
            }
        }
    }

    /**
     * 注册 QueryKeyCallback 和 QueryCerCallback，用于解密终端响应。
     * 当终端加密响应到达时，SDK 通过 QueryKeyCallback 请求终端的会话密钥，
     * 发布网关向管控平台查询后通过 VAuth_SetKey 提供给 SDK。
     */
    @Override
    public synchronized byte[] signEnvelope(byte[] data) {
        return processEnvelope(data, true);
    }

    @Override
    public synchronized byte[] verifyEnvelope(byte[] signedEnvelope) {
        return processEnvelope(signedEnvelope, false);
    }

    private synchronized byte[] processEnvelope(byte[] data, boolean sign) {
        if (data == null || data.length == 0) {
            return data;
        }
        if (!svacModulePresenceGuard.ensureReady(sign ? "signEnvelope" : "verifyEnvelope")) {
            return null;
        }
        if (!authenticated || deviceHandle < 0) {
            log.error("{} envelope refused: authenticated={}, handle={}",
                    sign ? "sign" : "verify", authenticated, deviceHandle);
            return null;
        }
        try {
            PointerByReference pOutData = new PointerByReference();
            IntByReference outLen = new IntByReference();
            boolean success;
            if (sign) {
                success = sdk.VAuth_EncryptData(deviceHandle, true, data, data.length, pOutData, outLen);
            } else {
                success = sdk.VAuth_DecryptData(deviceHandle, true, data, data.length, pOutData, outLen);
            }
            if (!success || pOutData.getValue() == null) {
                int err = sdk.VAuth_GetLastError();
                log.error("{} envelope failed: errorCode={}, msg={}",
                        sign ? "sign" : "verify", err, getErrorText(err));
                return null;
            }
            Pointer outPtr = pOutData.getValue();
            int length = outLen.getValue();
            if (length <= 0) {
                safeFree(outPtr);
                log.error("{} envelope failed: invalidOutputLength={}", sign ? "sign" : "verify", length);
                return null;
            }
            byte[] out = outPtr.getByteArray(0, length);
            safeFree(outPtr);
            log.debug("{} envelope success: inputBytes={}, outputBytes={}",
                    sign ? "sign" : "verify", data.length, length);
            return out;
        } catch (Exception e) {
            log.error("{} envelope exception", sign ? "sign" : "verify", e);
            return null;
        }
    }

    private synchronized void registerDecryptCallbacks() {
        // QueryKeyCallback：SDK 解密时请求终端会话密钥
        queryKeyCallback = (handle, id, ver, dwUser) -> {
            log.info("[QueryKeyCallback] SDK 请求密钥: id={}, ver={}", id, ver);
            try {
                // 先检查本地缓存
                String localCacheKey = id + "_" + ver + "_encrypted";
                String cachedEncKey = keyCache.get(localCacheKey);
                if (cachedEncKey != null) {
                    boolean setOk = sdk.VAuth_SetKey(handle, id, ver, cachedEncKey);
                    log.info("[QueryKeyCallback] 从本地缓存设置密钥: id={}, ver={}, result={}", id, ver, setOk);
                    return setOk ? 0 : -1;
                }

                // 向管控平台请求终端会话密钥（用本端证书加密后返回）
                RestTemplate restTemplate = new RestTemplate();
                Map<String, String> body = new HashMap<>();
                body.put("srcAuthId", id);                          // 终端 authId
                body.put("ver", ver);                               // 密钥版本
                body.put("requestAuthId", vAuthConfig.getAuthId()); // 发布网关 authId
                try {
                    String ownCert = loadCertificate(vAuthConfig.getClientCertPath());
                    body.put("requestCert", ownCert);
                } catch (Exception certEx) {
                    log.warn("[QueryKeyCallback] 加载本端证书失败，由平台查找: {}", certEx.getMessage());
                }
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String url = vAuthConfig.getControlPlatformUrl() + "/auth/server/key/query";

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(
                        url, new HttpEntity<>(body, headers), Map.class);

                if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                    String encryptedKey = (String) resp.get("data");
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

        // QueryCerCallback：SDK 请求证书时提供（通常是本端证书，用于密钥解密）
        queryCerCallback = (id, type, dwUser) -> {
            log.info("[QueryCerCallback] SDK 请求证书: id={}, type={}", id, type);
            try {
                // 如果是本端证书，直接从文件加载
                if (vAuthConfig.getAuthId() != null && vAuthConfig.getAuthId().equalsIgnoreCase(id)) {
                    String ownCert = loadCertificate(vAuthConfig.getClientCertPath());
                    boolean setOk = sdk.VAuth_SetCer(id, type, ownCert);
                    log.info("[QueryCerCallback] 本端证书已注入: id={}, type={}, result={}", id, type, setOk);
                    return setOk ? 0 : -1;
                }

                // 其他证书：检查缓存，再向管控平台查询
                String localCacheKey = id + "_" + type;
                String cachedCert = cerCache.get(localCacheKey);
                if (cachedCert != null) {
                    boolean setOk = sdk.VAuth_SetCer(id, type, cachedCert);
                    log.info("[QueryCerCallback] 从本地缓存设置证书: id={}, type={}, result={}", id, type, setOk);
                    return setOk ? 0 : -1;
                }

                RestTemplate restTemplate = new RestTemplate();
                Map<String, String> certBody = new HashMap<>();
                certBody.put("authId", id);
                HttpHeaders certHeaders = new HttpHeaders();
                certHeaders.setContentType(MediaType.APPLICATION_JSON);
                String certUrl = vAuthConfig.getControlPlatformUrl() + "/auth/server/cert/query";
                @SuppressWarnings("unchecked")
                Map<String, Object> certResp = restTemplate.postForObject(
                        certUrl, new HttpEntity<>(certBody, certHeaders), Map.class);
                if (certResp != null && Integer.valueOf(200).equals(certResp.get("code"))) {
                    String cert = (String) certResp.get("data");
                    cerCache.put(localCacheKey, cert);
                    boolean setOk = sdk.VAuth_SetCer(id, type, cert);
                    log.info("[QueryCerCallback] VAuth_SetCer: id={}, type={}, result={}", id, type, setOk);
                    return setOk ? 0 : -1;
                } else {
                    log.error("[QueryCerCallback] 管控平台返回失败: id={}, resp={}", id, certResp);
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

    /**
     * 根据配置打开SDF或UKey设备
     */
    private synchronized int openDevice() {
        String type = vAuthConfig.getDeviceType();
        if ("ukey".equalsIgnoreCase(type)) {
            String ukeyPath = resolveUkeyPath();
            if (isBlank(ukeyPath)) {
                log.error("未找到发布网关绑定的 UKey，拒绝打开默认设备");
                return -1;
            }
            log.info("打开发布网关 UKey 设备: path={}", ukeyPath);
            return sdk.VAuth_OpenUkey(
                    ukeyPath,
                    vAuthConfig.getPassword(),
                    vAuthConfig.getAuthId()
            );
        } else {
            log.info("打开 SDF 密码卡");
            return sdk.VAuth_OpenSDF(
                    vAuthConfig.getPassword(),
                    vAuthConfig.getAuthId()
            );
        }
    }

    /**
     * 执行三步双向认证握手
     * Step1: BuildAuthReq -> 发送到管控平台
     * Step2: BuildAuthInfo -> 发送到管控平台
     * Step3: CheckAuthResult
     */
    private synchronized boolean executeAuthHandshake() throws Exception {
        // Step1: 设置服务器证书
        String serverCert = loadCertificate(vAuthConfig.getServerCertPath());
        boolean setOk = sdk.VAuth_SetAuthServerInfo(
                deviceHandle, vAuthConfig.getServerId(), serverCert
        );
        if (!setOk) {
            log.error("VAuth_SetAuthServerInfo 失败");
            return false;
        }

        // Step2: 生成认证请求
        PointerByReference pReq = new PointerByReference();
        if (!sdk.VAuth_BuildAuthReq(deviceHandle, pReq) || pReq.getValue() == null) {
            log.error("VAuth_BuildAuthReq 失败");
            return false;
        }
        String authReq = pReq.getValue().getString(0, "UTF-8");
        sdk.VAuth_Free(pReq.getValue());
        log.debug("认证请求已生成，长度={}", authReq.length());

        // Step3: 发送认证请求到管控平台
        Map<String, Object> resp1 = sendToControlPlatform("/auth/server/request", authReq);
        if (!isSuccess(resp1)) {
            log.error("管控平台拒绝认证请求: {}", resp1);
            return false;
        }

        // Step4: 生成认证信息
        PointerByReference pInfo = new PointerByReference();
        String resp1Data = (String) resp1.get("data");
        log.debug("管控平台返回的认证数据(resp1Data): length={}, content={}", resp1Data != null ? resp1Data.length() : 0, resp1Data);
        if (!sdk.VAuth_BuildAuthInfo(deviceHandle, resp1Data, pInfo) || pInfo.getValue() == null) {
            int errCode = sdk.VAuth_GetLastError();
            String errMsg = getErrorText(errCode);
            log.error("VAuth_BuildAuthInfo 失败, errCode={}, errMsg={}", errCode, errMsg);
            return false;
        }
        String authInfo = pInfo.getValue().getString(0, "UTF-8");
        sdk.VAuth_Free(pInfo.getValue());

        // Step5: 发送认证信息到管控平台（同时附带本端UKey签名证书，管控平台 ParseAuthInfo 验签用）
        String clientCert = "";
        try {
            clientCert = loadCertificate(vAuthConfig.getClientCertPath());
            log.debug("客户端证书已加载，长度={}", clientCert.length());
        } catch (Exception e) {
            log.warn("加载客户端证书失败（管控平台验签可能失败）: {}", e.getMessage());
        }
        Map<String, Object> resp2 = sendToControlPlatformWithCert("/auth/server/verify", authInfo, clientCert);
        if (!isSuccess(resp2)) {
            log.error("管控平台拒绝认证信息: {}", resp2);
            return false;
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
                return false;
            }
        }

        log.info("双向认证握手完成");
        return authOk;
    }

    /**
     * 根据配置解析当前发布网关绑定的 UKey path。
     *
     * <p>生产环境建议配置 ukey-sn，并叠加 ukey-cer-sn/ukey-cer-id 做启动校验。
     * 未配置绑定条件时仅兼容单 UKey 或 authId 唯一匹配场景。
     */
    private synchronized String resolveUkeyPath() {
        try {
            PointerByReference pInfos = new PointerByReference();
            if (!sdk.VAuth_ListUkeyInfos(pInfos) || pInfos.getValue() == null) {
                log.warn("VAuth_ListUkeyInfos 未返回数据");
                return "";
            }
            String json = pInfos.getValue().getString(0, "UTF-8");
            sdk.VAuth_Free(pInfos.getValue());
            log.info("已插入的 UKey 设备列表: {}", json);

            UkeyDeviceInfo selectedDevice = selectUkeyDevice(json);
            if (selectedDevice == null || isBlank(selectedDevice.path)) {
                return "";
            }
            log.info("发布网关绑定 UKey: name={}, label={}, sn={}, cerSn={}, cerId={}, path={}",
                    selectedDevice.name, selectedDevice.label, selectedDevice.sn,
                    selectedDevice.cerSn, selectedDevice.cerId, selectedDevice.path);
            return selectedDevice.path;
        } catch (Exception e) {
            log.warn("解析发布网关 UKey path 失败: {}", e.getMessage());
            return "";
        }
    }

    private UkeyDeviceInfo selectUkeyDevice(String ukeyJson) {
        if (isBlank(ukeyJson)) {
            return null;
        }
        List<UkeyDeviceInfo> devices = parseUkeyDevices(ukeyJson);
        if (devices.isEmpty()) {
            log.warn("UKey 列表为空");
            return null;
        }

        if (!isBlank(vAuthConfig.getUkeyPath())) {
            List<UkeyDeviceInfo> matches = new ArrayList<>();
            for (UkeyDeviceInfo device : devices) {
                if (equalsIgnoreCase(vAuthConfig.getUkeyPath(), device.path)) {
                    matches.add(device);
                }
            }
            return uniqueOrWarn(matches, "path=" + vAuthConfig.getUkeyPath());
        }

        boolean hasExplicitBinding = !isBlank(vAuthConfig.getUkeySn())
                || !isBlank(vAuthConfig.getUkeyCerSn())
                || !isBlank(vAuthConfig.getUkeyCerId());
        if (hasExplicitBinding) {
            List<UkeyDeviceInfo> matches = new ArrayList<>();
            for (UkeyDeviceInfo device : devices) {
                if (matchesExplicitBinding(device)) {
                    matches.add(device);
                }
            }
            return uniqueOrWarn(matches, "sn=" + vAuthConfig.getUkeySn()
                    + ", cerSn=" + vAuthConfig.getUkeyCerSn()
                    + ", cerId=" + vAuthConfig.getUkeyCerId());
        }

        List<UkeyDeviceInfo> authIdMatches = new ArrayList<>();
        for (UkeyDeviceInfo device : devices) {
            if (matchesAuthId(vAuthConfig.getAuthId(), device.cerId)) {
                authIdMatches.add(device);
            }
        }
        if (!authIdMatches.isEmpty()) {
            return uniqueOrWarn(authIdMatches, "authId=" + vAuthConfig.getAuthId());
        }

        if (devices.size() == 1) {
            UkeyDeviceInfo device = devices.get(0);
            log.warn("未配置 UKey 绑定条件，当前仅发现一个 UKey，兼容使用该设备: sn={}, cerSn={}, cerId={}",
                    device.sn, device.cerSn, device.cerId);
            return device;
        }

        log.warn("已发现 {} 个 UKey，但未配置明确绑定条件，拒绝默认选择第一个设备", devices.size());
        return null;
    }

    private List<UkeyDeviceInfo> parseUkeyDevices(String ukeyJson) {
        List<UkeyDeviceInfo> devices = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(ukeyJson);
            for (int i = 0; i < array.size(); i++) {
                JSONObject object = array.getJSONObject(i);
                if (object == null) {
                    continue;
                }
                UkeyDeviceInfo device = new UkeyDeviceInfo();
                device.name = object.getString("name");
                device.label = object.getString("label");
                device.sn = object.getString("sn");
                device.cerSn = object.getString("cerSn");
                device.cerId = object.getString("cerId");
                device.path = object.getString("path");
                devices.add(device);
            }
        } catch (Exception e) {
            log.warn("解析 UKey 列表失败: {}", e.getMessage());
        }
        return devices;
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

    private UkeyDeviceInfo uniqueOrWarn(List<UkeyDeviceInfo> matches, String condition) {
        if (matches.isEmpty()) {
            log.warn("未找到匹配的发布网关 UKey，匹配条件: {}", condition);
            return null;
        }
        if (matches.size() > 1) {
            log.warn("匹配到多个发布网关 UKey，匹配条件: {}，请增加 sn/cerSn/cerId 约束", condition);
            return null;
        }
        return matches.get(0);
    }

    private boolean matchesAuthId(String authId, String cerId) {
        if (isBlank(authId) || isBlank(cerId)) {
            return false;
        }
        return cerId.equalsIgnoreCase(authId) || cerId.toLowerCase().startsWith(authId.toLowerCase() + "_");
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

    /**
     * 从 SDK 返回的 Pointer 中读取错误描述（兼容 GBK/UTF-8）
     */
    private synchronized String getErrorText(int errCode) {
        Pointer ptr = sdk.VAuth_GetErrorText(errCode);
        if (ptr == null) return "(null)";
        // 先读取原始字节（最多256字节）
        byte[] raw = ptr.getByteArray(0, 256);
        int len = 0;
        for (byte b : raw) { if (b == 0) break; len++; }
        if (len == 0) return "(empty)";
        byte[] actual = new byte[len];
        System.arraycopy(raw, 0, actual, 0, len);
        // 尝试 UTF-8
        String utf8 = new String(actual, StandardCharsets.UTF_8);
        // 尝试 GBK
        try {
            String gbk = new String(actual, Charset.forName("GBK"));
            return utf8 + " | GBK=" + gbk;
        } catch (Exception e) {
            return utf8;
        }
    }

    /**
     * 从文件加载证书内容
     */
    private void runSvacMatrixTestIfEnabled() {
        if (!Boolean.TRUE.equals(vAuthConfig.getSvacMatrixTestEnabled())) {
            return;
        }
        if (!Boolean.TRUE.equals(vAuthConfig.getSvacMode())) {
            log.warn("SVAC matrix test skipped: svacMode=false");
            return;
        }
        if (!authenticated || deviceHandle < 0) {
            log.warn("SVAC matrix test skipped: authenticated={}, handle={}", authenticated, deviceHandle);
            return;
        }

        log.warn("SVAC matrix test enabled, this should only be used in isolated diagnostics");
        for (int length : SVAC_MATRIX_TEST_LENGTHS) {
            byte[] sample = new byte[length];
            for (int i = 0; i < sample.length; i++) {
                sample[i] = (byte) (i & 0xFF);
            }
            byte[] encrypted = encrypt(sample);
            log.info("SVAC matrix test result: inputBytes={}, encryptedBytes={}, success={}",
                    length, encrypted != null ? encrypted.length : 0, encrypted != null);
        }
    }

    private void logVAuthInput(String operation, String apiName, String flagName, boolean flagValue, byte[] data) {
        if (!log.isDebugEnabled() && !Boolean.TRUE.equals(vAuthConfig.getPayloadDebugLogEnabled())) {
            return;
        }
        log.debug("{} input: api={}, handle={}, {}={}, dataLen={}, thread={}, headHex={}",
                operation, apiName, deviceHandle, flagName, flagValue,
                data != null ? data.length : 0, Thread.currentThread().getName(), payloadHeadForLog(data));
    }

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

    private String payloadHeadForLog(byte[] data) {
        if (!Boolean.TRUE.equals(vAuthConfig.getPayloadDebugLogEnabled())) {
            return "disabled";
        }
        if (data == null) {
            return "null";
        }
        int length = Math.min(data.length, PAYLOAD_PREVIEW_BYTES);
        StringBuilder builder = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            builder.append(String.format("%02x", data[i] & 0xFF));
        }
        if (data.length > length) {
            builder.append("...");
        }
        return builder.toString();
    }

    private void safeFree(Pointer pointer) {
        if (pointer == null) {
            return;
        }
        try {
            sdk.VAuth_Free(pointer);
        } catch (Exception e) {
            log.warn("VAuth_Free failed: {}", e.getMessage());
        }
    }

    private int boolToNative(boolean value) {
        return value ? 1 : 0;
    }

    private String loadCertificate(String certPath) throws IOException {
        File f = new File(certPath);
        if (!f.exists()) {
            throw new IOException("证书文件不存在: " + certPath);
        }
        byte[] bytes = Files.readAllBytes(Paths.get(certPath));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 向管控平台发送HTTP请求
     */
    private Map<String, Object> sendToControlPlatform(String endpoint, String requestData) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> body = new HashMap<>();
        body.put("authId", vAuthConfig.getAuthId());
        body.put("requestData", requestData);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = vAuthConfig.getControlPlatformUrl() + endpoint;
        log.debug("发送请求到管控平台: {}", url);
        return restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * 向管控平台发送HTTP请求（携带客户端证书版本，用于 /auth/server/verify）
     * clientCert：本端UKey签名证书内容，管控平台 VAuth_ParseAuthInfo 验签时需要
     */
    private Map<String, Object> sendToControlPlatformWithCert(String endpoint, String requestData, String clientCert) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> body = new HashMap<>();
        body.put("authId", vAuthConfig.getAuthId());
        body.put("requestData", requestData);
        body.put("clientCert", clientCert);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = vAuthConfig.getControlPlatformUrl() + endpoint;
        log.debug("发送请求到管控平台（含客户端证书）: {}", url);
        return restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * 检查管控平台响应是否成功
     */
    private boolean isSuccess(Map<String, Object> response) {
        return response != null && Integer.valueOf(200).equals(response.get("code"));
    }
}
