package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.service.SvacFileCryptoService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Isolated SVAC file-content diagnostics.
 *
 * <p>These endpoints are intentionally not used by the normal publish/control
 * delivery path. They allow us to validate VAuth file/pack crypto on complete
 * file bytes without changing the existing GM package encryption chain.</p>
 *
 * <p>The default diagnostic algorithm is SVAC_PACK, which maps to
 * VAuth_EncryptPackData / VAuth_DecryptPackData.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/crypto/svac/file")
public class SvacFileCryptoController {

    @Resource
    private ObjectProvider<SvacFileCryptoService> svacFileCryptoServiceProvider;

    @PostMapping("/encrypt")
    public Map<String, Object> encrypt(@RequestBody SvacFileCryptoRequest request) {
        return execute("encrypt", request);
    }

    @PostMapping("/decrypt")
    public Map<String, Object> decrypt(@RequestBody SvacFileCryptoRequest request) {
        return execute("decrypt", request);
    }

    private Map<String, Object> execute(String operation, SvacFileCryptoRequest request) {
        Map<String, Object> result = new HashMap<>();
        SvacFileCryptoService service = svacFileCryptoServiceProvider.getIfAvailable();
        if (service == null) {
            result.put("success", false);
            result.put("message", "SVAC real SDK service is not available; check vauth.mock-mode=false");
            return result;
        }
        if (request == null || request.getContentBase64() == null || request.getContentBase64().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "contentBase64 cannot be empty");
            return result;
        }
        if (!service.isReady()) {
            result.put("success", false);
            result.put("message", "SVAC service is not ready; UKey authentication is not complete");
            return result;
        }

        String algorithm = normalizeAlgorithm(request.getAlgorithm());
        byte[] input;
        try {
            input = Base64.getDecoder().decode(request.getContentBase64());
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "contentBase64 is invalid: " + e.getMessage());
            return result;
        }

        byte[] output;
        if ("SVAC_PACK".equals(algorithm)) {
            output = "encrypt".equals(operation) ? service.encryptPackData(input) : service.decryptPackData(input);
        } else {
            result.put("success", false);
            result.put("message", "unsupported algorithm: " + algorithm + "; use SVAC_PACK");
            return result;
        }

        if (output == null || output.length == 0) {
            result.put("success", false);
            result.put("message", "SVAC " + operation + " returned empty output");
            result.put("algorithm", algorithm);
            result.put("inputSize", input.length);
            result.put("inputSha256", sha256Hex(input));
            return result;
        }

        result.put("success", true);
        result.put("operation", operation);
        result.put("algorithm", algorithm);
        result.put("inputSize", input.length);
        result.put("outputSize", output.length);
        result.put("inputSha256", sha256Hex(input));
        result.put("outputSha256", sha256Hex(output));
        result.put("contentBase64", Base64.getEncoder().encodeToString(output));
        log.info("[SVAC file diagnostic] {} success: algorithm={}, inputSize={}, outputSize={}",
                operation, algorithm, input.length, output.length);
        return result;
    }

    private String normalizeAlgorithm(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "SVAC_PACK";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Data
    public static class SvacFileCryptoRequest {
        /**
         * SVAC_PACK. Defaults to SVAC_PACK.
         */
        private String algorithm;

        /**
         * Complete file bytes, base64 encoded.
         */
        private String contentBase64;
    }
}
