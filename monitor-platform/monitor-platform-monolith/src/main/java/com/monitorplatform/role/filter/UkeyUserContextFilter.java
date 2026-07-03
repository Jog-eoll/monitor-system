package com.monitorplatform.role.filter;

import com.monitorplatform.common.util.SecurityUtils;
import com.monitorplatform.role.entity.AppUser;
import com.monitorplatform.role.mapper.AppUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class UkeyUserContextFilter extends OncePerRequestFilter {

    private final AppUserMapper appUserMapper;

    public UkeyUserContextFilter(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ukeyId = SecurityUtils.getUkeyIdentity(request);
        if (isNotBlank(ukeyId)) {
            resolveUser(request, ukeyId.trim());
        }
        filterChain.doFilter(request, response);
    }

    private void resolveUser(HttpServletRequest request, String ukeyId) {
        try {
            AppUser user = appUserMapper.selectByUkeyId(ukeyId);
            if (user == null) {
                SecurityUtils.setCurrentUser(request, null, ukeyId, ukeyId);
                log.warn("[OperateLog] UKey未绑定平台用户, ukeyId={}, uri={}", ukeyId, request.getRequestURI());
                return;
            }
            SecurityUtils.setCurrentUser(request, user.getId(), user.getUsername(), ukeyId);
        } catch (Exception e) {
            SecurityUtils.setCurrentUser(request, null, ukeyId, ukeyId);
            log.warn("[OperateLog] UKey映射平台用户失败, ukeyId={}, uri={}, error={}",
                    ukeyId, request.getRequestURI(), e.getMessage(), e);
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
