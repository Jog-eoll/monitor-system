package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.service.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 发布任务包加密入口。
 * Sigma 或客户端在 precheck/verify 通过后，可先把明文任务 JSON 加密，再提交安全投递任务。
 */
@Slf4j
@RestController
@RequestMapping("/api/crypto/publish-task")
public class PublishTaskCryptoController {

    @Resource
    private CryptoService cryptoService;

    @PostMapping("/encrypt")
    public Map<String, Object> encrypt(@RequestBody String plainTaskJson) {
        Map<String, Object> result = new HashMap<>();
        if (plainTaskJson == null || plainTaskJson.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "明文发布任务不能为空");
            return result;
        }

        try {
            byte[] encrypted = cryptoService.encrypt(plainTaskJson.getBytes(StandardCharsets.UTF_8));
            String encryptedPackageId = "PKG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            result.put("success", true);
            result.put("encryptedPackageId", encryptedPackageId);
            result.put("encryptedPackage", Base64.getEncoder().encodeToString(encrypted));
            result.put("algorithm", "gateway-configured");
            result.put("createdAt", System.currentTimeMillis());
            log.info("[任务包加密] 完成: encryptedPackageId={}, plainSize={}, encryptedSize={}",
                    encryptedPackageId, plainTaskJson.length(), encrypted.length);
            return result;
        } catch (Exception e) {
            log.error("[任务包加密] 失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "任务包加密失败: " + e.getMessage());
            return result;
        }
    }
}
