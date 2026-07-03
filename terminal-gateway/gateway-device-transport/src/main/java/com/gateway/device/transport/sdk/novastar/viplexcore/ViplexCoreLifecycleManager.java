package com.gateway.device.transport.sdk.novastar.viplexcore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.base.novastar.viplexcore.*;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.model.DeviceAuthEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * ViplexCore SDK 生命周期管理器。
 *
 * <p>负责 SDK 库加载、初始化和销毁。所有 SDK 调用通过单线程 executor 串行化，
 * 满足 Qt 底层线程安全要求。异步回调通过 {@link CompletableFuture} 转为同步等待。</p>
 *
 * <p>由于 SDK 回调无 correlation ID，同一时刻最多一个 in-flight 调用。</p>
 */
@Slf4j
public class ViplexCoreLifecycleManager implements ViplexCoreChannel {

    private static final int MAX_LOGIN_FAILURES = 10;
    private static final Duration LOGIN_COOLDOWN = Duration.ofSeconds(60);

    /**
     * 获取底层 JNA 库实例，供 Handler 调用具体 SDK 函数
     */
    @Getter
    private final ViplexCoreLibrary library;
    private final ExecutorService executor;
    /**
     * 登录失败计数（按 SN），用于防暴力登录
     */
    private final Map<String, LoginFailRecord> loginFailures = new ConcurrentHashMap<>();
    /**
     * 当前活跃的登录会话（"SN#loginType" → 已登录账号）。
     * <p>SDK nvLoginAsync 建立 TCP 长连接，重复调用会失败。
     * 不同 loginType 可独立建立会话。</p>
     */
    private final Map<String, ViplexCoreAccount> activeSessions = new ConcurrentHashMap<>();
    /**
     * 已知设备的可用账号（SN → 账号），首次登录成功后记录，后续直接使用
     */
    private final Map<String, ViplexCoreAccount> knownAccounts = new ConcurrentHashMap<>();
    /**
     * SDK 异步操作 in-flight 信号量，确保同一时刻仅一个异步操作在执行。
     *
     * <p>公平模式避免线程饥饿，与单线程 executor 配合满足
     * "同一时刻最多一个 in-flight 调用"的约束。</p>
     */
    private final Semaphore inFlightGuard = new Semaphore(1, true);
    /**
     * 公共认证存储（跨厂商），用于持久化已认证凭据，登出时清理。
     */
    private volatile DeviceAuthStore authStore;
    /**
     * SDK 是否已成功初始化
     */
    @Getter
    private volatile boolean initialized;

    public ViplexCoreLifecycleManager(ViplexCoreLibrary library, String dataDir,
                                      String company, String phone, String email) {
        this.library = library;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "viplex-sdk");
            t.setDaemon(true);
            return t;
        });

        log.info("ViplexCore SDK 初始化: dataDir={}", dataDir);
        library.nvSetDevLang("Java");

        String credentials = buildCredentials(company, phone, email);
        int ret = library.nvInit(dataDir, credentials);
        if (ret == 0) {
            initialized = true;
            log.info("ViplexCore SDK 初始化成功");
        } else {
            log.error("ViplexCore SDK 初始化失败, code={}", ret);
        }
    }

    private static String buildCredentials(String company, String phone, String email) {
        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode json = mapper.createObjectNode();
        json.put("company", company != null ? company : "");
        json.put("phone", phone != null ? phone : "");
        json.put("email", email != null ? email : "");
        return json.toString();
    }

    private static String sessionKey(String sn, LoginType loginType) {
        return sn + "#" + loginType.getCode();
    }

    private static boolean sessionKeyMatchesSn(String key, String sn) {
        return key.startsWith(sn + "#");
    }

    /**
     * 设置公共认证存储（由 Spring 配置注入）。
     */
    public void setAuthStore(DeviceAuthStore authStore) {
        this.authStore = authStore;
    }

    /**
     * 获取指定 SN 的已知可用账号（首次登录成功后记录）。
     * 内部 knownAccounts 未命中时回退到 {@link DeviceAuthStore}。
     */
    public ViplexCoreAccount getKnownAccount(String sn) {
        ViplexCoreAccount account = knownAccounts.get(sn);
        if (account != null) return account;
        if (authStore != null) {
            DeviceAuthEntry entry = authStore.get(sn);
            if (entry != null && entry.getAccountId() != null) {
                return ViplexCoreAccount.builder()
                        .accountId(entry.getAccountId())
                        .password(entry.getPassword())
                        .build();
            }
        }
        return null;
    }

    /**
     * 记录指定 SN 的可用账号，同步持久化到 {@link DeviceAuthStore}。
     */
    public void rememberAccount(String sn, ViplexCoreAccount account) {
        knownAccounts.put(sn, account);
        if (authStore != null && account != null) {
            authStore.update(sn, DeviceAuthEntry.builder()
                    .accountId(account.getAccountId())
                    .password(account.getPassword())
                    .build());
        }
    }

    /**
     * 判断指定 SN 的设备是否已登录（默认屏体管理 loginType=0）
     */
    @Override
    public boolean isLoggedIn(String sn) {
        return isLoggedIn(sn, LoginType.MANAGEMENT);
    }

    /**
     * 判断指定 SN + loginType 的会话是否已登录
     */
    @Override
    public boolean isLoggedIn(String sn, LoginType loginType) {
        return activeSessions.containsKey(sessionKey(sn, loginType));
    }

    /**
     * 登出指定设备。
     *
     * @param sn      设备序列号
     * @param timeout 登出超时
     * @return ViplexResponse，success 表示登出成功
     */
    public ViplexResponse logoutDevice(String sn, Duration timeout) {
        if (!initialized) {
            return ViplexResponse.failure(-1, "SDK 未初始化");
        }
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("sn", sn);
        ViplexResponse resp = executeSync(
                cb -> library.nvLogoutAsync(json.toString(), cb), timeout);
        // 无论 SDK 返回什么，清理本地所有 loginType 的会话（SDK 连接可能已断开）
        activeSessions.keySet().removeIf(k -> sessionKeyMatchesSn(k, sn));
        loginFailures.remove(sn);
        knownAccounts.remove(sn);
        if (authStore != null) authStore.remove(sn);
        if (resp.isSuccess()) {
            log.debug("ViplexCore 登出成功 SN={}", sn);
        } else {
            log.warn("ViplexCore 登出返回非成功，已清理本地会话 SN={}", sn);
        }
        return resp;
    }

    /**
     * 销毁 SDK 资源
     */
    public void destroy() {
        initialized = false;
        for (String key : activeSessions.keySet()) {
            try {
                String sn = key.substring(0, key.indexOf('#'));
                logoutDeviceInternalBlocking(sn);
            } catch (Exception e) {
                log.warn("destroy 阶段登出失败 key={}", key, e);
            }
        }
        activeSessions.clear();
        knownAccounts.clear();
        loginFailures.clear();
        executor.shutdownNow();
        log.info("ViplexCore SDK 已销毁");
    }

    /**
     * 同步执行 SDK 调用。
     *
     * @param task    接收 ViplexCallback 的 SDK 调用闭包
     * @param timeout 超时时间
     * @return ViplexResponse 包含 code 和 data
     */
    public ViplexResponse executeSync(Consumer<ViplexCoreLibrary.ViplexCallback> task,
                                      Duration timeout) {
        if (!initialized) {
            return ViplexResponse.failure(-1, "SDK 未初始化");
        }

        // ── 获取 in-flight 槽位，保证同一时刻仅一个 SDK 异步操作 ──
        try {
            if (!inFlightGuard.tryAcquire(timeout.toMillis() + 10_000, TimeUnit.MILLISECONDS)) {
                log.warn("SDK in-flight 槽位等待超时，当前操作被拒绝");
                return ViplexResponse.failure(-1, "SDK 通道繁忙，上一操作仍在执行");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ViplexResponse.failure(-1, "等待 SDK 通道时被中断");
        }

        try {
            CompletableFuture<ViplexResponse> future = new CompletableFuture<>();

            executor.submit(() -> {
                try {
                    task.accept((code, data) -> future.complete(new ViplexResponse(code, data)));
                } catch (Exception e) {
                    log.error("SDK 调用异常", e);
                    future.complete(ViplexResponse.failure(-1, e.getMessage()));
                }
            });

            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("SDK 调用超时 ({}ms)", timeout.toMillis());
                return ViplexResponse.timeout();
            } catch (Exception e) {
                log.error("SDK 调用中断", e);
                return ViplexResponse.failure(-1, e.getMessage());
            }
        } finally {
            inFlightGuard.release();
        }
    }

    /**
     * 同步执行 SDK 调用（支持进度回调）。
     *
     * <p>与 {@link #executeSync} 不同，SDK 回调可能多次触发（如传输进度）。
     * 每次回调经 {@code isComplete} 判定：{@code true} 则结束等待，
     * {@code false} 则忽略并继续等待下一次回调。</p>
     *
     * @param task       接收 ViplexCallback 的 SDK 调用闭包
     * @param timeout    超时时间
     * @param isComplete 完成判定器（true=结束等待）
     * @return ViplexResponse 最终响应
     */
    public ViplexResponse executeWithProgress(Consumer<ViplexCoreLibrary.ViplexCallback> task,
                                              Duration timeout,
                                              Predicate<ViplexResponse> isComplete) {
        if (!initialized) {
            return ViplexResponse.failure(-1, "SDK 未初始化");
        }

        // ── 获取 in-flight 槽位，保证同一时刻仅一个 SDK 异步操作 ──
        try {
            if (!inFlightGuard.tryAcquire(timeout.toMillis() + 10_000, TimeUnit.MILLISECONDS)) {
                log.warn("SDK in-flight 槽位等待超时，当前操作被拒绝");
                return ViplexResponse.failure(-1, "SDK 通道繁忙，上一操作仍在执行");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ViplexResponse.failure(-1, "等待 SDK 通道时被中断");
        }

        try {
            CompletableFuture<ViplexResponse> future = new CompletableFuture<>();

            executor.submit(() -> {
                try {
                    task.accept((code, data) -> {
                        ViplexResponse resp = new ViplexResponse(code, data);
                        if (isComplete.test(resp)) {
                            future.complete(resp);
                        } else {
                            log.debug("SDK 进度回调 (忽略): code={} data={}", code, data);
                        }
                    });
                } catch (Exception e) {
                    log.error("SDK 调用异常", e);
                    future.complete(ViplexResponse.failure(-1, e.getMessage()));
                }
            });

            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("SDK 调用超时 ({}ms)", timeout.toMillis());
                return ViplexResponse.timeout();
            } catch (Exception e) {
                log.error("SDK 调用中断", e);
                return ViplexResponse.failure(-1, e.getMessage());
            }
        } finally {
            inFlightGuard.release();
        }
    }

    /**
     * 广播搜索设备（UDP），捕获第一个发现的设备。
     *
     * @param timeout 搜索超时
     * @return ViplexResponse，data 为设备 JSON
     */
    public ViplexResponse searchDevice(Duration timeout) {
        if (!initialized) {
            return ViplexResponse.failure(-1, "SDK 未初始化");
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();

        executor.submit(() -> {
            try {
                library.nvSearchTerminalAsync((code, data) -> {
                    if (code == 0 && StringUtils.isNotEmpty(data)) {
                        resultRef.compareAndSet(null, data);
                    }
                    latch.countDown();
                });
            } catch (Exception e) {
                log.error("searchDevice 调用异常", e);
                latch.countDown();
            }
        });

        try {
            boolean ok = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("searchDevice 超时 ({}ms)", timeout.toMillis());
                return ViplexResponse.timeout();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ViplexResponse.failure(-1, "搜索被中断");
        }

        String data = resultRef.get();
        if (StringUtils.isEmpty(data)) {
            return ViplexResponse.failure(-1, "未发现设备");
        }
        return new ViplexResponse(0, data);
    }

    /**
     * 广播搜索所有设备，收集 SDK 在超时窗口内回调的每一个终端。
     *
     * <p>SDK {@code nvSearchTerminalAsync} 内部广播 ~4s，每发现一个设备回调一次。
     * 回调在 Native 线程执行，与 executor 线程无关。</p>
     *
     * @param timeout 搜索窗口时长（SDK 内部扫描约 4s，建议 >= 5s）
     * @return ViplexResponse，data 为 {@code {"devices":[...], "count":N}}
     */
    public ViplexResponse searchAllDevices(Duration timeout) {
        if (!initialized) {
            return ViplexResponse.failure(-1, "SDK 未初始化");
        }

        List<String> allDevices = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                library.nvSearchTerminalAsync((code, data) -> {
                    if (code == 0 && StringUtils.isNotEmpty(data)) {
                        allDevices.add(data);
                    }
                });
                // SDK 内部扫描 ~4s，等待窗口结束后释放
                Thread.sleep(timeout.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("searchAllDevices 调用异常", e);
            } finally {
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(timeout.toMillis() + 5000, TimeUnit.MILLISECONDS);
            if (!completed) {
                log.debug("searchAllDevices 等待超时，已收集 {} 个设备", allDevices.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ViplexResponse.failure(-1, "搜索被中断");
        }

        if (allDevices.isEmpty()) {
            return ViplexResponse.failure(-1, "未发现设备");
        }

        ObjectMapper mapper = JsonCustomMapper.get();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode arr = mapper.createArrayNode();
        for (String deviceJson : allDevices) {
            try {
                arr.add(mapper.readTree(deviceJson));
            } catch (Exception e) {
                log.warn("解析搜索设备 JSON 失败: {}", deviceJson, e);
            }
        }
        result.set("devices", arr);
        result.put("count", arr.size());
        return new ViplexResponse(0, result.toString());
    }

    // ═══ 内部工具 ═══

    /**
     * 内部阻塞登出（供 loginDevice 在切换凭据时使用）。
     * <p>不经过 executeSync，避免与当前 loginDevice 的 CountDownLatch 路径冲突。</p>
     */
    private void logoutDeviceInternalBlocking(String sn) {
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("sn", sn);
        String logoutParams = json.toString();
        CountDownLatch latch = new CountDownLatch(1);
        executor.submit(() -> {
            try {
                library.nvLogoutAsync(logoutParams, (code, data) -> latch.countDown());
            } catch (Exception e) {
                log.warn("logoutDeviceInternal 调用异常 SN={}", sn, e);
                latch.countDown();
            }
        });
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                log.debug("logoutDeviceInternal 等待超时 SN={}", sn);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        activeSessions.keySet().removeIf(k -> sessionKeyMatchesSn(k, sn));
        loginFailures.remove(sn);
        knownAccounts.remove(sn);
        if (authStore != null) authStore.remove(sn);
    }

    /**
     * 登录终端（默认屏体管理 loginType=0）。
     *
     * <p>SDK 登录回调 code=0 不代表成功，需检查 JSON 中的 {@code logined} 字段。</p>
     *
     * @param sn       设备序列号
     * @param username 用户名
     * @param password 密码
     * @param timeout  登录超时
     * @return ViplexResponse，success 取决于 logined 字段
     */
    public ViplexResponse loginDevice(String sn, String username, String password, Duration timeout) {
        return loginDevice(sn, username, password, timeout, LoginType.MANAGEMENT);
    }

    /**
     * 登录终端（指定 loginType）。
     *
     * @param sn        设备序列号
     * @param username  用户名
     * @param password  密码
     * @param timeout   登录超时
     * @param loginType 登录类型（{@link LoginType}）
     * @return ViplexResponse，success 取决于 logined 字段
     */
    public ViplexResponse loginDevice(String sn, String username, String password, Duration timeout, LoginType loginType) {
        if (!initialized) {
            return ViplexResponse.failure(-1, "SDK 未初始化");
        }

        String key = sessionKey(sn, loginType);

        // ── 快路径：已登录且凭据匹配，跳过 SDK 调用 ──
        ViplexCoreAccount existingSession = activeSessions.get(key);
        if (existingSession != null && existingSession.credentialsMatch(username, password)) {
            log.debug("ViplexCore 已登录，跳过重复登录 SN={} loginType={}", sn, loginType);
            return new ViplexResponse(0, "{\"logined\":true}");
        }

        // ── 已登录但凭据不同，先登出再重登 ──
        if (existingSession != null) {
            log.info("ViplexCore 凭据变更，先登出旧会话 SN={} loginType={} oldUser={} newUser={}",
                    sn, loginType, existingSession.getAccountId(), username);
            logoutDeviceInternalBlocking(sn);
        }

        // 防暴力登录：连续失败 3 次后冷却 60s
        LoginFailRecord record = loginFailures.compute(sn, (k, v) -> {
            if (v == null) return new LoginFailRecord();
            if (v.failures >= MAX_LOGIN_FAILURES) {
                Duration sinceCooldown = Duration.between(v.cooldownSince, Instant.now());
                if (sinceCooldown.compareTo(LOGIN_COOLDOWN) < 0) {
                    return v; // 仍在冷却期，不重置
                }
                return new LoginFailRecord(); // 冷却期满，重置
            }
            return v;
        });
        if (record.failures >= MAX_LOGIN_FAILURES) {
            long remainSec = LOGIN_COOLDOWN.getSeconds()
                    - Duration.between(record.cooldownSince, Instant.now()).getSeconds();
            return ViplexResponse.failure(-1,
                    String.format("登录锁定，请 %ds 后重试", Math.max(remainSec, 1)));
        }

        ObjectNode loginJson = JsonCustomMapper.get().createObjectNode();
        loginJson.put("sn", sn);
        loginJson.put("username", username != null ? username : ViplexCoreAccount.DEFAULT.getAccountId());
        loginJson.put("password", password != null ? password : ViplexCoreAccount.DEFAULT.getPassword());
        loginJson.put("loginType", loginType.getCode());
        loginJson.put("rememberPwd", 0);
        String loginParams = loginJson.toString();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();

        executor.submit(() -> {
            try {
                library.nvLoginAsync(loginParams, (code, data) -> {
                    resultRef.set(data != null ? data : "{}");
                    latch.countDown();
                });
            } catch (Exception e) {
                log.error("loginDevice 调用异常", e);
                latch.countDown();
            }
        });

        try {
            boolean ok = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("loginDevice 超时 ({}ms)", timeout.toMillis());
                return ViplexResponse.timeout();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ViplexResponse.failure(-1, "登录被中断");
        }

        String respStr = resultRef.get();
        if (StringUtils.isEmpty(respStr)) {
            recordLoginFailure(sn);
            return ViplexResponse.failure(-1, "登录无响应");
        }

        try {
            JsonNode resp = JsonCustomMapper.get().readTree(respStr);
            if (resp.has("logined") && resp.get("logined").asBoolean()) {
                // 登录成功，记录活跃会话（按 SN + loginType 区分）
                ViplexCoreAccount loggedIn = ViplexCoreAccount.builder()
                        .accountId(username).password(password).build();
                activeSessions.put(key, loggedIn);
                loginFailures.remove(sn);
                return new ViplexResponse(0, respStr);
            }
            // 登录失败，记录
            recordLoginFailure(sn);
            String errMsg = resp.has("errorDiscription")
                    ? resp.get("errorDiscription").asText()
                    : "登录被拒绝";
            return ViplexResponse.failure(-1, errMsg);
        } catch (Exception e) {
            recordLoginFailure(sn);
            return ViplexResponse.failure(-1, String.format("登录响应解析失败: %s", e.getMessage()));
        }
    }

    // ═══ 内部模型 ═══

    private void recordLoginFailure(String sn) {
        loginFailures.compute(sn, (k, v) -> {
            if (v == null) v = new LoginFailRecord();
            v.failures++;
            if (v.failures >= MAX_LOGIN_FAILURES) {
                v.cooldownSince = Instant.now();
                log.warn("[{}] 登录连续失败 {} 次，锁定 {}s", sn, v.failures,
                        LOGIN_COOLDOWN.getSeconds());
            }
            return v;
        });
    }

    @Override
    public ViplexResponse execute(SdkFunction function, String jsonParams, Duration timeout) {
        return executeSync(cb -> {
            switch (function) {
                case NV_GET_FIRMWARE_INFOS_ASYNC:
                    library.nvGetFirmwareInfosAsync(jsonParams, cb);
                    break;
                case NV_GET_DISPLAY_INFO_ASYNC:
                    library.nvGetDisplayInfoAsync(jsonParams, cb);
                    break;
                case NV_GET_CONFIGURATION_ASYNC:
                    library.nvGetconfigurationAsync(jsonParams, cb);
                    break;
                case NV_GET_PRODUCT_INFO_ASYNC:
                    library.nvGetProductInfoAsync(jsonParams, cb);
                    break;
                case NV_GET_VOLUME_ASYNC:
                    library.nvGetVolumeAsync(jsonParams, cb);
                    break;
                case NV_SET_VOLUME_ASYNC:
                    library.nvSetVolumeAsync(jsonParams, cb);
                    break;
                case NV_GET_SCREEN_BRIGHTNESS_ASYNC:
                    library.nvGetScreenBrightnessAsync(jsonParams, cb);
                    break;
                case NV_SET_SCREEN_BRIGHTNESS_ASYNC:
                    library.nvSetScreenBrightnessAsync(jsonParams, cb);
                    break;
                case NV_SET_SCREEN_POWER_STATE_ASYNC:
                    library.nvSetScreenPowerStateAsync(jsonParams, cb);
                    break;
                case NV_GET_SCREEN_POWER_STATE_ASYNC:
                    library.nvGetScreenPowerStateAsync(jsonParams, cb);
                    break;
                case NV_CALIBRATE_TIME_ASYNC:
                    library.nvCalibrateTimeAsync(jsonParams, cb);
                    break;
                case NV_SET_NET_TIMING_INFO_ASYNC:
                    library.nvSetNetTimingInfoAsync(jsonParams, cb);
                    break;
                case NV_GET_ETHERNET_INFO_ASYNC:
                    library.nvGetEthernetInfoAsync(jsonParams, cb);
                    break;
                case NV_SET_ETHERNET_INFO_ASYNC:
                    library.nvSetEthernetInfoAsync(jsonParams, cb);
                    break;
                case NV_GET_AP_NETWORK_OPEN_STATUS_ASYNC:
                    library.nvGetAPNetworkOpenStatusAsync(jsonParams, cb);
                    break;
                case NV_SET_AP_NETWORK_OPEN_STATUS_ASYNC:
                    library.nvSetAPNetworkOpenStatusAsync(jsonParams, cb);
                    break;
                case NV_SET_CUSTOM_RESOLUTION_ASYNC:
                    library.nvSetCustomResolutionAsync(jsonParams, cb);
                    break;
                case NV_GET_CURRENT_RESOLUTION_ASYNC:
                    library.nvGetCurrentResolutionAsync(jsonParams, cb);
                    break;
                case NV_GET_SUPPORTED_RESOLUTION_ASYNC:
                    library.nvGetSupportedResolutionAsync(jsonParams, cb);
                    break;
                case NV_SET_SCREEN_ATTRIBUTE_ASYNC:
                    library.nvSetScreenAttributeAsync(jsonParams, cb);
                    break;
                case NV_CREATE_PROGRAM_ASYNC:
                    library.nvCreateProgramAsync(jsonParams, cb);
                    break;
                case NV_SET_PAGE_PROGRAM_ASYNC:
                    library.nvSetPageProgramAsync(jsonParams, cb);
                    break;
                case NV_SET_PAGE_PROGRAMS_ASYNC:
                    library.nvSetPageProgramsAsync(jsonParams, cb);
                    break;
                case NV_MAKE_PROGRAM_ASYNC:
                    library.nvMakeProgramAsync(jsonParams, cb);
                    break;
                case NV_START_TRANSFER_PROGRAM_ASYNC:
                    library.nvStartTransferProgramAsync(jsonParams, cb);
                    break;
                case NV_GET_PROGRAM_INFO_ASYNC:
                    library.nvGetProgramInfoAsync(jsonParams, cb);
                    break;
                case NV_DELETE_PLAYLIST_ASYNC:
                    library.nvDeletePlayListAsync(jsonParams, cb);
                    break;
                case NV_START_PLAY_ASYNC:
                    library.nvStartPlayAsync(jsonParams, cb);
                    break;
                case NV_CLEAR_ALL_MEDIAS_ASYNC:
                    library.nvClearAllMediasAsync(jsonParams, cb);
                    break;
                case NV_DOWNLOAD_FILES_ASYNC:
                    library.nvDownLoadFilesAsync(jsonParams, cb);
                    break;
                case NV_QUERY_FILE_BY_TYPE_ASYNC:
                    library.nvQueryFileByTypeAsync(jsonParams, cb);
                    break;
                case NV_GET_FILE_MD5_ASYNC:
                    library.nvGetFileMD5Async(jsonParams, cb);
                    break;
                case NV_GET_TERMINAL_FONT_ASYNC:
                    library.nvGetTerminalFontAsync(jsonParams, cb);
                    break;
                case NV_DELETE_FONT_ASYNC:
                    library.nvDeleteFontAsync(jsonParams, cb);
                    break;
                case NV_UPDATE_FONT_ASYNC:
                    library.nvUpdateFontAsync(jsonParams, cb);
                    break;
                case NV_SET_REBOOT_TASK_ASYNC:
                    library.nvSetReBootTaskAsync(jsonParams, cb);
                    break;
                case NV_SEARCH_TERMINAL_ASYNC:
                    library.nvSearchTerminalAsync(cb);
                    break;
                default:
                    throw new IllegalArgumentException("未支持的 SDK 函数: " + function);
            }
        }, timeout);
    }

    // ═══ ViplexCoreChannel 实现 ═══

    @Override
    public ViplexResponse executeWithProgress(SdkFunction function, String jsonParams,
                                              Duration timeout, java.util.function.Predicate<ViplexResponse> isComplete) {
        return executeWithProgress(cb -> {
            switch (function) {
                case NV_START_TRANSFER_PROGRAM_ASYNC:
                    library.nvStartTransferProgramAsync(jsonParams, cb);
                    break;
                default:
                    throw new IllegalArgumentException("不支持进度回调的 SDK 函数: " + function);
            }
        }, timeout, isComplete);
    }

    @Override
    public ViplexResponse login(String sn, String username, String password, Duration timeout, LoginType loginType) {
        return loginDevice(sn, username, password, timeout, loginType);
    }

    /**
     * 登录失败记录（防暴力登录）
     */
    private static class LoginFailRecord {
        int failures;
        Instant cooldownSince;
    }

    // getKnownAccount、rememberAccount、isInitialized、isLoggedIn、searchDevice、searchAllDevices
    // 等已有方法签名已匹配接口，无需额外包装
}
