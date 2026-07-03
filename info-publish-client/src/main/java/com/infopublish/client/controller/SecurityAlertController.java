package com.infopublish.client.controller;

import com.infopublish.client.common.Result;
import com.infopublish.client.entity.dto.EtwTrafficEventDTO;
import com.infopublish.client.service.EtwTrafficAccountingService;
import com.infopublish.client.service.EtwTrafficCollectorService;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.TrafficMonitorService;
import com.infopublish.client.service.TransparentUdpProxyService;
import com.infopublish.client.service.UkeyAuthenticationHandler;
import com.infopublish.client.service.WindivertMonitorService;
import com.infopublish.client.service.WindivertShadowService;
import com.infopublish.client.service.ClientRelayFileSignatureService;
import com.infopublish.client.service.ContentPreAuditService;
import com.infopublish.client.service.SecurePublishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全告警 Controller
 *
 * <p>提供以下接口供前端页面轮询：
 * <ul>
 *   <li>GET  /security/alert/query      - 查询当前是否有未读告警（含非法来源 IP:Port）</li>
 *   <li>POST /security/alert/dismiss    - 用户确认后清除告警</li>
 *   <li>GET  /security/process/status   - 查询当前进程绑定状态及白名单端点</li>
 *   <li>POST /security/process/refresh  - 手动刷新进程 PID 绑定及 IP:Port 白名单</li>
 *   <li>GET  /security/process/endpoints - 查询当前合法来源白名单（调试用）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/security")
public class SecurityAlertController {

    @Value("${security.gateway-source-validation-enabled:false}")
    private boolean gatewaySourceValidationEnabled;

    @Resource
    private TrafficMonitorService trafficMonitorService;

    @Resource
    private ProcessBindService processBindService;

    @Resource
    private UkeyAuthenticationHandler ukeyAuthenticationHandler;

    @Resource
    private EtwTrafficAccountingService etwTrafficAccountingService;

    @Resource
    private EtwTrafficCollectorService etwTrafficCollectorService;

    @Resource
    private WindivertMonitorService windivertMonitorService;

    @Resource
    private WindivertShadowService windivertShadowService;

    @Resource
    private TransparentUdpProxyService transparentUdpProxyService;

    @Resource
    private ClientRelayFileSignatureService clientRelayFileSignatureService;

    @Resource
    private ContentPreAuditService contentPreAuditService;

    @Resource
    private SecurePublishService securePublishService;

    // ========== 告警接口 ==========

    /**
     * 查询当前是否有未读告警
     *
     * @return {hasAlert: bool, message: String, alertTime: String, ratio: double}
     */
    @GetMapping("/alert/query")
    public Result<Map<String, Object>> queryAlert() {
        Map<String, Object> data = new HashMap<>();
        TrafficMonitorService.AlertMessage alert = trafficMonitorService.getAlertMessage();

        if (alert != null) {
            data.put("hasAlert", true);
            data.put("message", alert.getMessage());
            data.put("alertTime", formatTime(alert.getAlertTime()));
            data.put("sourceIp", alert.getSourceIp());
            data.put("sourcePort", alert.getSourcePort());
        } else {
            data.put("hasAlert", false);
            data.put("message", null);
            data.put("alertTime", null);
            data.put("sourceIp", null);
            data.put("sourcePort", null);
        }

        return Result.ok(data);
    }

    /**
     * 用户确认告警后清除（点击"我知道了"按钮调用）
     */
    @PostMapping("/alert/dismiss")
    public Result<Void> dismissAlert() {
        trafficMonitorService.clearAlert();
        log.info("[安全告警] 用户已确认并清除告警");
        return Result.ok();
    }

    // ========== 进程绑定接口 ==========

    /**
     * 查询当前进程绑定状态及白名单端点数
     *
     * @return {authorizedPid: long, bound: bool, endpointCount: int}
     */
    @GetMapping("/process/status")
    public Result<Map<String, Object>> processStatus() {
        Map<String, Object> data = new HashMap<>();
        long pid = processBindService.getAuthorizedPid();
        data.put("authorizedPid", pid);
        data.put("bound", pid > 0);
        data.put("endpointCount", processBindService.getAuthorizedEndpoints().size());
        data.put("processSelectionPending", ukeyAuthenticationHandler.isProcessSelectionPending());
        return Result.ok(data);
    }

    /**
     * 查询当前合法来源白名单（调试用）
     *
     * @return {authorizedPid: long, endpoints: Set<String>}
     */
    @GetMapping("/process/endpoints")
    public Result<Map<String, Object>> processEndpoints() {
        Map<String, Object> data = new HashMap<>();
        data.put("authorizedPid", processBindService.getAuthorizedPid());
        data.put("endpoints", processBindService.getAuthorizedEndpoints());
        return Result.ok(data);
    }

    @GetMapping("/process/candidates")
    public Result<ProcessBindService.ProcessCandidatesResult> processCandidates() {
        return Result.ok(processBindService.scanProcessCandidates());
    }

    @PostMapping("/process/pick-candidate")
    public Result<Map<String, Object>> pickProcessCandidate() {
        if (!ukeyAuthenticationHandler.isProcessSelectionPending()) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("message", "当前没有等待选择的 UKey 认证流程，拒绝添加");
            data.put("authorizedPid", processBindService.getAuthorizedPid());
            return Result.error(409, "当前没有等待选择的 UKey 认证流程，拒绝添加", data);
        }

        try {
            ProcessBindService.ProcessSelectionResult selectionResult =
                    processBindService.requestProcessCandidatePicker();
            Map<String, Object> data = new HashMap<>();
            data.put("success", selectionResult.isSuccess());
            data.put("authorizedPid", selectionResult.getAuthorizedPid());
            data.put("message", selectionResult.getMessage());
            data.put("candidate", selectionResult.getCandidate());

            if (!selectionResult.isSuccess()) {
                return Result.error(400, selectionResult.getMessage(), data);
            }
            return Result.ok(selectionResult.getMessage(), data);
        } catch (Exception e) {
            log.warn("[进程选择] 添加 Sigma 候选失败: {}", e.getMessage());
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("message", "添加 Sigma 程序失败");
            data.put("authorizedPid", processBindService.getAuthorizedPid());
            return Result.error(500, "添加 Sigma 程序失败", data);
        }
    }

    @PostMapping("/process/candidate-from-helper")
    public Result<Map<String, Object>> addProcessCandidateFromHelper(@RequestBody Map<String, Object> body,
                                                                     HttpServletRequest request) {
        if (!isLocalRequest(request)) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("message", "拒绝非本机 helper 请求");
            return Result.error(403, "拒绝非本机 helper 请求", data);
        }
        if (!ukeyAuthenticationHandler.isProcessSelectionPending()) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("message", "当前没有等待选择的 UKey 认证流程，拒绝添加");
            data.put("authorizedPid", processBindService.getAuthorizedPid());
            return Result.error(409, "当前没有等待选择的 UKey 认证流程，拒绝添加", data);
        }

        Object pathObj = body != null ? body.get("path") : null;
        String selectedPath = pathObj != null ? String.valueOf(pathObj) : null;
        ProcessBindService.ProcessSelectionResult selectionResult =
                processBindService.addProcessCandidateFromHelper(selectedPath);

        Map<String, Object> data = new HashMap<>();
        data.put("success", selectionResult.isSuccess());
        data.put("authorizedPid", selectionResult.getAuthorizedPid());
        data.put("message", selectionResult.getMessage());
        data.put("candidate", selectionResult.getCandidate());
        if (!selectionResult.isSuccess()) {
            return Result.error(400, selectionResult.getMessage(), data);
        }
        return Result.ok(selectionResult.getMessage(), data);
    }

    @PostMapping("/process/select-and-start")
    public Result<Map<String, Object>> selectAndStartProcess(@RequestBody Map<String, Object> body) {
        if (!ukeyAuthenticationHandler.isProcessSelectionPending()) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("message", "当前没有等待选择的 UKey 认证流程，拒绝启动");
            data.put("authorizedPid", processBindService.getAuthorizedPid());
            return Result.error(409, "当前没有等待选择的 UKey 认证流程，拒绝启动", data);
        }

        Object candidateIdObj = body != null ? body.get("candidateId") : null;
        String candidateId = candidateIdObj != null ? String.valueOf(candidateIdObj) : null;
        if (candidateId == null || candidateId.trim().isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("message", "非法 Sigma 程序或选择已过期");
            data.put("authorizedPid", processBindService.getAuthorizedPid());
            return Result.error(400, "非法 Sigma 程序或选择已过期", data);
        }

        ProcessBindService.ProcessSelectionResult selectionResult =
                processBindService.selectAndStartCandidate(candidateId);

        Map<String, Object> data = new HashMap<>();
        data.put("success", selectionResult.isSuccess());
        data.put("authorizedPid", selectionResult.getAuthorizedPid());
        data.put("message", selectionResult.getMessage());
        data.put("candidate", selectionResult.getCandidate());

        if (!selectionResult.isSuccess()) {
            return Result.error(400, selectionResult.getMessage(), data);
        }

        Map<String, Object> completion = ukeyAuthenticationHandler.completePendingProcessSelection();
        data.put("completion", completion);
        if (!Boolean.TRUE.equals(completion.get("completed"))) {
            String message = String.valueOf(completion.get("message"));
            data.put("success", false);
            data.put("message", message);
            return Result.error(500, message, data);
        }

        return Result.ok(selectionResult.getMessage(), data);
    }

    /**
     * 手动刷新进程 PID 绑定
     *
     * <p>大屏控制软件重启后，运维人员可通过此接口主动触发重新查找 PID，
     * 无需重启客户端服务。
     */
    @PostMapping("/process/refresh")
    public Result<Map<String, Object>> refreshProcessBind() {
        log.info("[安全告警] 收到手动刷新进程绑定请求");
        processBindService.refreshBinding();

        Map<String, Object> data = new HashMap<>();
        long newPid = processBindService.getAuthorizedPid();
        data.put("authorizedPid", newPid);
        data.put("bound", newPid > 0);
        data.put("endpointCount", processBindService.getAuthorizedEndpoints().size());
        data.put("message", newPid > 0
                ? "进程绑定刷新成功，授权 PID=" + newPid + "，白名单端点数=" + processBindService.getAuthorizedEndpoints().size()
                : "未找到已运行且校验通过的目标进程，来源校验将保持拒绝");
        return Result.ok(data);
    }

    // ========== 网关来源校验接口（发布网关同步调用） ==========

    /**
     * 供发布网关同步调用：校验来源 IP:Port 是否合法
     *
     * <p>发布网关在收到每个 UDP 数据包后，立即将来源 IP:Port 以 HTTP POST 发到此接口。
     * 客户端优先查询 WinDivert PID 缓存，未命中时回退现有 UDP 端口归属查询，同步返回校验结果。
     *
     * <p>请求体：{"ip": "192.168.1.100", "port": 52341}
     * <p>响应体：{"authorized": true/false, "endpoint": "IP:Port", "reason": "..."}
     *
     * @param body 包含 ip 和 port 字段的 JSON 请求体
     */
    @PostMapping("/validate-source")
    public Result<Map<String, Object>> validateSource(@RequestBody Map<String, Object> body) {
        String ip   = (String) body.get("ip");
        Object portObj = body.get("port");
        if (ip == null || portObj == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("authorized", false);
            err.put("reason", "请求参数缺失：需要 ip 和 port 字段");
            return Result.ok(err);
        }
        int port;
        try {
            port = Integer.parseInt(portObj.toString());
        } catch (NumberFormatException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("authorized", false);
            err.put("reason", "port 格式非法：" + portObj);
            return Result.ok(err);
        }

        log.info("[来源校验] 网关请求校验来源: {}:{}", ip, port);

        Map<String, Object> data = new HashMap<>();
        data.put("endpoint", ip + ":" + port);
        if (!gatewaySourceValidationEnabled) {
            data.put("authorized", true);
            data.put("reasonCode", "SOURCE_VALIDATION_DISABLED");
            data.put("reason", "gateway per-packet source validation disabled");
            data.put("integrityChecked", false);
            log.debug("[source-validation] gateway callback validation disabled, allow {}:{}", ip, port);
            return Result.ok(data);
        }

        ProcessBindService.ProcessIntegrityResult integrityResult =
                processBindService.verifyAuthorizedProcessIntegrity("validate-source");
        data.put("integrityChecked", integrityResult.isChecked());
        if (!integrityResult.isValid()) {
            data.put("authorized", false);
            data.put("reasonCode", integrityResult.getReasonCode());
            data.put("reason", integrityResult.getReason());
            log.warn("[来源校验] 进程完整性复核失败，拒绝来源: {}:{}，reasonCode={}",
                    ip, port, integrityResult.getReasonCode());
            return Result.ok(data);
        }

        boolean authorized = trafficMonitorService.checkAndRecordSource(ip, port);
        data.put("authorized", authorized);
        data.put("reasonCode", authorized ? "OK" : "UDP_PORT_NOT_AUTHORIZED");
        data.put("reason", authorized
                ? "IP:Port 在 PID=" + processBindService.getAuthorizedPid() + " 的合法连接白名单中"
                : "IP:Port 不在白名单中，疑似非法来源");
        log.info("[来源校验] 校验结果: {} → {}", ip + ":" + port, authorized ? "合法" : "非法");
        return Result.ok(data);
    }

    // ========== 工具方法（测试用） ==========

    /**
     * 《仅测试用》模拟收到一条数据，手动触发来源合法性校验
     *
     * <p>用于验证白名单校验逻辑：
     * - ip/port 在白名单中：返回 authorized=true
     * - ip/port 不在白名单中：返回 authorized=false 并生成告警
     *
     * @param ip   模拟来源 IP
     * @param port 模拟来源端口
     */
    /**
     * Receives outbound UDP events reported by the local ETW collector.
     */
    @PostMapping("/traffic/etw/event")
    public Result<Map<String, Object>> recordEtwTraffic(@RequestBody EtwTrafficEventDTO event) {
        return Result.ok(etwTrafficAccountingService.recordOutboundEvent(event));
    }

    /**
     * Queries local ETW traffic stats for gateway reconciliation.
     */
    @GetMapping("/traffic/stats")
    public Result<Map<String, Object>> queryEtwTrafficStats(@RequestParam long windowStart,
                                                            @RequestParam long windowEnd,
                                                            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(etwTrafficAccountingService.queryStats(windowStart, windowEnd, limit));
    }

    /**
     * Clears local ETW traffic stats.
     */
    @PostMapping("/traffic/clear")
    public Result<Void> clearEtwTrafficStats() {
        etwTrafficAccountingService.clear();
        return Result.ok();
    }

    @GetMapping("/traffic/collector/status")
    public Result<Map<String, Object>> etwCollectorStatus() {
        return Result.ok(etwTrafficCollectorService.getStatus());
    }

    @GetMapping("/windivert/status")
    public Result<Map<String, Object>> windivertStatus() {
        return Result.ok(windivertMonitorService.getStatus());
    }

    @PostMapping("/windivert/clear")
    public Result<Void> clearWindivertCache() {
        windivertMonitorService.clear();
        return Result.ok();
    }

    @GetMapping("/windivert/shadow/status")
    public Result<Map<String, Object>> windivertShadowStatus() {
        return Result.ok(windivertShadowService.getStatus());
    }

    @GetMapping("/windivert/shadow/events")
    public Result<List<WindivertShadowService.ShadowPacketEvent>> windivertShadowEvents(
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(windivertShadowService.getRecentEvents(limit));
    }

    @PostMapping("/windivert/shadow/clear")
    public Result<Void> clearWindivertShadow() {
        windivertShadowService.clear();
        return Result.ok();
    }

    @GetMapping("/windivert/proxy/status")
    public Result<Map<String, Object>> transparentProxyStatus() {
        return Result.ok(transparentUdpProxyService.getStatus());
    }

    @PostMapping("/windivert/proxy/clear")
    public Result<Void> clearTransparentProxyStats() {
        transparentUdpProxyService.clear();
        return Result.ok();
    }

    @GetMapping("/relay/file-signature/status")
    public Result<Map<String, Object>> relayFileSignatureStatus() {
        return Result.ok(clientRelayFileSignatureService.getStatus());
    }

    @PostMapping("/relay/file-signature/clear")
    public Result<Void> clearRelayFileSignature() {
        clientRelayFileSignatureService.clear();
        return Result.ok();
    }

    @GetMapping("/content-audit/status")
    public Result<Map<String, Object>> contentAuditStatus() {
        return Result.ok(contentPreAuditService.getStatus());
    }

    @PostMapping("/content-audit/mode")
    public Result<Map<String, Object>> updateContentAuditMode(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return Result.ok(contentPreAuditService.updateRuntimeSettings(body));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/content-audit/items")
    public Result<List<com.infopublish.client.entity.dto.ContentAuditItem>> contentAuditItems(
            @RequestParam(required = false) String status) {
        return Result.ok(contentPreAuditService.listItems(status));
    }

    @GetMapping("/content-audit/items/{auditId}")
    public Result<com.infopublish.client.entity.dto.ContentAuditItem> contentAuditItem(
            @PathVariable String auditId) {
        com.infopublish.client.entity.dto.ContentAuditItem item = contentPreAuditService.getItem(auditId);
        if (item == null) {
            return Result.error(404, "not found");
        }
        return Result.ok(item);
    }

    @PostMapping("/content-audit/items/{auditId}/approve")
    public Result<Map<String, Object>> approveContentAuditItem(@PathVariable String auditId,
                                                               @RequestBody(required = false) Map<String, Object> body) {
        String operator = body != null ? String.valueOf(body.getOrDefault("operator", "manual")) : "manual";
        boolean success = contentPreAuditService.approve(auditId, operator);
        if (!success) {
            return Result.error(400, "approve failed");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("auditId", auditId);
        data.put("approved", true);
        return Result.ok(data);
    }

    @PostMapping("/content-audit/items/{auditId}/reject")
    public Result<Map<String, Object>> rejectContentAuditItem(@PathVariable String auditId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        String operator = body != null ? String.valueOf(body.getOrDefault("operator", "manual")) : "manual";
        boolean success = contentPreAuditService.reject(auditId, operator);
        if (!success) {
            return Result.error(400, "reject failed");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("auditId", auditId);
        data.put("rejected", true);
        data.put("operator", operator);
        return Result.ok(data);
    }

    @PostMapping("/content-audit/clear")
    public Result<Void> clearContentAudit() {
        contentPreAuditService.clear();
        return Result.ok();
    }

    @GetMapping("/secure-publish/status")
    public Result<Map<String, Object>> securePublishStatus() {
        return Result.ok(securePublishService.getStatus());
    }

    @GetMapping("/secure-publish/items")
    public Result<List<com.infopublish.client.entity.dto.SecurePublishItem>> securePublishItems() {
        return Result.ok(securePublishService.listItems());
    }

    @PostMapping("/secure-publish/scan")
    public Result<Map<String, Object>> scanSecurePublish() {
        return Result.ok(securePublishService.scanNow());
    }

    @PostMapping("/secure-publish/test-package")
    public Result<Map<String, Object>> createSecurePublishTestPackage(
            @RequestBody(required = false) Map<String, Object> body) {
        String payloadPath = body == null || body.get("payloadPath") == null ? null : String.valueOf(body.get("payloadPath"));
        String outputDir = body == null || body.get("outputDir") == null ? null : String.valueOf(body.get("outputDir"));
        return Result.ok(securePublishService.createTestPackage(payloadPath, outputDir));
    }

    @PostMapping("/secure-publish/test-keypair")
    public Result<Map<String, Object>> createSecurePublishTestKeyPair(
            @RequestBody(required = false) Map<String, Object> body) {
        boolean overwrite = body != null
                && body.get("overwrite") != null
                && Boolean.parseBoolean(String.valueOf(body.get("overwrite")));
        return Result.ok(securePublishService.createTestKeyPair(overwrite));
    }

    @GetMapping("/secure-publish/signer/status")
    public Result<Map<String, Object>> securePublishSignerStatus() {
        return Result.ok(securePublishService.getSignerStatus());
    }

    @PostMapping("/secure-publish/signer/keypair")
    public Result<Map<String, Object>> createSecurePublishSignerKeyPair(
            @RequestBody(required = false) Map<String, Object> body) {
        boolean overwrite = body != null
                && body.get("overwrite") != null
                && Boolean.parseBoolean(String.valueOf(body.get("overwrite")));
        return Result.ok(securePublishService.createSignerKeyPair(overwrite));
    }

    @PostMapping("/secure-publish/signer/package")
    public Result<Map<String, Object>> createSecurePublishSignerPackage(
            @RequestBody(required = false) Map<String, Object> body) {
        String payloadPath = body == null || body.get("payloadPath") == null ? null : String.valueOf(body.get("payloadPath"));
        String outputDir = body == null || body.get("outputDir") == null ? null : String.valueOf(body.get("outputDir"));
        return Result.ok(securePublishService.signPackage(payloadPath, outputDir));
    }

    @PostMapping("/secure-publish/signer/upload")
    public Result<Map<String, Object>> uploadSecurePublishSignerPackage(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "relativePaths", required = false) String[] relativePaths,
            @RequestParam(value = "outputDir", required = false) String outputDir) {
        return Result.ok(securePublishService.signUploadedFiles(files, relativePaths, outputDir));
    }

    @PostMapping("/secure-publish/signer/scan")
    public Result<Map<String, Object>> scanSecurePublishSignerInput() {
        return Result.ok(securePublishService.scanSignerInput());
    }

    @PostMapping("/traffic/collector/start")
    public Result<Map<String, Object>> startEtwCollector() {
        return Result.ok(etwTrafficCollectorService.startCollector());
    }

    @PostMapping("/traffic/collector/stop")
    public Result<Map<String, Object>> stopEtwCollector() {
        return Result.ok(etwTrafficCollectorService.stopCollector());
    }

    @PostMapping("/test/check-source")
    public Result<Map<String, Object>> testCheckSource(
            @org.springframework.web.bind.annotation.RequestParam String ip,
            @org.springframework.web.bind.annotation.RequestParam int port) {
        log.info("[安全告警-测试] 模拟来源校验: {}:{}", ip, port);
        boolean authorized = trafficMonitorService.checkAndRecordSource(ip, port);
        Map<String, Object> data = new HashMap<>();
        data.put("authorized", authorized);
        data.put("sourceIp", ip);
        data.put("sourcePort", port);
        data.put("authorizedEndpoints", processBindService.getAuthorizedEndpoints());
        data.put("result", authorized ? "合法来源，通过校验" : "疑似非法来源，已生成告警");
        return Result.ok(data);
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String remoteAddr = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(remoteAddr);
    }
}
