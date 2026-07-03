package com.monitorplatform.ukey.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.ukey.entity.dto.AuthExchangeRequestDTO;
import com.monitorplatform.ukey.service.VAuthAuthServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * VAuth 认证服务端 HTTP 接口
 *
 * 提供给监管客户端(info-publish-client)调用：
 *  - POST /auth/server/request
 *  - POST /auth/server/verify
 *  - POST /auth/server/error （可选）
 */
@Slf4j
@RestController
@RequestMapping("/auth/server")
@RequiredArgsConstructor
public class VAuthServerController {

    private final VAuthAuthServerService authServerService;

    /**
     * 第一步：处理客户端认证请求
     */
    @PostMapping("/request")
    public Result<?> handleAuthRequest(@RequestBody AuthExchangeRequestDTO request) {
        log.info("[VAuthServer] 收到认证请求, authId={}", request.getAuthId());
        try {
            String reply = authServerService.handleAuthRequest(request.getAuthId(), request.getRequestData());
            return Result.data(reply);
        } catch (Exception e) {
            log.error("[VAuthServer] 处理认证请求异常", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 第二步：处理客户端认证信息
     */
    @PostMapping("/verify")
    public Result<?> handleAuthVerify(@RequestBody AuthExchangeRequestDTO request) {
        log.info("[VAuthServer] 收到认证验证请求, authId={}", request.getAuthId());
        try {
            String reply = authServerService.handleAuthVerify(request.getAuthId(), request.getClientCert(), request.getRequestData());
            return Result.data(reply);
        } catch (Exception e) {
            log.error("[VAuthServer] 处理认证验证异常", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 第三步（可选）：处理客户端上报的认证错误
     */
    @PostMapping("/error")
    public Result<?> handleAuthError(@RequestBody Map<String, String> body) {
        String errorInfo = body.get("errorInfo");
        log.info("[VAuthServer] 收到认证错误上报");
        try {
            String detail = authServerService.handleAuthError(errorInfo);
            return Result.data(detail);
        } catch (Exception e) {
            log.error("[VAuthServer] 处理认证错误异常", e);
            return Result.fail(e.getMessage());
        }
    }

    // ==================== 密钥/证书分发接口（供解密侧回调使用） ====================

    /**
     * 查询加密后的会话密钥
     * <p>解密侧在 QueryKeyCallback 中调用此接口获取加密方的会话密钥。
     * 密钥会用请求方的证书公钥加密，只有持有对应私钥的设备才能使用。
     *
     * @param body srcAuthId - 加密方认证ID，ver - 密钥版本，requestAuthId - 请求方认证ID
     */
    @PostMapping("/key/query")
    public Result<?> queryKey(@RequestBody Map<String, String> body) {
        String srcAuthId = body.get("srcAuthId");
        String ver = body.get("ver");
        String requestAuthId = body.get("requestAuthId");
        String requestCert = body.get("requestCert"); // 请求方直接提供自身证书，优先用于加密密钥
        log.info("[VAuthServer] 收到密钥查询: srcAuthId={}, ver={}, requestAuthId={}, hasCert={}", srcAuthId, ver, requestAuthId, requestCert != null && !requestCert.isEmpty());
        try {
            String encryptedKey = authServerService.queryEncryptedKey(srcAuthId, ver, requestAuthId, requestCert);
            return Result.data(encryptedKey);
        } catch (Exception e) {
            log.error("[VAuthServer] 密钥查询失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 查询客户端签名证书
     * <p>解密侧在 QueryCerCallback 中调用此接口获取加密方的签名证书用于验签。
     *
     * @param body authId - 需要查询证书的认证ID
     */
    @PostMapping("/cert/query")
    public Result<?> queryCert(@RequestBody Map<String, String> body) {
        String authId = body.get("authId");
        log.info("[VAuthServer] 收到证书查询: authId={}", authId);
        try {
            String cert = authServerService.queryCertificate(authId);
            if (cert == null || cert.isEmpty()) {
                return Result.fail(404, "未找到证书: " + authId);
            } else {
                return Result.data(cert);
            }
        } catch (Exception e) {
            log.error("[VAuthServer] 证书查询失败", e);
            return Result.fail(e.getMessage());
        }
    }
}
