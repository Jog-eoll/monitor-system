package com.monitorplatform.common.util;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class SecurityUtils {

    public static final String HEADER_AUTH_IDENTITY = "X-Auth-Identity";
    public static final String HEADER_AUTH_ROLE = "X-Auth-Role";

    public static final String CURRENT_USER_ID = "CURRENT_USER_ID";
    public static final String CURRENT_USERNAME = "CURRENT_USERNAME";
    public static final String CURRENT_UKEY_IDENTITY = "CURRENT_UKEY_IDENTITY";

    private SecurityUtils() {
    }


    public static HttpServletRequest getRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) attributes).getRequest();
    }

    public static String getUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object userId = request.getAttribute(CURRENT_USER_ID);
        return userId == null ? null : String.valueOf(userId);
    }

    public static String getUsername() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object username = request.getAttribute(CURRENT_USERNAME);
        if (isNotBlank(username)) {
            return String.valueOf(username).trim();
        }
        return getUkeyIdentity(request);
    }

    public static String getUkeyIdentity() {
        return getUkeyIdentity(getRequest());
    }

    public static String getUkeyIdentity(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object ukeyIdentity = request.getAttribute(CURRENT_UKEY_IDENTITY);
        if (isNotBlank(ukeyIdentity)) {
            return String.valueOf(ukeyIdentity).trim();
        }
        String headerIdentity = request.getHeader(HEADER_AUTH_IDENTITY);
        return isBlank(headerIdentity) ? null : headerIdentity.trim();
    }


    public static void setCurrentUser(HttpServletRequest request, Long userId, String username, String ukeyId) {
        if (request == null) {
            return;
        }
        if (userId != null) {
            request.setAttribute(CURRENT_USER_ID, userId);
        }
        if (isNotBlank(username)) {
            request.setAttribute(CURRENT_USERNAME, username.trim());
        }
        if (isNotBlank(ukeyId)) {
            request.setAttribute(CURRENT_UKEY_IDENTITY, ukeyId.trim());
        }
    }

    private static boolean isNotBlank(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
