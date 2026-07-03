package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig;
import com.infopublish.client.entity.dto.precheck.BasicChecks;
import com.infopublish.client.entity.dto.precheck.FileCheckResult;
import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyResponse;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.GatewayService;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.SigmaApiClient;
import com.infopublish.client.service.SigmaPublishService;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 发布前预检查编排服务。
 *
 * <p>当前对接青松单接口模式：平台调用客户端 precheck，客户端根据 target.ip
 * 调用 {sigmaBaseUrl}/api/v1/callback/program-by-ip 获取节目单，并随 precheck
 * 结果签发 publishPermit。</p>
 */
@Slf4j
@Service
public class PublishPrecheckServiceImpl implements PublishPrecheckService {

    @Value("${precheck.enabled:true}")
    private boolean precheckEnabled;

    @Resource
    private UkeyLifecycleManager ukeyLifecycleManager;

    @Resource
    private ClientAuthService clientAuthService;

    @Resource
    private ProcessBindService processBindService;

    @Resource
    private AppConfig.ProcessBindProperties processBindProperties;

    @Resource
    private GatewayService gatewayService;

    @Resource
    private SigmaApiClient sigmaApiClient;

    @Resource
    private SigmaPublishService sigmaPublishService;

    private final ConcurrentHashMap<String, PrecheckResponse> idempotentCache = new ConcurrentHashMap<>();

    @Override
    public PrecheckResponse precheck(PrecheckRequest request) {
        String requestId = request.getRequestId();
        log.info("[预检查] 开始 precheck: requestId={}, sigmaBaseUrl={}, playlistId={}",
                requestId, request.getSigmaBaseUrl(), request.getPlaylistId());

        PrecheckResponse cached = idempotentCache.get(requestId);
        if (cached != null) {
            log.info("[预检查] 命中幂等缓存: requestId={}", requestId);
            return cached;
        }

        int timeoutMs = request.getEffectiveTimeoutMs();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PrecheckResponse> future = executor.submit(() -> doPrecheck(request));
            PrecheckResponse response = attachInfoBoardInfo(request, future.get(timeoutMs, TimeUnit.MILLISECONDS));
            cacheIfStable(requestId, response);
            return response;
        } catch (TimeoutException e) {
            log.error("[预检查] 整体超时: requestId={}, timeoutMs={}", requestId, timeoutMs);
            PrecheckResponse response = PrecheckResponse.error("处理超时", requestId);
            return response;
        } catch (Exception e) {
            log.error("[预检查] 执行异常: requestId={}, error={}", requestId, e.getMessage(), e);
            PrecheckResponse response = PrecheckResponse.error("预检查执行异常: " + e.getMessage(), requestId);
            return response;
        } finally {
            executor.shutdownNow();
        }
    }

    private void cacheIfStable(String requestId, PrecheckResponse response) {
        if (response != null && response.isSuccess()) {
            idempotentCache.put(requestId, response);
            return;
        }
        log.info("[预检查] 跳过错误响应幂等缓存: requestId={}", requestId);
    }

    private PrecheckResponse attachInfoBoardInfo(PrecheckRequest request, PrecheckResponse response) {
        if (response == null) {
            return null;
        }
        if (request == null || request.getTarget() == null) {
            return response;
        }
        response.attachInfoBoard(request.getTarget().getIp(), request.getTarget().getPort());
        return response;
    }

    private PrecheckResponse doPrecheck(PrecheckRequest request) {
        String requestId = request.getRequestId();

        if (!precheckEnabled) {
            log.warn("[预检查] 功能未启用: requestId={}", requestId);
            return PrecheckResponse.error("precheck 未启用", requestId);
        }

        BasicChecks checks = executeBasicChecks();
        if (!checks.isAllPassed()) {
            String failReason = buildBasicCheckFailMessage(checks);
            log.warn("[预检查] 基础检查未通过: requestId={}, reason={}", requestId, failReason);
            return PrecheckResponse.basicCheckFailed(request.getPlaylistId(), checks, failReason, requestId);
        }
        log.info("[预检查] 基础检查全部通过: requestId={}", requestId);

        QingsongProgramResponse.ProgramData program = resolveProgram(request);
        String infoBoardIp = resolveInfoBoardIp(request);
        String validationError = validateProgram(program, infoBoardIp);
        if (validationError != null) {
            log.warn("[预检查] 青松节目单校验失败: requestId={}, ip={}, reason={}",
                    requestId, infoBoardIp, validationError);
            return PrecheckResponse.error(validationError, requestId);
        }

        applyProgramToRequest(request, program);

        PrecheckResponse response = PrecheckResponse.passed(program.getPlaylistId(), checks,
                FileCheckResult.empty(), requestId);
        response.attachProgram(program.getTarget(), program.getItems());
        attachPublishPermitIfPossible(request, response, program.getPlaylistId());
        return response;
    }

    private QingsongProgramResponse.ProgramData resolveProgram(PrecheckRequest request) {
        if (request != null
                && hasText(request.getPlaylistId())
                && request.getTarget() != null
                && request.getItems() != null
                && !request.getItems().isEmpty()) {
            QingsongProgramResponse.ProgramData program = new QingsongProgramResponse.ProgramData();
            program.setSuccess(true);
            program.setPlaylistId(request.getPlaylistId());
            program.setTarget(request.getTarget());
            program.setItems(request.getItems());
            log.info("[预检查] 使用请求中已有节目单: requestId={}, playlistId={}, items={}",
                    request.getRequestId(), request.getPlaylistId(), request.getItems().size());
            return program;
        }

        String infoBoardIp = resolveInfoBoardIp(request);
        if (!hasText(infoBoardIp)) {
            return null;
        }
        return sigmaApiClient.getProgramByIp(request.getSigmaBaseUrl(), infoBoardIp);
    }

    private String resolveInfoBoardIp(PrecheckRequest request) {
        if (request == null || request.getTarget() == null) {
            return null;
        }
        return trimToNull(request.getTarget().getIp());
    }

    private String validateProgram(QingsongProgramResponse.ProgramData program, String requestedIp) {
        if (program == null) {
            return "获取青松节目单失败";
        }
        if (!hasText(program.getPlaylistId())) {
            return "青松节目单 playlistId 为空";
        }
        if (program.getTarget() == null) {
            return "青松节目单 target 为空";
        }
        if (!hasText(program.getTarget().getIp())) {
            return "青松节目单 target.ip 为空";
        }
        if (!program.getTarget().getIp().trim().equals(requestedIp)) {
            return "青松节目单 target.ip 与请求 IP 不一致";
        }
        if (program.getItems() == null || program.getItems().isEmpty()) {
            return "青松节目单 items 为空";
        }
        for (int i = 0; i < program.getItems().size(); i++) {
            SigmaVerifyRequest.PlaylistItem item = program.getItems().get(i);
            if (item == null) {
                return "青松节目单 items[" + i + "] 为空";
            }
            if (item.getOrderNo() == null) {
                return "青松节目单 items[" + i + "].orderNo 为空";
            }
            if (!hasText(item.getFileName())) {
                return "青松节目单 items[" + i + "].fileName 为空";
            }
            if (!hasText(item.getFileType())) {
                return "青松节目单 items[" + i + "].fileType 为空";
            }
            if (!hasText(item.getFileUrl())) {
                return "青松节目单 items[" + i + "].fileUrl 为空";
            }
            if (item.getDurationSeconds() == null || item.getDurationSeconds() <= 0) {
                return "青松节目单 items[" + i + "].durationSeconds 无效";
            }
        }
        return null;
    }

    private void applyProgramToRequest(PrecheckRequest request, QingsongProgramResponse.ProgramData program) {
        request.setPlaylistId(program.getPlaylistId());
        request.setTarget(program.getTarget());
        request.setItems(program.getItems());
    }

    private void attachPublishPermitIfPossible(PrecheckRequest request,
                                               PrecheckResponse response,
                                               String resolvedPlaylistId) {
        if (!response.isPublishAllowed()) {
            return;
        }
        if (request.getTarget() == null || request.getItems() == null || request.getItems().isEmpty()) {
            response.attachPermitFailure("publishPermit 签发失败: 青松节目单缺少 target/items");
            log.warn("[预检查] 未签发 publishPermit: requestId={}, reason=missing target/items",
                    request.getRequestId());
            return;
        }

        SigmaVerifyRequest permitRequest = new SigmaVerifyRequest();
        permitRequest.setRequestId(request.getRequestId());
        permitRequest.setPrecheckId(response.getPrecheckId());
        permitRequest.setPlaylistId(resolvedPlaylistId);
        permitRequest.setTarget(request.getTarget());
        permitRequest.setItems(request.getItems());
        permitRequest.setOperatorId(request.getOperatorId());

        SigmaVerifyResponse permitResponse = sigmaPublishService.issuePermit(permitRequest);
        if (permitResponse.isSuccess()) {
            response.attachPermit(permitResponse.getPublishPermit(), permitResponse.getPlaylistDigest());
            log.info("[预检查] publishPermit 已随 precheck 返回: requestId={}, playlistDigest={}",
                    request.getRequestId(), permitResponse.getPlaylistDigest());
        } else {
            response.attachPermitFailure("publishPermit 签发失败: " + permitResponse.getMessage());
            log.warn("[预检查] publishPermit 签发失败: requestId={}, message={}",
                    request.getRequestId(), permitResponse.getMessage());
        }
    }

    private BasicChecks executeBasicChecks() {
        BasicChecks checks = new BasicChecks();

        try {
            checks.setUkeyAuthenticated(ukeyLifecycleManager.isAuthenticated());
        } catch (Exception e) {
            log.warn("[预检查] UKey 认证检查异常: {}", e.getMessage());
            checks.setUkeyAuthenticated(false);
        }

        try {
            checks.setClientAuthenticated(clientAuthService.isAuthenticated());
        } catch (Exception e) {
            log.warn("[预检查] 客户端认证检查异常: {}", e.getMessage());
            checks.setClientAuthenticated(false);
        }

        try {
            checks.setSigmaProcessBound(!processBindProperties.isEnabled()
                    || processBindService.getAuthorizedPid() > 0);
        } catch (Exception e) {
            log.warn("[预检查] 信发平台进程绑定检查异常: {}", e.getMessage());
            checks.setSigmaProcessBound(false);
        }

        try {
            Map<String, Object> channelStatus = gatewayService.getChannelStatus();
            Object statusObj = channelStatus.get("status");
            boolean ready = statusObj != null && ((Number) statusObj).intValue() >= 0;
            checks.setGatewayReady(ready);
        } catch (Exception e) {
            log.warn("[预检查] 网关链路检查异常: {}", e.getMessage());
            checks.setGatewayReady(false);
        }

        return checks;
    }

    private String buildBasicCheckFailMessage(BasicChecks checks) {
        StringBuilder sb = new StringBuilder("基础检查未通过: ");
        if (!checks.isUkeyAuthenticated()) {
            sb.append("UKey 未认证; ");
        }
        if (!checks.isClientAuthenticated()) {
            sb.append("客户端未认证; ");
        }
        if (!checks.isSigmaProcessBound()) {
            sb.append("信发平台进程未绑定; ");
        }
        if (!checks.isGatewayReady()) {
            sb.append("网关链路不可用; ");
        }
        return sb.toString();
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
