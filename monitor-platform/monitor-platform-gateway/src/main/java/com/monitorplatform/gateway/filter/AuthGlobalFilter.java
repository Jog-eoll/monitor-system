package com.monitorplatform.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.List;

/**
 * 全局 Token 鉴权过滤器（JWT + Redis 黑名单）
 *
 * 校验流程:
 * 1. 路径命中白名单 → 直接放行
 * 2. 无 Authorization 头 → 401
 * 3. 本地解析 JWT（验签 + 过期）→ 失败则 401
 * 4. 查 Redis 黑名单（jti 是否存在）→ 存在则 401
 * 5. 全部通过 → 放行，并将 identity/role 写入请求头供下游使用
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${jwt.secret}")
    private String secret;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private Key signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 白名单路径（Ant 风格），无需 Token 直接放行
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            // 不带前缀
            "/cert/login",
            "/cert/online",
            "/cert/online-status",
            "/cert/validate",
            "/cert/encrypt-key",
            "/cert/disconnect",
            "/cert/parse",
            "/cert/import",
            "/cert/check-token",
            "/cert/lock-status",  // 查询锁定状态（登录页需要）
            // 带 /api 前缀（前端实际请求路径）
            "/api/cert/login",
            "/api/cert/online",
            "/api/cert/online-status",
            "/api/cert/validate",
            "/api/cert/encrypt-key",
            "/api/cert/disconnect",
            "/api/cert/parse",
            "/api/cert/import",
            "/api/cert/check-token",
            "/api/cert/lock-status",  // 查询锁定状态（登录页需要）
            // 服务发现路由路径（/服务名/路径）
            "/monitor-platform-ukey/cert/login",
            "/monitor-platform-ukey/cert/online",
            "/monitor-platform-ukey/cert/online-status",
            "/monitor-platform-ukey/cert/validate",
            "/monitor-platform-ukey/cert/encrypt-key",
            "/monitor-platform-ukey/cert/disconnect",
            "/monitor-platform-ukey/cert/parse",
            "/monitor-platform-ukey/cert/import",
            "/monitor-platform-ukey/cert/check-token",
            "/monitor-platform-ukey/cert/lock-status",  // 查询锁定状态（登录页需要）
            "/monitor-platform-ukey/auth/**",
            "/actuator/**",
            // WebSocket 握手路径（无 Token，直接放行）
            "/ws-endpoint",
            "/ws-endpoint/**",
            "/cert/admin-login",
            "/cert/temporary-authorization-info",
            "/api/cert/admin-login",
            "/api/cert/temporary-authorization-info",
            "/monitor-platform-ukey/cert/admin-login",
            "/monitor-platform-ukey/cert/temporary-authorization-info"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 1: 白名单放行
        for (String pattern : WHITE_LIST) {
            if (pathMatcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }

        // Step 2: 提取 Bearer Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        if (token == null || token.isEmpty()) {
            log.warn("[Auth] 缺少 Token, path={}", path);
            return unauthorized(exchange, "未登录，请先通过 UKey 认证");
        }

        // Step 3: 本地解析 JWT（无网络调用）
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.warn("[Auth] JWT 无效或已过期, path={}, error={}", path, e.getMessage());
            return unauthorized(exchange, "Token 无效或已过期，请重新登录");
        }

        // Step 4: 查 Redis 黑名单
        String jti = claims.getId();
        Boolean inBlacklist = stringRedisTemplate.hasKey(JWT_BLACKLIST_PREFIX + jti);
        if (Boolean.TRUE.equals(inBlacklist)) {
            log.warn("[Auth] Token 已在黑名单(强制下线), jti={}, path={}", jti, path);
            return unauthorized(exchange, "您已被强制下线，请重新登录");
        }

        // Step 5: 校验通过，将身份信息写入请求头透传给下游
        String identity = claims.getSubject();
        String role = claims.get("role", String.class);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Auth-Identity", identity)
                .header("X-Auth-Role", role != null ? role : "")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /** 返回 401 响应 */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** 优先级最高，第一个执行 */
    @Override
    public int getOrder() {
        return -100;
    }
}
