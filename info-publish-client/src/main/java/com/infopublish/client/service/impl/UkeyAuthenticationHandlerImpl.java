package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.config.AppConfig;
import com.infopublish.client.config.RemoteClientConfigLoader;
import com.infopublish.client.event.ProcessGuardEvent;
import com.infopublish.client.jna.VAuthSDKAdapter;
import com.infopublish.client.manager.UdpProxyRuleManager;
import com.infopublish.client.service.ArpBindService;
import com.infopublish.client.service.CertificateFileService;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.GatewayService;
import com.infopublish.client.service.MonitorPlatformClient;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.UkeyAuthenticationHandler;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ukey认证处理器
 *
 * 集成状态机，完整实现：
 * - UKey插入 -> 读取证书 -> 证书校验 -> 双向认证 -> 通知管控平台
 * - UKey拔出 -> 停止通道 -> 通知管控平台断开 -> 重置状态
 * - UKey重新插入 -> 自动热恢复
 */
@Slf4j
@Component
public class UkeyAuthenticationHandlerImpl implements UkeyAuthenticationHandler, ApplicationRunner, Ordered {

    private static final int STARTUP_SCAN_MAX_ATTEMPTS = 8;
    private static final long STARTUP_SCAN_INITIAL_DELAY_MILLIS = 200L;
    private static final long STARTUP_SCAN_RETRY_DELAY_MILLIS = 300L;
    private static final String PAGE_LAUNCHER_TASK_NAME = "InfoPublishClientPageLauncher";

    @Resource
    private VAuthSDKAdapter vAuthSDKAdapter;

    @Resource
    private MonitorPlatformClient monitorPlatformClient;

    @Resource
    private ClientAuthService clientAuthService;

    @Resource
    private CertificateFileService certificateFileService;

    @Resource
    private GatewayService gatewayService;

    @Resource
    private UkeyLifecycleManager lifecycleManager;

    @Resource
    private UdpProxyRuleManager proxyRuleManager;

    @Resource
    private ArpBindService arpBindService;

    @Resource
    private ProcessBindService processBindService;

    @Resource
    private AppConfig.ProcessBindProperties processBindProperties;

    @Resource
    private RemoteClientConfigLoader remoteClientConfigLoader;

    @Value("${vauth.client-cert-path:certs/client.cer}")
    private String clientCertPath;

    @Value("${server.port:7080}")
    private String serverPort;

    private volatile boolean startupScanStarted;
    private volatile boolean pendingProcessSelection;
    private volatile String pendingCertSerialNo;
    private volatile String pendingAuthToken;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 300;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!remoteClientConfigLoader.isConfigReady()) {
            log.warn("UKey startup scan delayed: remote config is not ready, reason={}",
                    remoteClientConfigLoader.getLastError());
            return;
        }
        startStartupScanOnce();
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void retryRemoteConfigAndStartupScan() {
        if (remoteClientConfigLoader.isConfigReady()) {
            startStartupScanOnce();
            return;
        }

        boolean ready = remoteClientConfigLoader.reload();
        if (!ready) {
            log.info("remote config is still not ready, skip UKey authentication retry, reason={}",
                    remoteClientConfigLoader.getLastError());
            return;
        }

        log.info("remote config is ready after retry, start UKey startup scan");
        startStartupScanOnce();
    }

    @PostConstruct
    public void init() {
        // 注册UKey事件监听器
        vAuthSDKAdapter.addUkeyEventListener(this::handleUkeyEvent);
        log.info("Ukey认证处理器已初始化（集成状态机）");

        // 启动后主动扫描：检测服务重启时已插入的 UKey
    }

    private void startStartupScanOnce() {
        if (startupScanStarted) {
            return;
        }
        synchronized (this) {
            if (startupScanStarted) {
                return;
            }
            startupScanStarted = true;
        }
        startStartupScan();
    }

    private void startStartupScan() {
        Thread scanThread = new Thread(() -> {
            sleepQuietly(STARTUP_SCAN_INITIAL_DELAY_MILLIS);
            for (int attempt = 1; attempt <= STARTUP_SCAN_MAX_ATTEMPTS; attempt++) {
                try {
                    String ukeyList = vAuthSDKAdapter.listUkeyInfos();
                    if (ukeyList != null && !ukeyList.trim().equals("[]") && !ukeyList.trim().isEmpty()) {
                        log.info("[启动扫描] 第 {} 次检测到已插入的 UKey，触发认证流程: {}", attempt, ukeyList);
                        onUkeyInserted("startup-scan");
                        return;
                    }
                    log.debug("[启动扫描] 第 {} 次未检测到 UKey，继续短间隔重试", attempt);
                } catch (Exception e) {
                    log.warn("[启动扫描] 第 {} 次扫描 UKey 失败: {}", attempt, e.getMessage());
                }

                if (attempt < STARTUP_SCAN_MAX_ATTEMPTS) {
                    sleepQuietly(STARTUP_SCAN_RETRY_DELAY_MILLIS);
                }
            }
            log.info("[启动扫描] 当前无 UKey 插入，等待插拔事件...");
        }, "ukey-startup-scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    /**
     * 处理UKey事件
     */
    private void handleUkeyEvent(int eventType, String deviceName, String message) {
        if (!remoteClientConfigLoader.isConfigReady()) {
            log.warn("ignore UKey event before remote config is ready: eventType={}, deviceName={}, reason={}",
                    eventType, deviceName, remoteClientConfigLoader.getLastError());
            return;
        }
        if (eventType == 1) {
            onUkeyInserted(deviceName);
        } else if (eventType == 2) {
            onUkeyRemoved(deviceName);
        }
    }

    @EventListener
    public void onProcessGuardEvent(ProcessGuardEvent event) {
        if (event == null) {
            return;
        }
        log.warn("进程守护触发 UKey 软件拔出: process={}, pid={}, source={}, reason={}",
                event.getProcessName(), event.getPid(), event.getSource(), event.getReason());
        log.warn("Process guard will keep UKey heartbeat and wait for Sigma rebinding, pid={}", event.getPid());
        onAuthorizedProcessInvalid(event);
    }

    private synchronized void onAuthorizedProcessInvalid(ProcessGuardEvent event) {
        String certSerialNo = lifecycleManager.getCurrentCertSerialNo();
        if (certSerialNo == null || certSerialNo.isEmpty()) {
            log.warn("Process guard event ignored because current certSerialNo is empty, pid={}", event.getPid());
            return;
        }

        UkeyLifecycleManager.State state = lifecycleManager.getCurrentState();
        if (!lifecycleManager.isAuthenticated()
                && state != UkeyLifecycleManager.State.PROCESS_SELECTION_REQUIRED) {
            log.warn("Process guard event ignored because UKey state is {}, pid={}", state, event.getPid());
            return;
        }

        try {
            if (lifecycleManager.isChannelActive()) {
                try {
                    Map<String, Object> stopResult = gatewayService.stopForwardChannel();
                    log.info("Forward channel stopped after process guard event: {}", stopResult);
                } catch (Exception e) {
                    log.error("Failed to stop forward channel after process guard event", e);
                }
            }

            try {
                proxyRuleManager.disableAllRulesOnUkeyRemoval();
                if (arpBindService.getStatus().isBound()) {
                    log.warn("Local link rules disabled, but ARP binding still exists after process guard event");
                } else {
                    log.info("Local link rules disabled after process guard event");
                }
            } catch (Exception e) {
                log.error("Failed to disable local link rules after process guard event", e);
            }

            try {
                processBindService.clearBinding();
            } catch (Exception e) {
                log.warn("Failed to clear process binding after process guard event: {}", e.getMessage());
            }

            markProcessSelectionPending(certSerialNo, "AUTH-TOKEN-" + System.currentTimeMillis());
            if (lifecycleManager.getCurrentState() != UkeyLifecycleManager.State.PROCESS_SELECTION_REQUIRED) {
                lifecycleManager.onProcessSelectionRequired();
            }
            openProcessSelectorPage();
            log.warn("Target process binding was lost; UKey remains authenticated and waits for Sigma binding. pid={}, reason={}",
                    event.getPid(), event.getReason());
        } catch (Exception e) {
            lifecycleManager.onError("process guard handling failed: " + e.getMessage());
            log.error("Process guard handling failed", e);
        }
    }

    // ========== 步骤1-4：UKey插入 -> 读取证书 -> 校验 -> 认证 ==========

    /**
     * 处理UKey插入事件
     */
    private synchronized void onUkeyInserted(String deviceName) {
        log.info("========================================");
        log.info("  UKey插入事件");
        log.info("  设备: {}", deviceName);
        log.info("========================================");

        try {
            // 1. 列出可用的UKey设备
            String ukeyList = vAuthSDKAdapter.listUkeyInfos();
            if (ukeyList == null || ukeyList.trim().equals("[]")) {
                lifecycleManager.onError("未检测到UKey设备");
                return;
            }
            log.info("检测到UKey设备列表: {}", ukeyList);

            // 2. 解析UKey信息
            String ukeyPath = extractUkeyPath(ukeyList);
            String certSerialNo = extractCertId(ukeyList);

            if (ukeyPath == null || certSerialNo == null) {
                lifecycleManager.onError("解析UKey信息失败");
                return;
            }

            // 状态机: IDLE -> UKEY_DETECTED
            lifecycleManager.onUkeyDetected(ukeyPath, certSerialNo);
            log.info("UKey已检测: path={}, certSerialNo={}", ukeyPath, certSerialNo);

            // 3. 向管控平台请求证书校验
            String certificateContent = extractCertificateContent(ukeyList);
            Map<String, Object> validateResult = monitorPlatformClient.validateCertificate(certSerialNo, certificateContent);

            boolean valid = (boolean) validateResult.getOrDefault("valid", false);
            if (!valid) {
                String errorMsg = (String) validateResult.getOrDefault("message", "证书校验失败");
                lifecycleManager.onError(errorMsg);
                log.error("证书校验失败: {}", errorMsg);
                return;
            }

            // 状态机: UKEY_DETECTED -> UKEY_VALIDATED
            lifecycleManager.onUkeyValidated();
            log.info("证书校验通过");

            // 4. 发起双向认证
            lifecycleManager.onAuthenticating();

            boolean authSuccess = clientAuthService.authenticateWithControlPlatform(ukeyPath);
            if (!authSuccess) {
                lifecycleManager.onError("双向认证失败");
                log.error("双向认证失败");
                return;
            }

            // 状态机: AUTHENTICATING -> AUTHENTICATED
            lifecycleManager.onAuthenticated();
            log.info("双向认证成功");

            // 5. 生成认证令牌。进程绑定成功前不通知平台上线、不恢复链路。
            String authToken = "AUTH-TOKEN-" + System.currentTimeMillis();

            // 6. 仅尝试绑定已经运行且校验通过的目标进程。未运行时进入人工选择流程。
            try {
                if (!processBindProperties.isEnabled()) {
                    log.info("[进程绑定] 功能未启用，认证成功后直接进入业务可用状态");
                    completeAuthenticatedFlow(certSerialNo, authToken);
                    return;
                }
                processBindService.bindCurrentPid();
                long pid = processBindService.getAuthorizedPid();
                log.info("进程绑定完成，授权PID: {}", pid);
                if (pid <= 0) {
                    markProcessSelectionPending(certSerialNo, authToken);
                    lifecycleManager.onProcessSelectionRequired();
                    openProcessSelectorPage();
                    log.warn("目标进程尚未绑定，已进入人工选择 Sigma 程序流程");
                    return;
                }
            } catch (Exception e) {
                lifecycleManager.onError("进程绑定异常: " + e.getMessage());
                clientAuthService.clearAuthentication();
                monitorPlatformClient.notifyClientDisconnected(certSerialNo, "process_bind_exception");
                log.error("进程绑定失败，拒绝恢复链路", e);
                return;
            }

            // 7. PID 绑定成功后通知平台上线并恢复链路规则。
            completeAuthenticatedFlow(certSerialNo, authToken);

            log.info("========================================");
            log.info("  UKey认证流程完成");
            log.info("  状态: {}", lifecycleManager.getCurrentState());
            log.info("========================================");

        } catch (Exception e) {
            log.error("UKey认证流程异常", e);
            lifecycleManager.onError("认证流程异常: " + e.getMessage());
        }
    }

    private void markProcessSelectionPending(String certSerialNo, String authToken) {
        processBindService.clearProcessCandidateCache();
        this.pendingProcessSelection = true;
        this.pendingCertSerialNo = certSerialNo;
        this.pendingAuthToken = authToken;
    }

    private void clearProcessSelectionPending() {
        this.pendingProcessSelection = false;
        this.pendingCertSerialNo = null;
        this.pendingAuthToken = null;
        processBindService.clearProcessCandidateCache();
    }

    public boolean isProcessSelectionPending() {
        return pendingProcessSelection;
    }

    public synchronized Map<String, Object> completePendingProcessSelection() {
        Map<String, Object> result = new HashMap<>();
        if (!pendingProcessSelection) {
            result.put("completed", false);
            result.put("message", "当前没有等待选择的 UKey 认证流程，拒绝启动");
            return result;
        }

        long pid = processBindService.getAuthorizedPid();
        if (pid <= 0) {
            result.put("completed", false);
            result.put("message", "Sigma 程序尚未完成 PID 绑定");
            return result;
        }

        String certSerialNo = pendingCertSerialNo;
        String authToken = pendingAuthToken;
        try {
            lifecycleManager.onProcessBound();
            completeAuthenticatedFlow(certSerialNo, authToken);
            result.put("completed", true);
            result.put("authorizedPid", pid);
            result.put("message", "进程绑定成功，链路已恢复");
            return result;
        } catch (Exception e) {
            lifecycleManager.onError("进程选择完成流程异常: " + e.getMessage());
            clientAuthService.clearAuthentication();
            if (certSerialNo != null) {
                try {
                    monitorPlatformClient.notifyClientDisconnected(certSerialNo, "process_selection_complete_exception");
                } catch (Exception notifyError) {
                    log.warn("通知管控平台进程选择失败断开异常: {}", notifyError.getMessage());
                }
            }
            clearProcessSelectionPending();
            result.put("completed", false);
            result.put("message", "进程绑定成功，但恢复链路失败: " + e.getMessage());
            return result;
        }
    }

    private void completeAuthenticatedFlow(String certSerialNo, String authToken) {
        monitorPlatformClient.notifyClientAuthenticated(certSerialNo, authToken);

        try {
            proxyRuleManager.reactivateRulesOnUkeyAuthenticated();
            log.info("链路规则已重新激活，ARP 绑定已恢复");
        } catch (Exception e) {
            log.warn("恢复链路规则失败: {}", e.getMessage());
        }

        clearProcessSelectionPending();
    }

    private void openProcessSelectorPage() {
        String url = "http://127.0.0.1:" + serverPort + "/#process-selector";
        if (runInteractivePageLauncherTask(url)) {
            return;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("已打开 Sigma 程序选择页面: {}", url);
                return;
            }
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            log.info("已请求系统打开 Sigma 程序选择页面: {}", url);
        } catch (Exception e) {
            log.warn("自动打开 Sigma 程序选择页面失败，请手动访问 {}，原因: {}", url, e.getMessage());
        }
    }

    // ========== 步骤5：UKey拔出 -> 断连 ==========

    /**
     * 处理UKey拔出事件
     */
    private synchronized void onUkeyRemoved(String deviceName) {
        log.warn("========================================");
        log.warn("  UKey拔出事件: {}", deviceName);
        log.warn("========================================");

        String certSerialNo = lifecycleManager.getCurrentCertSerialNo();
        clientAuthService.clearAuthentication();

        try {
            // 1. 如果有活跃通道，先停止转发通道
            if (lifecycleManager.isChannelActive()) {
                log.info("正在停止转发通道...");
                try {
                    Map<String, Object> stopResult = gatewayService.stopForwardChannel();
                    log.info("停止转发通道结果: {}", stopResult);
                } catch (Exception e) {
                    log.error("停止转发通道失败", e);
                }
            }

            // 2. 禁用本地所有链路规则 + 解绑 ARP（UKey 不在 = 不允许数据发送）
            try {
                proxyRuleManager.disableAllRulesOnUkeyRemoval();
                // 检查 ARP 是否真正解除
                if (arpBindService.getStatus().isBound()) {
                    log.warn("⚠ 本地链路规则已禁用，但 ARP 绑定解除失败（可能未以管理员身份运行）");
                } else {
                    log.info("本地链路规则已全部禁用，ARP 绑定已解除");
                }
            } catch (Exception e) {
                log.error("禁用链路规则失败", e);
            }

            // 清除进程绑定
            try {
                processBindService.clearBinding();
                log.info("进程绑定已清除");
            } catch (Exception e) {
                log.warn("清除进程绑定失败: {}", e.getMessage());
            }
            clearProcessSelectionPending();
            // 3. 通知管控平台：断开连接
            if (certSerialNo != null) {
                try {
                    monitorPlatformClient.notifyClientDisconnected(certSerialNo, "ukey_removed");
                    log.info("已通知管控平台断开连接");
                } catch (Exception e) {
                    log.error("通知管控平台断开失败", e);
                }
            }

            // 4. 清除本地认证状态
            clientAuthService.clearAuthentication();

        } catch (Exception e) {
            log.error("UKey拔出处理异常", e);
        } finally {
            // 5. 重置状态机到IDLE
            lifecycleManager.reset("UKey已拔出");
            log.info("状态已重置为IDLE，等待UKey重新插入");
        }
    }

    // ========== 步骤7：主动退出/注销 ==========

    /**
     * 主动退出/注销
     * B/S页面点击退出时调用
     */
    public synchronized void logout() {
        log.info("========================================");
        log.info("  执行主动退出/注销");
        log.info("========================================");

        String certSerialNo = lifecycleManager.getCurrentCertSerialNo();
        clientAuthService.clearAuthentication();

        try {
            // 1. 停止转发通道
            if (lifecycleManager.isChannelActive()) {
                gatewayService.stopForwardChannel();
                log.info("转发通道已停止");
            }

            // 2. 通知管控平台注销
            if (certSerialNo != null) {
                monitorPlatformClient.notifyClientDisconnected(certSerialNo, "logout");
                log.info("已通知管控平台注销");
            }

            // 3. 清除认证状态
            clientAuthService.clearAuthentication();
            clearProcessSelectionPending();

        } catch (Exception e) {
            log.error("退出流程异常", e);
        } finally {
            // 4. 重置状态机
            lifecycleManager.reset("用户主动退出");
            log.info("已退出，后台服务继续运行，等待下次UKey插入");
        }
    }

    // ========== 工具方法 ==========

    private boolean runInteractivePageLauncherTask(String url) {
        List<String> command = Arrays.asList("schtasks", "/Run", "/TN", PAGE_LAUNCHER_TASK_NAME);
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = readProcessOutput(process);
                log.warn("触发用户态页面打开任务超时，将回退到当前会话打开。task={}, url={}, output={}",
                        PAGE_LAUNCHER_TASK_NAME, url, output);
                return false;
            }
            String output = readProcessOutput(process);
            if (process.exitValue() == 0) {
                log.info("已通过用户态计划任务请求打开 Sigma 程序选择页面: task={}, url={}",
                        PAGE_LAUNCHER_TASK_NAME, url);
                return true;
            }
            log.warn("用户态页面打开任务触发失败，将回退到当前会话打开。task={}, exitCode={}, output={}",
                    PAGE_LAUNCHER_TASK_NAME, process.exitValue(), output);
            return false;
        } catch (Exception e) {
            log.warn("用户态页面打开任务触发异常，将回退到当前会话打开。task={}, reason={}",
                    PAGE_LAUNCHER_TASK_NAME, e.getMessage());
            return false;
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(' ');
                }
                output.append(line.trim());
            }
        }
        return output.toString();
    }

    private String extractUkeyPath(String ukeyJson) {
        try {
            JSONArray array = JSON.parseArray(ukeyJson);
            if (array != null && !array.isEmpty()) {
                JSONObject first = array.getJSONObject(0);
                return first.getString("path");
            }
        } catch (Exception e) {
            log.error("提取UKey路径失败", e);
        }
        return null;
    }

    private String extractCertId(String ukeyJson) {
        try {
            JSONArray array = JSON.parseArray(ukeyJson);
            if (array != null && !array.isEmpty()) {
                JSONObject first = array.getJSONObject(0);
                return first.getString("cerId");
            }
        } catch (Exception e) {
            log.error("提取证书ID失败", e);
        }
        return null;
    }

    private String extractCertificateContent(String ukeyJson) {
        // 从本地配置的 client.cer 文件读取客户端证书内容
        // 该证书用于管控平台校验和服务端 ParseAuthInfo
        String currentClientCertPath = clientAuthService.getClientCerPath();
        if (currentClientCertPath == null || currentClientCertPath.trim().isEmpty()) {
            currentClientCertPath = clientCertPath;
        }
        try {
            String certContent = certificateFileService.loadCertificateContent(currentClientCertPath);
            log.debug("成功读取客户端证书内容: {}", currentClientCertPath);
            return certContent;
        } catch (RuntimeException e) {
            log.warn("读取客户端证书文件失败 [{}]: {}", currentClientCertPath, e.getMessage());
            return null;
        }
    }
}
