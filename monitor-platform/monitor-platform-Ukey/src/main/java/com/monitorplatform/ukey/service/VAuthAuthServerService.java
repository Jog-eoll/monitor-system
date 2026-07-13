package com.monitorplatform.ukey.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.monitorplatform.ukey.entity.UkeyCertificate;
import com.monitorplatform.ukey.jna.VAuthServerSDKLibrary;
import com.monitorplatform.ukey.config.TokenStore;
import com.monitorplatform.ukey.config.JwtUtil;
import com.monitorplatform.ukey.websocket.UkeyStatusPushService;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * VAuth 认证服务端适配层（运行在 Linux 上的管控平台）
 *
 * 支持两种硬件模式（通过 vauth.server.mode 配置）：
 * - sdf：使用 SDF 密码卡（生产环境）
 * - ukey：使用 UKey USB 设备（调试/演示，SDK >= 20260302）
 */
@Slf4j
@Service
public class VAuthAuthServerService {

    @Value("${vauth.server.mode:sdf}")
    private String serverMode;

    @Value("${vauth.server.password:88888888}")
    private String serverPassword;

    @Value("${vauth.server.auth-id:auth-server}")
    private String serverAuthId;

    @Value("${vauth.server.ukey-path:}")
    private String serverUkeyPath;

    @Value("${vauth.server.ukey-sn:}")
    private String serverUkeySn;

    @Value("${vauth.server.ukey-cer-sn:}")
    private String serverUkeyCerSn;

    @Value("${vauth.server.ukey-cer-id:}")
    private String serverUkeyCerId;

    private final VAuthServerSDKLibrary sdk = VAuthServerSDKLibrary.INSTANCE;

    /** 设备句柄（UKey 或 SDF 通用）
     * volatile 保证多线程可见性：SDK 回调线程（拔出置-1）与异步开启线程（插入赋值）及业务请求线程（读取）三者可见性一致 */
    private volatile int deviceHandle = -1;

    /** 服务端 UKey 在线状态（volatile 保证多线程可见性） */
    private volatile boolean serverUkeyOnline = false;

    @Resource
    private TokenStore tokenStore;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    @Resource
    private UkeyCertificateService ukeyCertificateService;

    /** 用于服务端 UKey 插拔事件后主动推送状态给前端，@Lazy 打破循环依赖 */
    @Lazy
    @Resource
    private UkeyStatusPushService ukeyStatusPushService;

    /**
     * 客户端证书缓存：key = authId, value = PEM 内容
     * ParseAuthInfo 前存入，QueryCerCallback 中读取
     */
    private final ConcurrentHashMap<String, String> clientCertCache = new ConcurrentHashMap<>();

    // JNA 回调对象必须保持强引用，防止被 GC 回收
    private VAuthServerSDKLibrary.FnQueryKeyCallBack queryKeyCallback;
    private VAuthServerSDKLibrary.FnQueryCerCallBack queryCerCallback;
    private VAuthServerSDKLibrary.FnUkeyEventCallBack ukeyEventCallback;
    private VAuthServerSDKLibrary.FnKeyCallBack keyCallback;

    /**
     * 会话密钥缓存：key = "authId_ver", value = 原始密钥(BASE64)
     * 认证时 SDK 通过 KeyCallback 推送，解密侧通过 /auth/key/query 拉取
     */
    private final ConcurrentHashMap<String, String> sessionKeyCache = new ConcurrentHashMap<>();

    /**
     * 认证时刻的证书快照缓存：key = authId, value = PEM证书
     * 在 KeyCallback 触发时（ParseAuthInfo 执行后）快照，此时 clientCertCache 中必然有该 authId 的证书。
     * 比 clientCertCache 更可靠——避免因重启或 findCertificate 查到错误 DB 记录而拿到错误证书。
     */
    private final ConcurrentHashMap<String, String> sessionCertCache = new ConcurrentHashMap<>();

    /**
     * 专用线程池：处理 UKey 插入后的异步 openUkey()。
     * 不能在 SDK 回调线程内直接调用 SDK 函数，否则会死锁/失败。
     */
    private final ScheduledExecutorService ukeyOpenExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ukey-open-thread");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    public void init() {
        log.info("[VAuthServer] 初始化 VAuth SDK, 模式={}", serverMode);
        boolean initOk = sdk.VAuth_Init();
        if (!initOk) {
            int code = sdk.VAuth_GetLastError();
            String msg = getErrorText(code);
            log.error("[VAuthServer] VAuth_Init 失败, code={}, msg={}", code, msg);
            throw new IllegalStateException("VAuth SDK 初始化失败: " + msg);
        }

        // 三个回调提前到最开头统一注册，不论是否有 UKey
        queryKeyCallback = (handle, id, ver, dwUser) -> {
            log.debug("[VAuthServer][QueryKeyCallback] id={}, ver={}", id, ver);
            return 0;
        };
        sdk.VAuth_SetQueryKeyCallback(queryKeyCallback, Pointer.NULL);

        queryCerCallback = (id, type, dwUser) -> {
            log.debug("[VAuthServer][QueryCerCallback] id={}, type={}", id, type);
            String cert = clientCertCache.get(id);
            if (cert == null) {
                for (String k : clientCertCache.keySet()) {
                    if (k.equalsIgnoreCase(id)) {
                        cert = clientCertCache.get(k);
                        break;
                    }
                }
            }
            if (cert == null || cert.isEmpty()) {
                log.error("[VAuthServer][QueryCerCallback] 未找到 id={} 的客户端证书", id);
                return -1;
            }
            boolean ok = sdk.VAuth_SetCer(id, type, cert);
            return ok ? 0 : -1;
        };
        sdk.VAuth_SetQueryCerCallback(queryCerCallback, Pointer.NULL);

        // KeyCallback：认证完成后 SDK 推送会话密钥，存入 sessionKeyCache 供解密侧查询
        keyCallback = (id, ver, key, dwUser) -> {
            String cacheKey = id + "_" + ver;
            sessionKeyCache.put(cacheKey, key);
            log.info("[VAuthServer][KeyCallback] 收到会话密钥: id={}, ver={}, 已缓存(总数={})",
                    id, ver, sessionKeyCache.size());

            // 在 KeyCallback 触发时同步快照该 authId 的证书。
            // 此时 handleAuthVerify 已将客户端证书写入 clientCertCache，可靠性最高。
            // 后续 queryCertificate 优先从 sessionCertCache 取，避免 DB fallback 返回错误证书。
            String certSnapshot = clientCertCache.get(id);
            if (certSnapshot == null) {
                for (String k : clientCertCache.keySet()) {
                    if (k.equalsIgnoreCase(id)) { certSnapshot = clientCertCache.get(k); break; }
                }
            }
            if (certSnapshot != null && !certSnapshot.isEmpty()) {
                sessionCertCache.put(id, certSnapshot);
                log.info("[VAuthServer][KeyCallback] 证书快照已缓存: id={}", id);
            } else {
                log.warn("[VAuthServer][KeyCallback] 未找到 id={} 的证书快照（客户端可能未发送证书）", id);
            }
            return 0;
        };
        sdk.VAuth_SetKeyCallback(keyCallback, Pointer.NULL);
        log.info("[VAuthServer] 注册 KeyCallback（接收会话密钥）: OK");

        if ("ukey".equalsIgnoreCase(serverMode)) {
            // 注册 UKey 插拔事件回调
            ukeyEventCallback = (type, name, msg, dwUser) -> {
                if (type == 1) {
                    // 插入事件：不能在 SDK 回调线程内直接调用 SDK 函数（会死锁）
                    // 先立即标记在线，再异步执行 openUkey()
                    log.info("[VAuthServer] 检测到 UKey 插入: name={}, type={}", name, type);
                    serverUkeyOnline = true;
                    ukeyOpenExecutor.schedule(() -> {
                        synchronized (VAuthAuthServerService.this) {
                            try {
                                if (deviceHandle < 0) {
                                    int handle = openUkey();
                                    if (handle >= 0) {
                                        deviceHandle = handle;
                                        log.info("[VAuthServer] UKey 异步开启成功, handle={}, 认证服务已就绪", handle);
                                        // 服务端 UKey 插入并成功开启，推送最新状态给前端
                                        ukeyStatusPushService.pushCurrentStatus();
                                    } else {
                                        // 打开失败则回退为离线，等下次插入事件重试
                                        serverUkeyOnline = false;
                                        log.warn("[VAuthServer] UKey 异步开启失败, serverUkeyOnline 已回退为 false");
                                        // 插入失败也推送，让前端知道服务端 UKey 实际不可用
                                        ukeyStatusPushService.pushCurrentStatus();
                                    }
                                } else {
                                    log.info("[VAuthServer] UKey 已打开(handle={}), 无需重复打开", deviceHandle);
                                }
                            } catch (Exception e) {
                                serverUkeyOnline = false;
                                log.error("[VAuthServer] UKey 异步开启异常", e);
                                ukeyStatusPushService.pushCurrentStatus();
                            }
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                } else {
                    // 拔出：清空所有 Token 并写入 JWT 黑名单
                    log.warn("[VAuthServer] 服务端 UKey 拔出! name={}, type={}", name, type);
                    serverUkeyOnline = false;
                    deviceHandle = -1;
                    addAllToBlacklist();
                    log.warn("[VAuthServer] 已清空所有 Token 并写入黑名单, 强制所有用户下线");
                    // 服务端 UKey 拔出，立即推送强制下线事件给所有前端
                    ukeyStatusPushService.pushCurrentStatus();
                }
                return 0;
            };
            boolean evOk = sdk.VAuth_SetUkeyEventCallback(ukeyEventCallback, Pointer.NULL);
            log.info("[VAuthServer] 注册 UkeyEventCallback: {}", evOk ? "OK" : "FAIL(code=" + sdk.VAuth_GetLastError() + ")");

            // 尝试打开 UKey，未找到不影响启动
            deviceHandle = openUkey();
            if (deviceHandle >= 0) {
                serverUkeyOnline = true;
                log.info("[VAuthServer] VAuth SDK 初始化完成, 模式={}, handle={}", serverMode, deviceHandle);
            } else {
                log.warn("[VAuthServer] 当前未检测到 UKey, 等待 UKey 插入...");
                log.warn("[VAuthServer] 证书校验功能不受影响，/auth/server/* 接口将在 UKey 插入后自动就绪");
            }

            // 启动扫描：延迟 1.5s 再次检测，防止 SDK 事件回调注册完成前 UKey 已插入导致的窗口期漏检
            // 若首次 openUkey() 已成功(handle >= 0)，此处扫描会因 deviceHandle >= 0 直接跳过，无副作用
            ukeyOpenExecutor.schedule(() -> {
                synchronized (VAuthAuthServerService.this) {
                    if (deviceHandle < 0) {
                        log.info("[VAuthServer][startup-scan] 延迟扫描 UKey...");
                        int handle = openUkey();
                        if (handle >= 0) {
                            deviceHandle = handle;
                            serverUkeyOnline = true;
                            log.info("[VAuthServer][startup-scan] 检测到已插入 UKey, handle={}, 认证服务已就绪", handle);
                            try {
                                ukeyStatusPushService.pushCurrentStatus();
                            } catch (Exception ex) {
                                log.warn("[VAuthServer][startup-scan] 推送状态失败: {}", ex.getMessage());
                            }
                        } else {
                            log.info("[VAuthServer][startup-scan] 未检测到 UKey，继续等待插入事件");
                        }
                    } else {
                        log.debug("[VAuthServer][startup-scan] UKey 已就绪(handle={}), 跳过扫描", deviceHandle);
                    }
                }
            }, 1500, TimeUnit.MILLISECONDS);
        } else {
            deviceHandle = openSdf();
            if (deviceHandle < 0) {
                log.warn("[VAuthServer] SDF 设备打开失败，VAuth 认证功能不可用");
                return;
            }
            // 注册 QueryKey / QueryCer 回调（SDF 模式无插拔事件）
            boolean qkOk = sdk.VAuth_SetQueryKeyCallback(queryKeyCallback, Pointer.NULL);
            log.info("[VAuthServer] 注册 QueryKeyCallback: {}", qkOk ? "OK" : "FAIL");
            boolean qcOk = sdk.VAuth_SetQueryCerCallback(queryCerCallback, Pointer.NULL);
            log.info("[VAuthServer] 注册 QueryCerCallback: {}", qcOk ? "OK" : "FAIL");
            log.info("[VAuthServer] VAuth SDK 初始化完成, 模式={}, handle={}", serverMode, deviceHandle);
        }
    }

    /**
     * 返回服务端 UKey 是否在线
     * SDF 模式下始终返回 true
     */
    public boolean isServerUkeyOnline() {
        if (!"ukey".equalsIgnoreCase(serverMode)) {
            return true;
        }
        return serverUkeyOnline;
    }

    /**
     * 以 SDF 模式打开设备
     */
    private int openSdf() {
        log.info("[VAuthServer] 正在打开 SDF 密码卡...");
        int handle = sdk.VAuth_OpenSDF(serverPassword, serverAuthId);
        if (handle < 0) {
            int code = sdk.VAuth_GetLastError();
            String msg = getErrorText(code);
            log.warn("[VAuthServer] 打开 SDF 失败, handle={}, code={}, msg={}", handle, code, msg);
        }
        return handle;
    }

    /**
     * 以 UKey 模式打开设备
     */
    private int openUkey() {
        log.info("[VAuthServer] 正在查找 UKey 设备, bindConfig: authId={}, path={}, sn={}, cerSn={}, cerId={}",
                serverAuthId, serverUkeyPath, serverUkeySn, serverUkeyCerSn, serverUkeyCerId);

        PointerByReference pReply = new PointerByReference();
        boolean listOk = sdk.VAuth_ListUkeyInfos(pReply);
        if (!listOk || pReply.getValue() == null) {
            int code = sdk.VAuth_GetLastError();
            String msg = getErrorText(code);
            log.warn("[VAuthServer] 列举 UKey 失败, code={}, msg={}", code, msg);
            return -1;
        }

        String ukeyJson = pReply.getValue().getString(0, "UTF-8");
        sdk.VAuth_Free(pReply.getValue());
        log.info("[VAuthServer] 发现 UKey 设备: {}", ukeyJson);
        List<UkeyDeviceInfo> listedDevices = parseUkeyDevices(ukeyJson);
        log.info("[VAuthServer] UKey 枚举结果: count={}", listedDevices.size());
        for (int i = 0; i < listedDevices.size(); i++) {
            UkeyDeviceInfo device = listedDevices.get(i);
            log.info("[VAuthServer] UKey[{}]: name={}, label={}, sn={}, cerSn={}, cerId={}, path={}",
                    i, device.name, device.label, device.sn, device.cerSn, device.cerId, device.path);
        }

        UkeyDeviceInfo selectedDevice = selectUkeyDevice(listedDevices);
        if (selectedDevice == null || !hasText(selectedDevice.path) || !hasText(selectedDevice.cerId)) {
            log.warn("[VAuthServer] 无法选择可用服务端 UKey");
            return -1;
        }

        if (hasConfiguredServerAuthId() && !matchesAuthId(serverAuthId, selectedDevice.cerId)) {
            log.warn("[VAuthServer] 服务端 UKey 绑定与 authId 不一致，拒绝打开: authId={}, selectedCerId={}, selectedPath={}",
                    serverAuthId, selectedDevice.cerId, selectedDevice.path);
            return -1;
        }

        String effectiveAuthId = resolveEffectiveServerAuthId(selectedDevice);
        if (!hasText(effectiveAuthId)) {
            log.warn("[VAuthServer] 无法解析服务端认证 ID，请配置 VAUTH_SERVER_AUTH_ID 或确认 UKey cerId 有效");
            return -1;
        }

        if (hasText(serverUkeyPath) && !equalsIgnoreCase(serverUkeyPath, selectedDevice.path)) {
            log.warn("[VAuthServer] 配置的 UKey path 已不是当前实际 path，已按稳定标识重新匹配: configuredPath={}, actualPath={}, cerId={}",
                    serverUkeyPath, selectedDevice.path, selectedDevice.cerId);
        }
        log.info("[VAuthServer] 绑定服务端 UKey: name={}, label={}, sn={}, cerSn={}, cerId={}, path={}, authId={}",
                selectedDevice.name, selectedDevice.label, selectedDevice.sn, selectedDevice.cerSn,
                selectedDevice.cerId, selectedDevice.path, effectiveAuthId);

        int handle = sdk.VAuth_OpenUkey(selectedDevice.path, serverPassword, effectiveAuthId);
        if (handle < 0) {
            int code = sdk.VAuth_GetLastError();
            String msg = getErrorText(code);
            log.warn("[VAuthServer] 打开 UKey 失败, handle={}, code={}, msg={}", handle, code, msg);
        } else {
            serverAuthId = effectiveAuthId;
            log.info("[VAuthServer] 打开 UKey 成功, handle={}, effectiveAuthId={}, selectedCerId={}",
                    handle, effectiveAuthId, selectedDevice.cerId);
        }
        return handle;
    }

    private UkeyDeviceInfo selectUkeyDevice(List<UkeyDeviceInfo> devices) {
        if (devices.isEmpty()) {
            log.warn("[VAuthServer] UKey 列表为空");
            return null;
        }

        if (hasText(serverUkeyCerId)) {
            return uniqueOrWarn(filterByField(devices, "cerId", serverUkeyCerId), "cerId=" + serverUkeyCerId);
        }
        if (hasText(serverUkeyCerSn)) {
            return uniqueOrWarn(filterByField(devices, "cerSn", serverUkeyCerSn), "cerSn=" + serverUkeyCerSn);
        }
        if (hasText(serverUkeySn)) {
            return uniqueOrWarn(filterByField(devices, "sn", serverUkeySn), "sn=" + serverUkeySn);
        }
        if (hasConfiguredServerAuthId()) {
            List<UkeyDeviceInfo> matches = new ArrayList<>();
            for (UkeyDeviceInfo device : devices) {
                if (matchesAuthId(serverAuthId, device.cerId)) {
                    matches.add(device);
                }
            }
            return uniqueOrWarn(matches, "authId=" + serverAuthId);
        }
        if (hasText(serverUkeyPath)) {
            log.info("[VAuthServer] 未配置稳定 UKey 标识，使用 path 作为兼容兜底: {}", serverUkeyPath);
            return uniqueOrWarn(filterByField(devices, "path", serverUkeyPath), "path=" + serverUkeyPath);
        }
        if (devices.size() == 1) {
            UkeyDeviceInfo device = devices.get(0);
            log.info("[VAuthServer] 未配置服务端认证 ID，当前仅发现一个 UKey，自动使用该设备并从 cerId 推导 authId: sn={}, cerSn={}, cerId={}",
                    device.sn, device.cerSn, device.cerId);
            return device;
        }

        log.warn("[VAuthServer] 已发现 {} 个 UKey，但未配置 VAUTH_SERVER_UKEY_CER_ID/VAUTH_SERVER_UKEY_CER_SN/VAUTH_SERVER_UKEY_SN 或 VAUTH_SERVER_AUTH_ID，拒绝默认选择设备",
                devices.size());
        return null;
    }

    private List<UkeyDeviceInfo> parseUkeyDevices(String ukeyJson) {
        List<UkeyDeviceInfo> devices = new ArrayList<>();
        if (!hasText(ukeyJson)) {
            return devices;
        }
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
            log.warn("[VAuthServer] 解析 UKey 列表失败: {}", e.getMessage());
        }
        return devices;
    }

    private List<UkeyDeviceInfo> filterByField(List<UkeyDeviceInfo> devices, String field, String expected) {
        List<UkeyDeviceInfo> matches = new ArrayList<>();
        for (UkeyDeviceInfo device : devices) {
            String actual;
            if ("cerId".equals(field)) {
                actual = device.cerId;
            } else if ("cerSn".equals(field)) {
                actual = device.cerSn;
            } else if ("sn".equals(field)) {
                actual = device.sn;
            } else if ("path".equals(field)) {
                actual = device.path;
            } else {
                actual = null;
            }
            if (equalsIgnoreCase(expected, actual)) {
                matches.add(device);
            }
        }
        return matches;
    }

    private UkeyDeviceInfo uniqueOrWarn(List<UkeyDeviceInfo> matches, String condition) {
        if (matches.isEmpty()) {
            log.warn("[VAuthServer] 未找到匹配的服务端 UKey，匹配条件: {}", condition);
            return null;
        }
        if (matches.size() > 1) {
            log.warn("[VAuthServer] 匹配到多个服务端 UKey，匹配条件: {}，请增加 cerId/cerSn/sn 约束", condition);
            return null;
        }
        return matches.get(0);
    }

    private String resolveEffectiveServerAuthId(UkeyDeviceInfo device) {
        if (hasConfiguredServerAuthId()) {
            return serverAuthId.trim();
        }
        return deriveAuthIdFromCerId(device.cerId);
    }

    private String deriveAuthIdFromCerId(String cerId) {
        if (!hasText(cerId)) {
            return null;
        }
        String trimmed = cerId.trim();
        int suffixIndex = trimmed.indexOf('_');
        if (suffixIndex > 0) {
            return trimmed.substring(0, suffixIndex);
        }
        return trimmed;
    }

    private boolean hasConfiguredServerAuthId() {
        return hasText(serverAuthId) && !"auth-server".equalsIgnoreCase(serverAuthId.trim());
    }

    private boolean matchesAuthId(String authId, String cerId) {
        if (!hasText(authId) || !hasText(cerId)) {
            return false;
        }
        String expected = authId.trim();
        String actual = cerId.trim();
        return actual.equalsIgnoreCase(expected)
                || actual.toLowerCase().startsWith(expected.toLowerCase() + "_");
    }

    private boolean equalsIgnoreCase(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static class UkeyDeviceInfo {
        private String name;
        private String label;
        private String sn;
        private String cerSn;
        private String cerId;
        private String path;
    }

    private int openFirstUkeyLegacy() {
        log.info("[VAuthServer] 正在查找 UKey 设备...");

        // 1. 列举 UKey
        PointerByReference pReply = new PointerByReference();
        boolean listOk = sdk.VAuth_ListUkeyInfos(pReply);
        if (!listOk || pReply.getValue() == null) {
            int code = sdk.VAuth_GetLastError();
            String msg = getErrorText(code);
            log.warn("[VAuthServer] 列举 UKey 失败, code={}, msg={}", code, msg);
            return -1;
        }

        String ukeyJson = pReply.getValue().getString(0, "UTF-8");
        sdk.VAuth_Free(pReply.getValue());
        log.info("[VAuthServer] 发现 UKey 设备: {}", ukeyJson);

        // 2. 从 JSON 中提取第一个 UKey 的 path
        String ukeyPath = extractJsonField(ukeyJson, "path");
        if (ukeyPath == null || ukeyPath.isEmpty()) {
            log.warn("[VAuthServer] 无法从 UKey 信息中提取 path");
            return -1;
        }
        log.info("[VAuthServer] 使用 UKey path={}", ukeyPath);

        // 3. 打开 UKey
        int handle = sdk.VAuth_OpenUkey(ukeyPath, serverPassword, serverAuthId);
        if (handle < 0) {
            int code = sdk.VAuth_GetLastError();
            String msg = getErrorText(code);
            log.warn("[VAuthServer] 打开 UKey 失败, handle={}, code={}, msg={}", handle, code, msg);
        }
        return handle;
    }

    /**
     * 简单提取 JSON 字段值（避免引入 JSON 库依赖）
     */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    @PreDestroy
    public void cleanup() {
        log.info("[VAuthServer] 清理 VAuth SDK 资源...");
        ukeyOpenExecutor.shutdownNow();
        try {
            if (deviceHandle >= 0) {
                sdk.VAuth_CloseHandle(deviceHandle);
            }
        } catch (Exception e) {
            log.warn("[VAuthServer] 关闭设备句柄异常", e);
        }
        try {
            sdk.VAuth_Cleanup();
        } catch (Exception e) {
            log.warn("[VAuthServer] VAuth_Cleanup 异常", e);
        }
    }

    /**
     * 处理客户端认证请求（第一步）
     * 在调用 SDK 前，根据 authId 前缀从数据库查出对应证书并注入缓存，
     * 供 QueryCerCallback 在 ParseAuthReq 过程中使用。
     */
    public String handleAuthRequest(String authId, String reqInfo) {
        checkDeviceReady();

        // 关键：ParseAuthReq 执行时 SDK 会触发 QueryCerCallback，
        // 此时需要缓存中已有该 authId 对应的证书内容。
        // 根据 authId 前缀（cert_serial_no 可能含后缀如 _IE2D11）模糊匹配数据库中的证书。
        if (!clientCertCache.containsKey(authId)) {
            UkeyCertificate cert = ukeyCertificateService.getByCertSerialNoPrefix(authId);
            if (cert == null) {
                // 证书记录不存在于数据库（未注册）：ParseAuthReq 阶段通常不需要客户端证书，
                // 客户端会在第二步 verify 时主动上传，届时写入缓存即可。
                log.info("[VAuthServer] authId={} 未在数据库预注册证书，将依赖客户端在 verify 阶段主动上传", authId);
            } else if (cert.getCertificateContent() == null || cert.getCertificateContent().isEmpty()) {
                // 证书记录存在但 certificate_content 为空：说明注册时未上传证书文件内容，
                // 同样依赖 verify 阶段客户端主动上传，流程正常。
                log.info("[VAuthServer] authId={} 证书记录存在(certSerialNo={})但内容为空，将依赖客户端在 verify 阶段主动上传",
                        authId, cert.getCertSerialNo());
            } else {
                clientCertCache.put(authId, cert.getCertificateContent());
                log.debug("[VAuthServer] 预加载客户端证书到缓存, authId={}, certSerialNo={}", authId, cert.getCertSerialNo());
            }
        }

        PointerByReference pReply = new PointerByReference();
        try {
            boolean success = sdk.VAuth_ParseAuthReq(deviceHandle, authId, reqInfo, pReply);
            if (!success || pReply.getValue() == null) {
                int code = sdk.VAuth_GetLastError();
                String msg = getErrorText(code);
                log.error("[VAuthServer] ParseAuthReq 失败, code={}, msg={}", code, msg);
                throw new IllegalStateException("ParseAuthReq 失败: " + msg);
            }
            return pReply.getValue().getString(0, "UTF-8");
        } finally {
            if (pReply.getValue() != null) {
                sdk.VAuth_Free(pReply.getValue());
            }
        }
    }

    /**
     * 处理客户端认证信息（第二步）
     */
    public String handleAuthVerify(String authId, String clientCert, String authInfo) {
        checkDeviceReady();
        if (!hasText(authId)) {
            throw new IllegalArgumentException("authId is empty");
        }
        if (!hasText(authInfo)) {
            throw new IllegalArgumentException("authInfo is empty");
        }

        String certForVerify = clientCert;
        // 将客户端证书写入缓存，QueryCerCallback 回调时读取
        if (hasText(clientCert)) {
            clientCertCache.put(authId, clientCert);
            log.debug("[VAuthServer] 客户端证书已缓存, authId={}, len={}", authId, clientCert.length());
        } else {
            certForVerify = clientCertCache.get(authId);
            log.warn("[VAuthServer] 客户端未传入证书内容, authId={}", authId);
        }
        log.info("[VAuthServer] AuthVerify input: authId={}, authInfoLen={}, clientCertLen={}",
                authId, authInfo.length(), certForVerify == null ? 0 : certForVerify.length());

        PointerByReference pReply = new PointerByReference();
        try {
            boolean success = sdk.VAuth_ParseAuthInfo(deviceHandle, authId, certForVerify, authInfo, pReply);
            if (!success || pReply.getValue() == null) {
                int code = sdk.VAuth_GetLastError();
                String msg = getErrorText(code);
                log.error("[VAuthServer] ParseAuthInfo 失败, code={}, msg={}", code, msg);
                throw new IllegalStateException("ParseAuthInfo 失败: " + msg);
            }
            return pReply.getValue().getString(0, "UTF-8");
        } finally {
            if (pReply.getValue() != null) {
                sdk.VAuth_Free(pReply.getValue());
            }
        }
    }

    /**
     * 处理客户端上报的认证错误（可选第三步）
     */
    public String handleAuthError(String errorInfo) {
        checkDeviceReady();
        PointerByReference pReply = new PointerByReference();
        try {
            boolean success = sdk.VAuth_ParseAuthError(deviceHandle, errorInfo, pReply);
            if (!success || pReply.getValue() == null) {
                int code = sdk.VAuth_GetLastError();
                String msg = getErrorText(code);
                log.error("[VAuthServer] ParseAuthError 失败, code={}, msg={}", code, msg);
                throw new IllegalStateException("ParseAuthError 失败: " + msg);
            }
            return pReply.getValue().getString(0, "UTF-8");
        } finally {
            if (pReply.getValue() != null) {
                sdk.VAuth_Free(pReply.getValue());
            }
        }
    }

    private void checkDeviceReady() {
        if (deviceHandle < 0) {
            throw new IllegalStateException(
                    "认证服务不可用：" + ("ukey".equalsIgnoreCase(serverMode) ? "UKey" : "SDF")
                            + " 设备未就绪，请检查硬件设备和驱动");
        }
    }

    private String getErrorText(int code) {
        try {
            Pointer pointer = sdk.VAuth_GetErrorText(code);
            if (pointer == null) {
                return "unknown error, code=" + code;
            }

            String utf8 = readCString(pointer, "UTF-8");
            String gbk = readCString(pointer, "GBK");
            if (looksGarbled(utf8) && hasText(gbk) && !looksGarbled(gbk)) {
                return gbk;
            }
            if (hasText(utf8)) {
                return utf8;
            }
            if (hasText(gbk)) {
                return gbk;
            }
        } catch (Exception e) {
            log.warn("[VAuthServer] read SDK error text failed: code={}, error={}", code, e.getMessage());
        }
        return "unknown error, code=" + code;
    }

    private String readCString(Pointer pointer, String charsetName) {
        if (pointer == null || !Charset.isSupported(charsetName)) {
            return null;
        }
        try {
            return pointer.getString(0, charsetName);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksGarbled(String text) {
        if (!hasText(text)) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\uFFFD') {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    // ==================== 密钥/证书分发接口（供解密侧调用） ====================

    /**
     * 查询加密后的会话密钥（解密侧 QueryKeyCallback 中调用）
     *
     * <p>流程：从 sessionKeyCache 取出原始密钥 → 用请求方证书加密 → 返回加密后密钥
     * <p>加密后的密钥只有持有对应私钥的设备才能解密（SM2 非对称加密）
     *
     * @param srcAuthId     数据源（加密方）的认证ID，如发布网关的 authId
     * @param ver           密钥版本
     * @param requestAuthId 请求方（解密方）的认证ID
     * @param requestCert   请求方直接提供的证书内容（优先使用），为空时自动从缓存/DB查找
     * @return 加密后的密钥(BASE64)
     */
    public String queryEncryptedKey(String srcAuthId, String ver, String requestAuthId, String requestCert) {
        checkDeviceReady();

        // 1. 查找源端（加密方）的原始会话密钥
        String cacheKey = srcAuthId + "_" + ver;
        String rawKey = sessionKeyCache.get(cacheKey);
        if (rawKey == null) {
            log.error("[VAuthServer] 未找到会话密钥: srcAuthId={}, ver={}, cacheKey={}, 缓存大小={}",
                    srcAuthId, ver, cacheKey, sessionKeyCache.size());
            throw new IllegalStateException("未找到会话密钥: " + cacheKey);
        }

        // 2. 确定用于加密密钥的证书：优先使用请求方直接传来的证书，其次从缓存查找
        String certToUse = (requestCert != null && !requestCert.isEmpty())
                ? requestCert
                : findCertificate(requestAuthId);
        if (certToUse == null || certToUse.isEmpty()) {
            log.error("[VAuthServer] 未找到请求方证书: requestAuthId={}", requestAuthId);
            throw new IllegalStateException("未找到请求方证书: " + requestAuthId);
        }
        log.info("[VAuthServer] 密钥加密参数: rawKey长度={}, cert长度={}, cert来源={}",
                rawKey.length(),
                certToUse.length(),
                (requestCert != null && !requestCert.isEmpty()) ? "请求方直传" : "缓存/DB");

        // 3. 用请求方的证书（公钥）加密原始密钥
        PointerByReference pReply = new PointerByReference();
        try {
            boolean ok = sdk.VAuth_EncryptKey(deviceHandle, rawKey, certToUse, pReply);
            if (!ok || pReply.getValue() == null) {
                int code = sdk.VAuth_GetLastError();
                String msg = getErrorText(code);
                log.error("[VAuthServer] VAuth_EncryptKey 失败: code={}, msg={}", code, msg);
                throw new IllegalStateException("VAuth_EncryptKey 失败: " + msg);
            }
            String encryptedKey = pReply.getValue().getString(0, "UTF-8");
            sdk.VAuth_Free(pReply.getValue());
            log.info("[VAuthServer] 密钥加密成功: srcAuthId={}, ver={}, requestAuthId={}, 加密后长度={}",
                    srcAuthId, ver, requestAuthId, encryptedKey.length());
            return encryptedKey;
        } finally {
            // pReply 已在上面释放
        }
    }

    /**
     * 查询客户端签名证书（解密侧 QueryCerCallback 中调用）
     * 优先从 sessionCertCache（认证时快照）中查找，避免 clientCertCache 过期或 DB 返回错误记录。
     *
     * @param authId 需要查询证书的认证ID
     * @return 签名证书(PEM)，未找到时返回 null
     */
    public String queryCertificate(String authId) {
        // 优先查认证时刻快照（最可靠）
        String cert = sessionCertCache.get(authId);
        if (cert != null && !cert.isEmpty()) {
            log.debug("[VAuthServer] 从快照缓存取证书: authId={}", authId);
            return cert;
        }
        log.debug("[VAuthServer] 快照缓存未命中，回退到 findCertificate: authId={}", authId);
        return findCertificate(authId);
    }

    /**
     * 从缓存或数据库查找客户端证书（支持模糊匹配）
     */
    private String findCertificate(String authId) {
        // 先精确匹配缓存
        String cert = clientCertCache.get(authId);
        if (cert != null && !cert.isEmpty()) {
            return cert;
        }
        // 大小写不敏感匹配
        for (String k : clientCertCache.keySet()) {
            if (k.equalsIgnoreCase(authId)) {
                cert = clientCertCache.get(k);
                if (cert != null && !cert.isEmpty()) {
                    return cert;
                }
            }
        }
        // 从数据库查找
        UkeyCertificate dbCert = ukeyCertificateService.getByCertSerialNoPrefix(authId);
        if (dbCert != null && dbCert.getCertificateContent() != null && !dbCert.getCertificateContent().isEmpty()) {
            clientCertCache.put(authId, dbCert.getCertificateContent());
            return dbCert.getCertificateContent();
        }
        return null;
    }

    /**
     * 将所有已登录的 JWT 写入 Redis 黑名单，然后清空内存 TokenStore
     * 服务端 UKey 拔出时调用
     */
    private void addAllToBlacklist() {
        // 先获取所有 token 列表，再清除
        List<String> allTokens = tokenStore.getAllTokens();
        tokenStore.removeAll();
        for (String token : allTokens) {
            try {
                Claims claims = jwtUtil.parse(token);
                String jti = claims.getId();
                long ttl = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
                if (ttl > 0) {
                    stringRedisTemplate.opsForValue().set(
                            JWT_BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.SECONDS);
                    log.info("[JWT黑名单] 服务端 UKey拔出, jti={} 已入黑名单, ttl={}s", jti, ttl);
                }
            } catch (JwtException ignored) {
                // 已过期 Token 无需处理
            }
        }
    }
}
