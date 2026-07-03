package com.infopublish.client.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Sigma 公共请求头解析过滤器
 * <p>
 * 解析 Sigma 对接文档 Section 6.1 定义的公共请求头，
 * 并将值存入 ThreadLocal 供下游服务使用。
 * </p>
 *
 * <p>解析的请求头：</p>
 * <ul>
 *     <li>X-SP-Client-Id — 调用方 ID</li>
 *     <li>X-SP-Request-Id — 请求唯一 ID</li>
 *     <li>X-SP-Timestamp — ISO8601 时间</li>
 *     <li>Authorization — Bearer Token（第一阶段仅记录不校验）</li>
 * </ul>
 */
@Slf4j
@Component
public class SpRequestHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        String clientId = request.getHeader("X-SP-Client-Id");
        String requestId = request.getHeader("X-SP-Request-Id");
        String timestamp = request.getHeader("X-SP-Timestamp");
        String authorization = request.getHeader("Authorization");

        // 存入 ThreadLocal
        SpRequestContext.set(clientId, requestId, timestamp, authorization);

        if (clientId != null || requestId != null) {
            log.debug("[SP请求头] clientId={}, requestId={}, timestamp={}", clientId, requestId, timestamp);
        }

        try {
            chain.doFilter(servletRequest, servletResponse);
        } finally {
            // 清理 ThreadLocal 防止内存泄漏
            SpRequestContext.clear();
        }
    }
}
