package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.sigma.SigmaPublishStatusResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyResponse;
import com.alibaba.fastjson2.JSON;
import com.infopublish.client.config.AppConfig;
import com.infopublish.client.log.DiagnosticLogReport;
import com.infopublish.client.log.DiagnosticLogReporter;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.GatewayService;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.SigmaPublishService;
import com.infopublish.client.service.UkeyLifecycleManager;
import com.infopublish.client.utils.PlaylistDigestUtil;
import com.infopublish.client.utils.PublishPermitUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 信发平台安全发布服务实现
 */
@Slf4j
@Service
public class SigmaPublishServiceImpl implements SigmaPublishService {

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

    @Value("${secure-publish.permit.issuer:info-publish-client}")
    private String permitIssuer;

    @Value("${secure-publish.permit.client-id:default-client}")
    private String permitClientId;

    @Value("${secure-publish.permit.hmac-secret:change-me-in-production}")
    private String hmacSecret;

    @Value("${secure-publish.permit.expire-seconds:3600}")
    private int expireSeconds;

    @javax.annotation.Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    @Override
    public SigmaPublishStatusResponse getStatus() {
        SigmaPublishStatusResponse resp = new SigmaPublishStatusResponse();
        resp.setUkeyAuthenticated(ukeyLifecycleManager.isAuthenticated());
        resp.setClientAuthenticated(clientAuthService.isAuthenticated());
        resp.setSigmaProcessBound(isProcessBoundOrDisabled());

        try {
            Map<String, Object> channelStatus = gatewayService.getChannelStatus();
            resp.setGatewayReady(channelStatus != null && !channelStatus.isEmpty());
        } catch (Exception e) {
            log.warn("[状态查询] 网关状态查询失败: {}", e.getMessage());
            resp.setGatewayReady(false);
        }

        resp.setSecurePublishEnabled(true);
        log.info("[状态查询] ready={}, ukey={}, client={}, sigma={}, gateway={}",
                resp.isReadyForPublish(), resp.isUkeyAuthenticated(),
                resp.isClientAuthenticated(), resp.isSigmaProcessBound(), resp.isGatewayReady());
        return resp;
    }

    @Override
    public SigmaVerifyResponse verify(SigmaVerifyRequest request) {
        log.info("[publishPermit] 兼容 verify 入口请求: requestId={}, playlistId={}, items={}",
                request.getRequestId(), request.getPlaylistId(),
                request.getItems() != null ? request.getItems().size() : 0);

        return issuePermit(request);
    }

    @Override
    public SigmaVerifyResponse issuePermit(SigmaVerifyRequest request) {
        log.info("[publishPermit] 开始签发: requestId={}, playlistId={}, items={}",
                request.getRequestId(), request.getPlaylistId(),
                request.getItems() != null ? request.getItems().size() : 0);

        // 1. 基本状态检查
        if (!ukeyLifecycleManager.isAuthenticated()) {
            reportPermitSigned(request, null, null, null, null, "UKey 未认证");
            return SigmaVerifyResponse.fail("UKey 未认证", request.getRequestId());
        }
        if (!clientAuthService.isAuthenticated()) {
            reportPermitSigned(request, null, null, null, null, "客户端未认证");
            return SigmaVerifyResponse.fail("客户端未认证", request.getRequestId());
        }
        if (!isProcessBoundOrDisabled()) {
            reportPermitSigned(request, null, null, null, null, "信发平台进程未绑定");
            return SigmaVerifyResponse.fail("信发平台进程未绑定", request.getRequestId());
        }

        // 2. 生成 playlistDigest
        List<PlaylistDigestUtil.Item> digestItems = new ArrayList<>();
        if (request.getItems() != null) {
            for (SigmaVerifyRequest.PlaylistItem src : request.getItems()) {
                PlaylistDigestUtil.Item it = new PlaylistDigestUtil.Item();
                it.orderNo = src.getOrderNo();
                it.fileName = src.getFileName();
                it.fileType = src.getFileType();
                it.fileUrl = src.getFileUrl();
                it.durationSeconds = src.getDurationSeconds();
                it.fileHash = src.getFileHash();
                digestItems.add(it);
            }
        }

        String deviceId = null, ip = null;
        Integer port = null;
        if (request.getTarget() != null) {
            deviceId = request.getTarget().getDeviceId();
            ip = request.getTarget().getIp();
            port = request.getTarget().getPort();
        }

        String playlistDigest = PlaylistDigestUtil.digest(
                request.getPlaylistId(), deviceId, ip, port, digestItems);
        log.info("[publishPermit] playlistDigest={}", playlistDigest);

        // 3. 签发 publishPermit (HS256 JWT)
        long now = System.currentTimeMillis() / 1000;
        String jti = UUID.randomUUID().toString();

        StringBuilder claims = new StringBuilder("{");
        claims.append("\"jti\":\"").append(jti).append("\"");
        claims.append(",\"requestId\":\"").append(escapeJson(request.getRequestId())).append("\"");
        String precheckId = request.getPrecheckId() != null && !request.getPrecheckId().isEmpty()
                ? request.getPrecheckId() : request.getRequestId();
        claims.append(",\"precheckId\":\"").append(escapeJson(precheckId)).append("\"");
        if (request.getOperatorId() != null) {
            claims.append(",\"operatorId\":\"").append(escapeJson(request.getOperatorId())).append("\"");
        }
        if (deviceId != null || ip != null || port != null) {
            claims.append(",\"target\":{");
            claims.append("\"deviceId\":\"").append(escapeJson(deviceId)).append("\"");
            claims.append(",\"ip\":\"").append(escapeJson(ip)).append("\"");
            claims.append(",\"port\":").append(port != null ? port : 0);
            claims.append("}");
        }
        claims.append(",\"playlistDigest\":\"").append(playlistDigest).append("\"");
        claims.append(",\"iat\":").append(now);
        claims.append(",\"exp\":").append(now + expireSeconds);
        claims.append(",\"iss\":\"").append(escapeJson(permitIssuer)).append("\"");
        claims.append(",\"clientId\":\"").append(escapeJson(permitClientId)).append("\"");
        claims.append("}");

        String publishPermit = PublishPermitUtil.sign(claims.toString(), hmacSecret);
        log.info("[publishPermit] 已签发: jti={}, exp=+{}s", jti, expireSeconds);

        reportPermitSigned(request, jti, playlistDigest, request.getOperatorId(),
                deviceId, null);
        return SigmaVerifyResponse.pass(publishPermit, playlistDigest,
                request.getRequestId(), precheckId);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void reportPermitSigned(SigmaVerifyRequest request, String jti, String playlistDigest,
                                     String operatorId, String targetDeviceId, String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setTraceId(request != null ? defaultStr(request.getRequestId(), "") : "");
            report.setEventType("CLIENT_PUBLISH_PERMIT_SIGNED");
            report.setEventLevel(errorMessage != null ? "error" : "info");
            report.setStage("client");
            report.setContentId(request != null ? defaultStr(request.getPlaylistId(), "") : "");
            report.setSignStatus(errorMessage != null ? "fail" : "success");
            report.setResultStatus(errorMessage != null ? "fail" : "success");
            report.setOperatorId(defaultStr(operatorId, permitIssuer));
            report.setOperatorName(defaultStr(operatorId, permitIssuer));
            if (request != null && request.getTarget() != null) {
                report.setBoardIp(request.getTarget().getIp());
                report.setBoardPort(request.getTarget().getPort());
            }
            report.setSummary(errorMessage != null ? "签发发布许可失败" : "签发发布许可成功");
            report.setErrorMessage(errorMessage);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("jti", defaultStr(jti, ""));
            detail.put("requestId", request != null ? defaultStr(request.getRequestId(), "") : "");
            detail.put("playlistId", request != null ? defaultStr(request.getPlaylistId(), "") : "");
            detail.put("playlistDigest", defaultStr(playlistDigest, ""));
            if (request != null && request.getOperatorId() != null) {
                detail.put("operatorId", request.getOperatorId());
            }
            if (targetDeviceId != null) {
                detail.put("target", targetDeviceId);
            } else if (request != null && request.getTarget() != null) {
                detail.put("target", defaultStr(request.getTarget().getDeviceId(),
                        defaultStr(request.getTarget().getIp(), "")));
            }
            detail.put("expireSeconds", expireSeconds);
            if (errorMessage != null) {
                detail.put("error", errorMessage);
            }
            report.setDetailJson(JSON.toJSONString(detail));
            report.setDedupKey(request != null ? "permit:" + defaultStr(request.getRequestId(), UUID.randomUUID().toString()) : "permit:" + jti);
            report.setRefId(jti);
            report.setRefTable("publish_permit");

            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.debug("[publishPermit] report permit signed failed: {}", e.getMessage());
        }
    }

    private static String defaultStr(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String defaultStr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? fallback : s;
    }

    private boolean isProcessBoundOrDisabled() {
        return !processBindProperties.isEnabled() || processBindService.getAuthorizedPid() > 0;
    }
}
