package com.monitorplatform.ukey.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.util.EncryptUtil;
import com.monitorplatform.common.util.RedisUtil;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.ukey.config.JwtUtil;
import com.monitorplatform.ukey.config.TokenStore;
import com.monitorplatform.ukey.entity.UkeyCertificate;
import com.monitorplatform.ukey.entity.dto.CertValidateRequestDTO;
import com.monitorplatform.ukey.entity.dto.CertValidateResponseDTO;
import com.monitorplatform.ukey.feign.RoleFeignClient;
import com.monitorplatform.ukey.service.LoginSecurityService;
import com.monitorplatform.ukey.service.UkeyCertificateService;
import com.monitorplatform.ukey.service.VAuthAuthServerService;
import com.monitorplatform.ukey.util.PasswordValidator;
import com.monitorplatform.ukey.websocket.UkeyStatusPushService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * UKey证书管理Controller
 * 提供证书校验、注册、管理等接口
 */
@Slf4j
@RestController
@RequestMapping("/cert")
public class UkeyCertificateController {

    @Resource
    private UkeyCertificateService ukeyCertificateService;

    @Resource
    private TokenStore tokenStore;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private VAuthAuthServerService vAuthAuthServerService;

    @Resource
    private UkeyStatusPushService ukeyStatusPushService;

    /**
     * 登录安全服务（实现登录失败锁定机制）
     */
    @Resource
    private LoginSecurityService loginSecurityService;

    @Resource
    private RoleFeignClient roleFeignClient;

    /**
     * 心跳超时阈值（秒），超过此时间未收到心跳则认为客户端已宕机
     */
    @Value("${client.heartbeat.timeout-seconds:15}")
    private int heartbeatTimeoutSeconds;

    /**
     * Redis 黑名单 key 前缀，value = jti
     */
    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    /**
     * 管理员账号（必须从配置文件读取，禁止硬编码）
     */
    @Value("${ukey.admin.username}")
    private String adminUsername;

    /**
     * 管理员密码（必须从配置文件或环境变量读取，禁止硬编码默认值）
     */
    @Value("${ukey.admin.password}")
    private String adminPassword;

    /**
     * 密码传输加密密钥（用于解密前端传来的加密密码）
     */
    @Value("${ukey.password.encrypt-key:}")
    private String passwordEncryptKey;


    @Resource
    private RedisUtil redisUtil;

    /**
     * 校验UKey证书合法性
     * 客户端插入UKey后调用此接口，服务端依次校验：
     * 1. 证书是否已注册
     * 2. 证书状态（NORMAL/REVOKED/LOST）
     * 3. 有效期
     * 4. 加密算法合规性
     * 5. 绑定关系
     */
    @PostMapping("/validate")
    public Result<?> validateCertificate(@Validated @RequestBody CertValidateRequestDTO request) {
        CertValidateResponseDTO response = ukeyCertificateService.validateCertificate(request);
        if (response.isValid()) {
            return Result.data(response, response.getMessage());
        } else {
            return Result.fail(400, response.getMessage());
        }
    }

    /**
     * UKey 证书分页查询（UKey管理页面）
     * 支持按 Key名称或序列号关键词模糊查询
     *
     * @param keyword  关键词，匹配 Key名称或序列号，可为空
     * @param page     页码，默认1
     * @param pageSize 每页条数，默认10
     */
    @GetMapping("/page")
    public Result<?> pageCertificates(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        Page<UkeyCertificate> pageResult = ukeyCertificateService.pageQuery(keyword, page, pageSize);
        Map<String, Object> data = new HashMap<>();
        data.put("records", pageResult.getRecords());
        data.put("total", pageResult.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("pages", pageResult.getPages());
        return Result.data(data);
    }

    /**
     * 注册新证书
     */
    @PostMapping("/register")
    public Result<?> registerCertificate(@RequestBody UkeyCertificate certificate) {
        log.info("注册新证书 certSerialNo={}", certificate.getCertSerialNo());
        UkeyCertificate saved = ukeyCertificateService.registerCertificate(certificate);
        return Result.data(saved, "证书注册成功");
    }

    /**
     * 编辑 UKey 证书状态
     * 请求体 { "certSerialNo": "...", "certStatus": "NORMAL|REVOKED|LOST", "remark": "可选" }
     * <p>
     * 状态枚举说明：
     * NORMAL  = 正常（可登录）
     * REVOKED = 已注销（永久禁用）
     * LOST    = 已挂失（临时冻结，可恢复）
     */
    @PostMapping("/edit")
    public Result<?> editCertStatus(@RequestBody Map<String, String> params) {
        try {
            String certSerialNo = params.get("certSerialNo");
            String certStatus = params.get("certStatus");
            String remark = params.get("remark");

            if (certSerialNo == null || certSerialNo.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certSerialNo");
            }
            if (certStatus == null || certStatus.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certStatus");
            }
            if (!"NORMAL".equals(certStatus) && !"REVOKED".equals(certStatus) && !"LOST".equals(certStatus)) {
                return Result.fail(400, "certStatus 枚举值不合法，合法值为: NORMAL | REVOKED | LOST");
            }

            log.info("[编辑证书状态] certSerialNo={}, certStatus={}", certSerialNo, certStatus);
            boolean success = ukeyCertificateService.updateCertStatus(certSerialNo, certStatus);
            if (!success) {
                return Result.fail(404, "证书不存在: " + certSerialNo);
            }
            if (remark != null && !remark.isEmpty()) {
                ukeyCertificateService.updateRemark(certSerialNo, remark);
            }
            // 证书注销/挂失时立即加入黑名单，前端轮询 /cert/online-status 检测到 forceLogout=true 后强制退出
            if ("REVOKED".equals(certStatus) || "LOST".equals(certStatus)) {
                int cleared = addToBlacklistByCertSerialNo(certSerialNo);
                log.warn("[编辑证书状态] 状态变更为 {}，强制下线 certSerialNo={}, clearedTokens={}", certStatus, certSerialNo, cleared);
                // 证书注销/挂失，通过 WebSocket 主动推送强制下线事件给所有前端
                ukeyStatusPushService.pushCurrentStatus();
            }
            return Result.success("状态更新成功");
        } catch (Exception e) {
            return Result.error("编辑证书状态失败", e);
        }
    }

    /**
     * 删除 UKey 证书
     * 请求体 { "certSerialNo": "..." }
     * 注意：删除不可恢复，若仅需禁用请改用 /cert/edit 将状态改为 REVOKED
     */
    @PostMapping("/delete")
    public Result<?> deleteCertificate(@RequestBody Map<String, String> params) {
        String certSerialNo = params.get("certSerialNo");
        if (certSerialNo == null || certSerialNo.isEmpty()) {
            return Result.fail(400, "缺少必要参数: certSerialNo");
        }
        log.info("[删除证书] certSerialNo={}", certSerialNo);
        boolean success = ukeyCertificateService.deleteByCertSerialNo(certSerialNo);
        if (!success) {
            return Result.fail(404, "证书不存在: " + certSerialNo);
        }
        return Result.success("删除成功");
    }

    // ================================================================
    // 登录安全管理接口
    // ================================================================

    /**
     * 查询账户锁定状态
     * 请求参数: certSerialNo
     * 响应: { locked, failCount, remainingSeconds, message }
     */
    @GetMapping("/lock-status")
    public Result<?> getLockStatus(@RequestParam String certSerialNo) {
        if (certSerialNo == null || certSerialNo.isEmpty()) {
            return Result.fail(400, "缺少必要参数: certSerialNo");
        }
        LoginSecurityService.LockStatus status = loginSecurityService.getLockStatus(certSerialNo);
        Map<String, Object> data = new HashMap<>();
        data.put("locked", status.isLocked());
        data.put("failCount", status.getFailCount());
        data.put("remainingSeconds", status.getRemainingSeconds());
        data.put("message", status.getMessage());
        return Result.data(data);
    }

    /**
     * 更新证书状态（注销/挂失/恢复），保留兼容旧调用
     */
    @PutMapping("/status")
    public Result<?> updateCertStatus(@RequestParam String certSerialNo, @RequestParam String status) {
        Map<String, String> params = new HashMap<>();
        params.put("certSerialNo", certSerialNo);
        params.put("certStatus", status);
        return editCertStatus(params);
    }

    /**
     * 绑定证书到客户端
     */
    @PutMapping("/bind")
    public Result<?> bindToClient(@RequestParam String certSerialNo, @RequestParam String clientId) {
        log.info("绑定证书到客户端: certSerialNo={}, clientId={}", certSerialNo, clientId);
        boolean success = ukeyCertificateService.bindToClient(certSerialNo, clientId);
        return success ? Result.success("绑定成功") : Result.fail(404, "证书不存在");
    }

    /**
     * 客户端在线状态上报
     * 认证成功时传 ONLINE，UKey 拔出或退出时传 OFFLINE
     * 请求体 { "certSerialNo": "...", "clientId": "...", "onlineStatus": "ONLINE|OFFLINE" }
     */
    @PostMapping("/online")
    public Result<?> updateOnlineStatus(@RequestBody Map<String, String> params) {
        try {
            String certSerialNo = params.get("certSerialNo");
            String clientId = params.get("clientId");
            String onlineStatus = params.get("onlineStatus");

            if (certSerialNo == null || onlineStatus == null) {
                return Result.fail(400, "缺少必要参数: certSerialNo, onlineStatus");
            }
            if (!"ONLINE".equals(onlineStatus) && !"OFFLINE".equals(onlineStatus)) {
                return Result.fail(400, "onlineStatus 只能是 ONLINE 或 OFFLINE");
            }

            log.info("客户端在线状态上报 certSerialNo={}, clientId={}, status={}", certSerialNo, clientId, onlineStatus);

            long t0 = System.currentTimeMillis();
            boolean success = ukeyCertificateService.updateOnlineStatus(certSerialNo, clientId, onlineStatus);
            long t1 = System.currentTimeMillis();
            log.info("[耗时] updateOnlineStatus: {}ms", t1 - t0);
            if (success) {
                // 状态变更成功后，通过 WebSocket 主动推送最新状态给所有前端
                ukeyStatusPushService.pushCurrentStatus();
                long t2 = System.currentTimeMillis();
                log.info("[耗时] pushCurrentStatus: {}ms, 总计: {}ms", t2 - t1, t2 - t0);
            }
            return success ? Result.success("状态更新成功") : Result.fail(404, "证书不存在");
        } catch (Exception e) {
            return Result.error("在线状态上报失败", e);
        }
    }

    /**
     * 客户端断开通知
     * UKey 拔出或主动退出时调用，服务端处理：
     * 1. 鏇存柊 onlineStatus = OFFLINE
     * 2. 清除该证书对应的所有 Token（强制前端下线）
     */
    @PostMapping("/disconnect")
    public Result<?> clientDisconnect(@RequestBody Map<String, String> params) {
        String clientId = params.get("clientId");
        String certSerialNo = params.get("certSerialNo");
        String reason = params.getOrDefault("reason", "unknown");

        log.info("客户端断开通知: clientId={}, certSerialNo={}, reason={}", clientId, certSerialNo, reason);

        if (certSerialNo != null && !certSerialNo.isEmpty()) {
            ukeyCertificateService.updateOnlineStatus(certSerialNo, clientId, "OFFLINE");
            int cleared = addToBlacklistByCertSerialNo(certSerialNo);
            log.info("已清除 Token: certSerialNo={}, 清除数={}", certSerialNo, cleared);
            // UKey 拔出，通过 WebSocket 主动推送离线状态给所有前端
            ukeyStatusPushService.pushCurrentStatus();
        }
        return Result.success("断开通知已处理");
    }

    /**
     * 查询当前在线 UKey 状态（登录页 & 已登录页面轮询接口）
     * 前端每 2~3 秒轮询一次
     * <p>
     * 关键字段说明：
     * forceLogout=true         表示需要强制退出登录
     * forceLogoutReasons       强制退出原因数组，枚举值：
     * CLIENT_UKEY_REMOVED  客户端 UKey 物理拔出
     * SERVER_UKEY_REMOVED  服务端 UKey 物理拔出
     * CERT_REVOKED         证书被注销
     * CERT_LOST            证书被挂失
     * CLIENT_SERVICE_DOWN  客户端后端服务已停止（心跳超时）
     * serverUkeyOnline         服务端 UKey 是否在线
     * clientServiceOnline      客户端后端服务是否在线（基于心跳判断）
     * <p>
     * 注意：forceLogout 检测依赖 Token，登录页不带 Token 时 forceLogout 始终为 false
     */
    @PostMapping("/online-status")
    public Result<?> getOnlineStatus(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String currentIdentity = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                try {
                    Claims claims = jwtUtil.parse(token);
                    String jti = claims.getId();
                    // 不在 Redis 黑名单中，说明 Token 仍有效
                    if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(JWT_BLACKLIST_PREFIX + jti))) {
                        currentIdentity = claims.getSubject();
                    }
                } catch (JwtException ignored) {
                    // Token 无效或已过期，currentIdentity 保持 null
                }
            }

            UkeyCertificate onlineCert = ukeyCertificateService.getOnlineUkey();

            // 服务端 UKey 状态
            boolean serverOnline = vAuthAuthServerService.isServerUkeyOnline();
            // 客户端后端服务在线状态（基于心跳时间判断）
            boolean clientServiceOnline = isClientAlive(onlineCert);

            if (onlineCert == null) {
                // 无在线客户端 UKey
                boolean forceLogout = (currentIdentity != null);
                List<String> reasons = new ArrayList<>();
                if (forceLogout) {
                    if (!serverOnline) reasons.add("SERVER_UKEY_REMOVED");
                    if (!clientServiceOnline) reasons.add("CLIENT_SERVICE_DOWN");
                    reasons.add("CLIENT_UKEY_REMOVED");
                }
                Map<String, Object> data = new HashMap<>();
                data.put("username", null);
                data.put("userBound", false);
                data.put("ukeybound", false);
                data.put("forceLogout", forceLogout);
                data.put("forceLogoutReasons", reasons);
                data.put("serverUkeyOnline", serverOnline);
                data.put("clientServiceOnline", clientServiceOnline);
                return Result.data(data, forceLogout ? "UKey 已拔出，请重新插入认证" : "暂无在线 UKey");
            } else {
                // 有在线客户端 UKey，收集所有强制下线原因
                String certStatus = onlineCert.getCertStatus();
                List<String> reasons = new ArrayList<>();
                if (!serverOnline) reasons.add("SERVER_UKEY_REMOVED");
                if (!clientServiceOnline) reasons.add("CLIENT_SERVICE_DOWN");
                if ("REVOKED".equals(certStatus)) reasons.add("CERT_REVOKED");
                if ("LOST".equals(certStatus)) reasons.add("CERT_LOST");
                boolean forceLogout = !reasons.isEmpty();

                Map<String, Object> data = new HashMap<>();
                data.put("certSerialNo", onlineCert.getCertSerialNo());
                data.put("displayName", onlineCert.getDisplayName() != null ? onlineCert.getDisplayName() : onlineCert.getCertSerialNo());
                data.put("onlineStatus", onlineCert.getOnlineStatus());
                data.put("lastAuthTime", onlineCert.getLastAuthTime());
                data.put("boundClientId", onlineCert.getBoundClientId());
                appendUserBinding(data, onlineCert.getCertSerialNo());
                data.put("forceLogout", forceLogout);
                data.put("forceLogoutReasons", reasons);
                data.put("serverUkeyOnline", serverOnline);
                data.put("clientServiceOnline", clientServiceOnline);
                return Result.data(data, forceLogout ? "证书状态异常，强制下线" : "UKey 已插入");
            }
        } catch (Exception e) {
            return Result.error("查询在线状态失败", e);
        }
    }

    /**
     * 判断客户端服务是否存活（基于心跳时间）
     * 若证书为空（无在线 UKey），视为客户端服务状态未知，返回 false
     * 若证书从未上报过心跳，返回 false
     * 若距上次心跳超过 heartbeatTimeoutSeconds 秒，返回 false
     */
    private boolean isClientAlive(UkeyCertificate cert) {
        if (cert == null) return false;
        LocalDateTime lastBeat = cert.getLastHeartbeatTime();
        if (lastBeat == null) return false;
        long secondsSinceLastBeat = java.time.Duration.between(lastBeat, LocalDateTime.now()).getSeconds();
        return secondsSinceLastBeat <= heartbeatTimeoutSeconds;
    }

    /**
     * 上一次检测到的客户端存活状态（用于翻转检测，避免重复推送）
     * true=存活，false=超时/未知，null=初始未知
     */
    private volatile Boolean lastClientAliveState = null;

    /**
     * 心跳超时定时检测
     * 每隔 10 秒检查一次在线 UKey 的心跳，
     * 当客户端存活状态发生翻转时通过 WebSocket 推送，确保前端及时感知 CLIENT_SERVICE_DOWN。
     * 正常心跳间隔 5 秒，超时阈值 15 秒，10 秒轮检足以捕捉翻转。
     */
    @Scheduled(fixedDelay = 10000)
    public void checkHeartbeatTimeout() {
        try {
            UkeyCertificate onlineCert = ukeyCertificateService.getOnlineUkey();
            boolean currentAlive = isClientAlive(onlineCert);

            if (lastClientAliveState == null) {
                // 首次初始化，不推送，仅记录初始状态
                lastClientAliveState = currentAlive;
                return;
            }

            if (lastClientAliveState && !currentAlive) {
                // 状态翻转：存活 -> 超时
                log.warn("[心跳检测] 客户端服务心跳超时，触发 WebSocket 推送 CLIENT_SERVICE_DOWN");
                // 心跳超时视同客户端离线，同步更新数据库状态为 OFFLINE
                if (onlineCert != null) {
                    ukeyCertificateService.updateOnlineStatus(onlineCert.getCertSerialNo(), onlineCert.getBoundClientId(), "OFFLINE");
                    log.warn("[心跳检测] 已将 {} 状态置为 OFFLINE", onlineCert.getCertSerialNo());
                }
                ukeyStatusPushService.pushCurrentStatus();
            } else if (!lastClientAliveState && currentAlive) {
                // 状态翻转：超时 -> 恢复，推送恢复通知
                log.info("[心跳检测] 客户端服务心跳恢复，触发 WebSocket 推送");
                ukeyStatusPushService.pushCurrentStatus();
            }

            lastClientAliveState = currentAlive;
        } catch (Exception e) {
            log.error("[心跳检测] 定时检测异常", e);
        }
    }

    /**
     * 客户端心跳上报接口
     * 客户端每 5 秒调用一次，管控平台以此感知客户端进程是否存活
     * 无需认证（白名单接口）
     * <p>
     * 请求体 { "certSerialNo": "...", "clientId": "...", "clientIp": "..." }
     */
    @PostMapping("/heartbeat")
    public Result<?> heartbeat(@RequestBody Map<String, String> params, javax.servlet.http.HttpServletRequest request) {
        try {
            String certSerialNo = params.get("certSerialNo");
            String clientId = params.get("clientId");
            // IP 优先取请求体中的字段，其次取 X-Forwarded-For，最后取直连 IP
            String clientIp = params.get("clientIp");
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = request.getHeader("X-Forwarded-For");
            }
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = request.getRemoteAddr();
            }

            if (certSerialNo == null || certSerialNo.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certSerialNo");
            }

            // 记录心跳前的存活状态
            boolean wasDead = !isClientAlive(ukeyCertificateService.getOnlineUkey());

            boolean ok = ukeyCertificateService.updateHeartbeat(certSerialNo, clientId, clientIp);
            if (!ok) {
                // 证书不存在时静默返回成功，避免客户端频繁报错
                log.debug("[心跳] 证书不存在，忽略: certSerialNo={}", certSerialNo);
            }

            // 心跳写入后，如果之前是超时状态，现在恢复了，立即推送
            if (wasDead && ok) {
                log.info("[心跳] 客户端从超时恢复，立即触发 WebSocket 推送");
                lastClientAliveState = true;
                ukeyStatusPushService.pushCurrentStatus();
            }

            return Result.success("ok", null);
        } catch (Exception e) {
            log.error("[心跳] 处理心跳异常", e);
            return Result.error("心跳上报失败", e);
        }
    }

    /**
     * UKey 登录接口
     * 前缀条件：该 UKey 已完成双向认证（onlineStatus=ONLINE）
     * 请求体 { "certSerialNo": "...", "pin": "..." }
     * 响应: { token, certSerialNo, displayName }
     * <p>
     * 安全机制：
     * - 连续5次失败锁定账户
     * - 锁定10分钟后自动解锁
     */
    @PostMapping("/login")
    public Result<?> ukeyLogin(@RequestBody Map<String, String> params) {
        try {
            String certSerialNo = params.get("certSerialNo");
            String pin = params.get("pin");

            if (certSerialNo == null || pin == null) {
                return Result.fail(400, "missing params: certSerialNo, pin");
            }

            log.info("UKey login request: certSerialNo={}", certSerialNo);

            // 如果配置了加密密钥，则尝试解密PIN码
            if (passwordEncryptKey != null && !passwordEncryptKey.trim().isEmpty()) {
                try {
                    pin = EncryptUtil.decrypt(pin, passwordEncryptKey);
                    log.info("[UKeyLogin] PIN码解密成功");
                } catch (Exception e) {
                    log.error("[UKeyLogin] PIN码解密失败，可能是未加密或密钥不匹配", e);
                    return Result.fail(400, "PIN码格式错误");
                }
            }

            if (loginSecurityService.isLocked(certSerialNo)) {
                LoginSecurityService.LockStatus lockStatus = loginSecurityService.getLockStatus(certSerialNo);
                log.warn("[UKeyLogin] account locked, certSerialNo={}", certSerialNo);
                Map<String, Object> lockData = new HashMap<>();
                lockData.put("locked", true);
                lockData.put("message", lockStatus.getMessage());
                lockData.put("failCount", lockStatus.getFailCount());
                return Result.fail(423, lockStatus.getMessage());
            }

            UkeyCertificate cert = ukeyCertificateService.loginByUkey(certSerialNo, pin);
            if (cert == null) {
                int failCount = loginSecurityService.recordFail(certSerialNo);
                int remainingAttempts = 5 - failCount;
                String msg = remainingAttempts > 0 ? String.format("PIN error, remaining attempts: %d", remainingAttempts) : "too many PIN errors, account is locked";
                Map<String, Object> failData = new HashMap<>();
                failData.put("failCount", failCount);
                failData.put("remainingAttempts", Math.max(0, remainingAttempts));
                failData.put("locked", failCount >= 5);
                return Result.fail(401, msg);
            }

            loginSecurityService.clearFailCount(certSerialNo);

            Map<String, Object> authResp = roleFeignClient.getAuthContextByUkeyId(cert.getCertSerialNo());
            if (authResp == null) {
                return Result.fail(500, "role service response is null");
            }
            int authCode = safeInt(authResp.get("code"), 500);
            if (authCode != 200) {
                return Result.fail(403, safeStr(authResp.get("msg"), "platform user or permissions not configured"));
            }
            Object authDataObj = authResp.get("data");
            if (!(authDataObj instanceof Map)) {
                return Result.fail(403, "platform user or permissions not configured");
            }
            @SuppressWarnings("unchecked") Map<String, Object> authData = (Map<String, Object>) authDataObj;

            String roleCode = safeStr(authData.get("roleCode"), "UKEY");
            String permissionCsv = toPermissionCsv(authData.get("permissionCodes"));

            String token = jwtUtil.generate(cert.getCertSerialNo(), roleCode);
            tokenStore.put(token, cert.getCertSerialNo());
            String displayName = cert.getDisplayName() != null ? cert.getDisplayName() : certSerialNo;

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("certSerialNo", certSerialNo);
            data.put("displayName", displayName);
            data.put("boundClientId", cert.getBoundClientId());
            data.put("userId", authData.get("userId"));
            data.put("username", authData.get("username"));
            data.put("roleId", authData.get("roleId"));
            data.put("roleCode", roleCode);
            data.put("roleName", authData.get("roleName"));
            data.put("permissionCodes", authData.get("permissionCodes"));
            data.put("permissionCsv", permissionCsv);

            log.info("UKey login success: certSerialNo={}, userId={}, roleCode={}", certSerialNo, authData.get("userId"), roleCode);
            return Result.success("login success", data);
        } catch (Exception e) {
            return Result.error("UKey login exception", e);
        }
    }

    private int safeInt(Object value, int defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String safeStr(Object value, String defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? defaultVal : str;
    }

    private void appendUserBinding(Map<String, Object> data, String certSerialNo) {
        data.put("username", null);
        data.put("userBound", false);
        data.put("ukeybound", false);
        if (certSerialNo == null || certSerialNo.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, Object> authResp = roleFeignClient.getAuthContextByUkeyId(certSerialNo);
            int authCode = safeInt(authResp == null ? null : authResp.get("code"), 500);
            if (authCode != 200) {
                log.warn("[OnlineStatus] UKey user binding not found: certSerialNo={}, code={}", certSerialNo, authCode);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> authData = authResp.get("data") instanceof Map
                    ? (Map<String, Object>) authResp.get("data")
                    : null;
            String username = safeStr(authData == null ? null : authData.get("username"), "");
            if (username.isEmpty()) {
                log.warn("[OnlineStatus] UKey user binding has empty username: certSerialNo={}", certSerialNo);
                return;
            }

            data.put("username", username);
            data.put("userBound", true);
            data.put("ukeybound", true);
        } catch (Exception e) {
            log.warn("[OnlineStatus] query UKey user binding failed: certSerialNo={}, error={}", certSerialNo, e.getMessage());
        }
    }

    private String toPermissionCsv(Object permissionCodesObj) {
        if (permissionCodesObj == null) {
            return "";
        }
        if (permissionCodesObj instanceof List) {
            @SuppressWarnings("unchecked") List<Object> list = (List<Object>) permissionCodesObj;
            return list.stream().filter(v -> v != null && !String.valueOf(v).trim().isEmpty()).map(v -> String.valueOf(v).trim()).collect(Collectors.joining(","));
        }
        return String.valueOf(permissionCodesObj).trim();
    }

    /**
     * 管理员账号密码登录
     * 用于管理员进入 UKey授权管理 页面时的独立鉴权
     * 请求体 { "username": "admin", "password": "xxx" }
     * 响应: { token, role: "ADMIN" }
     */
    @PostMapping("/admin-login")
    public Result<?> adminLogin(@RequestBody Map<String, String> params) {
        try {
            Object temporaryAuthorization = redisUtil.get("temporary-authorization");
            if (Boolean.FALSE.equals(temporaryAuthorization)) {
                return Result.fail(401, "Temporary authorization not found");
            }
            String username = params.get("username");
            String password = params.get("password");

            // 安全校验：禁止使用 root、admin、guest 等默认账号名登录
            PasswordValidator.ValidationResult usernameResult = PasswordValidator.validateUsername(username);
            if (!usernameResult.isValid()) {
                log.warn("[AdminLogin] 用户名不合规，拒绝登录 username={}", username);
                return Result.fail(403, usernameResult.getMessage());
            }

            // 如果配置了加密密钥，则尝试解密密码
            if (passwordEncryptKey != null && !passwordEncryptKey.trim().isEmpty()) {
                try {
                    password = EncryptUtil.decrypt(password, passwordEncryptKey);
                    log.info("[AdminLogin] 密码解密成功");
                } catch (Exception e) {
                    log.error("[AdminLogin] 密码解密失败，可能是未加密或密钥不匹配", e);
                    return Result.fail(400, "密码格式错误");
                }
            }

            if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
                log.warn("[AdminLogin] 账号或密码错误 username={}", username);
                return Result.fail(401, "账号或密码错误");
            }
            // 签发 JWT
            String token = jwtUtil.generate("ADMIN", "ADMIN");
            tokenStore.put(token, "ADMIN");
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("role", "ADMIN");
            log.info("[AdminLogin] 管理员登录成功 username={}", username);
            redisUtil.set("temporary-authorization", true);
            return Result.success("登录成功", data);
        } catch (Exception e) {
            return Result.error("管理员登录异常", e);
        }
    }


    /**
     * 获取临时授权信息
     *
     * @return
     */
    @GetMapping("/temporary-authorization-info")
    public com.monitorplatform.common.entity.Result<?> temporaryAuthorization() {
        Object temporaryAuthorization = redisUtil.get("temporary-authorization");
        if (temporaryAuthorization == null){
            temporaryAuthorization = true;
        }
        return com.monitorplatform.common.entity.Result.data(temporaryAuthorization);
    }

    // ================================================================
    // 管理员 - UKey 证书导入与管理接口
    // ================================================================

    /**
     * Token 有效性校验（网关专用）
     * 请求头 Authorization: Bearer <token>
     * 返回 200 表示有效，401 表示无效/未登录
     * JWT 方案下，网关本地解析 JWT 校验，此接口仅作备用
     */
    @GetMapping("/check-token")
    public Result<?> checkToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        if (token == null || token.isEmpty()) {
            return Result.fail(401, "Token 无效或未登录");
        }
        try {
            Claims claims = jwtUtil.parse(token);
            String jti = claims.getId();
            // 查 Redis 黑名单
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(JWT_BLACKLIST_PREFIX + jti))) {
                return Result.fail(401, "Token 已失效（已登出）");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("identity", claims.getSubject());
            data.put("role", claims.get("role", String.class));
            return Result.success("Token 有效", data);
        } catch (JwtException e) {
            return Result.fail(401, "Token 无效或已过期");
        }
    }

    /**
     * 获取密码加密密钥（供前端加密密码使用）
     * 注意：此接口应该通过 HTTPS 调用，确保密钥传输安全
     * 返回: { "encryptKey": "..." }
     */
    @GetMapping("/encrypt-key")
    public Result<?> getEncryptKey() {
        try {
            if (passwordEncryptKey == null || passwordEncryptKey.trim().isEmpty()) {
                // 如果未配置加密密钥，则生成一个新的
                passwordEncryptKey = EncryptUtil.generateKey();
                log.info("[EncryptKey] 自动生成新的加密密钥");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("encryptKey", passwordEncryptKey);
            data.put("algorithm", "AES-256-GCM");
            return Result.success("获取加密密钥成功", data);
        } catch (Exception e) {
            return Result.error("获取加密密钥失败", e);
        }
    }

    /**
     * 导入 UKey 证书（导入后状态为 NORMAL，直接生效）
     * 鏀寔 multipart/form-data 鍜?application/json 涓ょ Content-Type
     * 参数: certSerialNo, displayName, pin, issuer, validFrom, validUntil, remark, boundClientId
     */
    @PostMapping(value = "/import", consumes = {"multipart/form-data", "application/json", "application/x-www-form-urlencoded"})
    public Result<?> importCertificate(@RequestParam(value = "certSerialNo", required = false) String certSerialNo, @RequestParam(value = "displayName", required = false) String displayName, @RequestParam(value = "pin", required = false) String pin, @RequestParam(value = "issuer", required = false) String issuer, @RequestParam(value = "validFrom", required = false) String validFromStr, @RequestParam(value = "validUntil", required = false) String validUntilStr, @RequestParam(value = "remark", required = false) String remark, @RequestParam(value = "boundClientId", required = false) String boundClientId, @RequestParam(value = "certStatus", required = false) String certStatus) {
        try {

            if (certSerialNo == null || certSerialNo.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certSerialNo");
            }
            // 支持多种日期格式："2036-03-02T13:54"、"2036-03-02 13:54"、"2036-03-02T13:54:00"
            LocalDateTime validFrom = parseDateTime(validFromStr);
            LocalDateTime validUntil = parseDateTime(validUntilStr);
            // valid_until 数据库 NOT NULL，前端未传时给默认 10 年后
            if (validUntil == null) {
                validUntil = LocalDateTime.now().plusYears(10);
                log.warn("[UKey导入] 未收到 validUntil，使用默认 10 年后: certSerialNo={}", certSerialNo);
            }
            UkeyCertificate cert = ukeyCertificateService.importCertificate(certSerialNo, displayName, pin, issuer, validFrom, validUntil, remark);
            // 导入后若传了 certStatus，更新证书状态
            if (certStatus != null && !certStatus.isEmpty()) {
                ukeyCertificateService.updateCertStatus(certSerialNo, certStatus.toUpperCase());
                log.info("[UKey导入] 证书状态已设置: certSerialNo={}, certStatus={}", certSerialNo, certStatus);
            }
            // 导入后若传了 boundClientId，立即绑定
            if (boundClientId != null && !boundClientId.isEmpty()) {
                cert.setBoundClientId(boundClientId);
                ukeyCertificateService.updateCertificate(cert);
                log.info("[UKey导入] 证书已绑定客户端: certSerialNo={}, boundClientId={}", certSerialNo, boundClientId);
            }
            return Result.success("证书导入成功");
        } catch (Exception e) {
            return Result.error("导入证书失败", e);
        }
    }

    /**
     * 绑定证书与客户端（PUT /cert/bind-client）
     * 请求体 { "certSerialNo": "44030000003330000126_1E2D11", "clientId": "ipc-a1b2c3" }
     * 将指定证书的 bound_client_id 更新为传入的 clientId
     */
    @PutMapping("/bind-client")
    public Result<?> bindClient(@RequestBody Map<String, String> params) {
        String certSerialNo = params.get("certSerialNo");
        String clientId = params.get("clientId");
        if (certSerialNo == null || certSerialNo.isEmpty()) {
            return Result.fail(400, "缺少必要参数: certSerialNo");
        }
        if (clientId == null || clientId.isEmpty()) {
            return Result.fail(400, "缺少必要参数: clientId");
        }
        try {
            UkeyCertificate cert = ukeyCertificateService.getByCertSerialNo(certSerialNo);
            if (cert == null) {
                // 尝试前缀匹配（authId 不带后缀时）
                cert = ukeyCertificateService.getByCertSerialNoPrefix(certSerialNo);
            }
            if (cert == null) {
                return Result.fail(404, "证书不存在: " + certSerialNo);
            }
            cert.setBoundClientId(clientId);
            ukeyCertificateService.updateCertificate(cert);
            log.info("[UKey绑定] 证书已绑定客户端: certSerialNo={}, clientId={}", cert.getCertSerialNo(), clientId);
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("certSerialNo", cert.getCertSerialNo());
            resultData.put("boundClientId", clientId);
            return Result.data(resultData, "绑定成功");
        } catch (Exception e) {
            log.error("[UKey绑定] 绑定失败", e);
            return Result.error("绑定失败", e);
        }
    }

    /**
     * 查询待审核的 UKey 证书列表
     */
    @GetMapping("/pending/list")
    public Result<?> listPendingCertificates() {
        try {
            List<UkeyCertificate> list = ukeyCertificateService.listPendingCertificates();
            return Result.success(list);
        } catch (Exception e) {
            return Result.error("查询待审核证书失败", e);
        }
    }

    /**
     * 审核通过：PENDING -> NORMAL
     * 请求体 { "certSerialNo": "...", "pin": "可选，导入时未填则在此补充" }
     */
    @PostMapping("/pending/approve")
    public Result<?> approveCertificate(@RequestBody Map<String, String> params) {
        try {
            String certSerialNo = params.get("certSerialNo");
            String pin = params.get("pin");
            if (certSerialNo == null || certSerialNo.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certSerialNo");
            }
            boolean success = ukeyCertificateService.approveCertificate(certSerialNo, pin);
            log.info("[审核通过] certSerialNo={}, 结果={}", certSerialNo, success);
            return Result.success("审核通过，证书已生效");
        } catch (Exception e) {
            return Result.error("审核证书失败", e);
        }
    }

    /**
     * 审核拒绝：PENDING -> REVOKED
     * 请求体 { "certSerialNo": "..." }
     */
    @PostMapping("/pending/reject")
    public Result<?> rejectCertificate(@RequestBody Map<String, String> params) {
        try {
            String certSerialNo = params.get("certSerialNo");
            if (certSerialNo == null || certSerialNo.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certSerialNo");
            }
            boolean success = ukeyCertificateService.rejectCertificate(certSerialNo);
            log.info("[审核拒绝] certSerialNo={}, 结果={}", certSerialNo, success);
            return Result.success("证书已拒绝");
        } catch (Exception e) {
            return Result.error("拒绝证书失败", e);
        }
    }

    /**
     * 修改 PIN 码
     * 实现安全规范要求：密码定期更换
     * 请求体 { "certSerialNo": "...", "oldPin": "...", "newPin": "..." }
     */
    @PostMapping("/password/change")
    public Result<?> changePassword(@RequestBody Map<String, String> params) {
        try {
            String certSerialNo = params.get("certSerialNo");
            String oldPin = params.get("oldPin");
            String newPin = params.get("newPin");

            if (certSerialNo == null || certSerialNo.isEmpty()) {
                return Result.fail(400, "缺少必要参数: certSerialNo");
            }
            if (oldPin == null || oldPin.isEmpty()) {
                return Result.fail(400, "缺少必要参数: oldPin");
            }
            if (newPin == null || newPin.isEmpty()) {
                return Result.fail(400, "缺少必要参数: newPin");
            }

            boolean success = ukeyCertificateService.changePassword(certSerialNo, oldPin, newPin);
            log.info("[修改密码] certSerialNo={}, 结果={}", certSerialNo, success);
            return success ? Result.success("密码修改成功") : Result.fail(400, "密码修改失败，请检查旧密码是否正确");
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("修改密码失败", e);
        }
    }

    // ================================================================
    // 证书文件解析接口（管理员导入时上传 cer 自动解析内容）
    // ================================================================

    /**
     * 解析证书文件：cer / .pem 格式
     * 前端上传文件后，后端解析返回证书内容，管理员确认后再调用 /cert/import 提交
     */
    @PostMapping("/parse")
    public Result<?> parseCertFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.fail(400, "请选择证书文件");
            }

            byte[] fileBytes = file.getBytes();
            X509Certificate x509;

            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                x509 = (X509Certificate) cf.generateCertificate(is);
            }

            String subjectDN = x509.getSubjectX500Principal().getName();
            String certSerialNo = extractCN(subjectDN);
            String issuerDN = x509.getIssuerX500Principal().getName();
            String sigAlg = x509.getSigAlgName();
            String cryptoAlgorithm = (sigAlg != null && sigAlg.toUpperCase().contains("SM")) ? "SM2" : x509.getPublicKey().getAlgorithm();

            LocalDateTime validFrom = x509.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime validUntil = x509.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

            Map<String, Object> data = new HashMap<>();
            data.put("certSerialNo", certSerialNo);
            data.put("displayName", certSerialNo);
            data.put("issuer", issuerDN);
            data.put("subject", subjectDN);
            data.put("validFrom", validFrom.toString());
            data.put("validUntil", validUntil.toString());
            data.put("cryptoAlgorithm", cryptoAlgorithm);
            data.put("serialNumber", x509.getSerialNumber().toString(16).toUpperCase());

            log.info("[证书解析] 解析成功: certSerialNo={}, issuer={}", certSerialNo, issuerDN);
            return Result.data(data, "证书解析成功");
        } catch (Exception e) {
            return Result.error("证书解析失败，请确认文件格式为标准 X.509 PEM/DER", e);
        }
    }

    /**
     * 从 DN 字符串中提取 CN 字段
     * 例如: "CN=44030000003330000126_1E2D11" -> "44030000003330000126_1E2D11"
     */
    private String extractCN(String dn) {
        if (dn == null) return "";
        // JDK 返回的 DN 格式为 RFC2253，CN 在首位
        String[] parts = dn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3).trim();
            }
        }
        return dn;
    }

    /**
     * 将某个 certSerialNo 对应的所有 JWT 加入 Redis 黑名单
     *
     * @return 加入黑名单的 token 数量
     */
    private int addToBlacklistByCertSerialNo(String certSerialNo) {
        // 先获取要清除的 Token 列表，再清除（顺序很重要）
        List<String> tokens = tokenStore.getTokensByCertSerialNo(certSerialNo);
        int count = tokenStore.removeByIdentity(certSerialNo);
        // 将对应的 jti 写入 Redis 黑名单，TTL = JWT 剩余有效期
        tokens.forEach(token -> {
            try {
                Claims claims = jwtUtil.parse(token);
                String jti = claims.getId();
                long ttl = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
                if (ttl > 0) {
                    stringRedisTemplate.opsForValue().set(JWT_BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.SECONDS);
                    log.info("[JWT黑名单] jti={} 已加入黑名单, ttl={}s", jti, ttl);
                }
            } catch (JwtException ignored) {
                // 已过期的 Token 不需要入黑名单
            }
        });
        return count;
    }

    /**
     * 日期字符串解析，支持多种格式：
     * - "2036-03-02T13:54:00"
     * - "2036-03-02T13:54"
     * - "2036-03-02 13:54:00"
     * - "2036-03-02 13:54"
     * - "2036-03-02T13:54:00.000Z"（ISO带毫秒）
     */
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        str = str.trim().replace(" ", "T");
        // 去掉毫秒和时区尾缀
        if (str.contains(".")) str = str.substring(0, str.indexOf('.'));
        if (str.endsWith("Z")) str = str.substring(0, str.length() - 1);
        try {
            // 带秒：2036-03-02T13:54:00
            if (str.length() == 19) return LocalDateTime.parse(str);
            // 不带秒：2036-03-02T13:54
            if (str.length() == 16) return LocalDateTime.parse(str + ":00");
            return LocalDateTime.parse(str);
        } catch (Exception e) {
            log.warn("[UKey导入] 日期格式解析失败, str={}, error={}", str, e.getMessage());
            return null;
        }
    }
}


