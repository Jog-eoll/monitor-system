package com.infopublish.client.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * publishPermit JWT 工具
 * <p>
 * 使用 JDK 原生 {@link Mac} + HmacSHA256 实现 HS256 JWT 签发与验签，
 * 不引入 jjwt / nimbus-jose 等第三方依赖。
 * </p>
 *
 * <p>JWT 三段式：base64url(header) + "." + base64url(payload) + "." + base64url(signature)</p>
 */
public final class PublishPermitUtil {

    private static final String ALGORITHM = "HS256";
    private static final String HMAC_ALGO = "HmacSHA256";

    /** 固定的 JWT Header */
    private static final String HEADER_JSON =
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HEADER_B64 =
            Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private PublishPermitUtil() {}

    /**
     * 签发 publishPermit JWT
     *
     * @param payloadJson claims JSON 字符串
     * @param secret      HMAC 密钥
     * @return 三段式 JWT
     */
    public static String sign(String payloadJson, String secret) {
        try {
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = HEADER_B64 + "." + payloadB64;
            String signature = hmacSha256Base64Url(signingInput, secret);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("JWT 签发失败", e);
        }
    }

    /**
     * 验签并返回 payload JSON
     *
     * @param token  三段式 JWT
     * @param secret HMAC 密钥
     * @return payload JSON 字符串；验签失败返回 null
     */
    public static String verify(String token, String secret) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        try {
            String signingInput = parts[0] + "." + parts[1];
            String expectedSig = hmacSha256Base64Url(signingInput, secret);
            if (!constantTimeEquals(expectedSig, parts[2])) {
                return null;
            }
            return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JWT 中提取 payload JSON（不验签，仅用于调试）
     */
    public static String decodePayloadUnsafe(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ── 内部方法 ──

    private static String hmacSha256Base64Url(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * 常数时间比较，防止时序攻击
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }
}
