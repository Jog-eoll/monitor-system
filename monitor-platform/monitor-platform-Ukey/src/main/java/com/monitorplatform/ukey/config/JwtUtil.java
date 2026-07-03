package com.monitorplatform.ukey.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 * 负责签发、解析 JWT Token。
 * Payload 包含:
 *   sub  - 身份标识（certSerialNo 或 "ADMIN"）
 *   role - 角色（"UKEY" 或 "ADMIN"）
 *   jti  - Token 唯一 ID（用于 Redis 黑名单）
 *   iat  - 签发时间
 *   exp  - 过期时间
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:8}")
    private int expireHours;

    private Key signingKey;

    @PostConstruct
    public void init() {
        // 密钥长度不足 256 bit 时自动补全
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 JWT
     *
     * @param identity 身份标识，UKey登录传 certSerialNo，管理员登录传 "ADMIN"
     * @param role     角色，"UKEY" 或 "ADMIN"
     * @return JWT 字符串
     */
    public String generate(String identity, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (long) expireHours * 3600 * 1000);
        return Jwts.builder()
                .setId(UUID.randomUUID().toString().replace("-", ""))
                .setSubject(identity)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT，返回 Claims
     * 签名错误或过期时抛出 JwtException
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 提取 jti（Token 唯一 ID）
     */
    public String getJti(String token) {
        return parse(token).getId();
    }

    /**
     * 提取身份标识（certSerialNo 或 "ADMIN"）
     */
    public String getIdentity(String token) {
        return parse(token).getSubject();
    }
}
