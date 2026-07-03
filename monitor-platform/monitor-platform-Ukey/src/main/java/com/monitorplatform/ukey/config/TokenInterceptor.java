package com.monitorplatform.ukey.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Token 鉴权拦截器
 * 从请求头 Authorization 中提取 Token 并校验
 * 格式: Authorization: Bearer <token>
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Resource
    private TokenStore tokenStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");

        // 提取 Bearer Token
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (!tokenStore.isValid(token)) {
            log.warn("[Token校验] 无效Token或未登录, uri={}, token={}", request.getRequestURI(), token);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> result = new HashMap<>();
            result.put("code", 401);
            result.put("msg", "未登录或Token已失效，请重新登录");
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }

        log.debug("[Token校验] 通过, uri={}, identity={}", request.getRequestURI(), tokenStore.getIdentity(token));
        return true;
    }
}
