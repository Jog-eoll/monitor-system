package com.infopublish.client.config;

/**
 * Sigma 公共请求头上下文
 * <p>
 * 基于 ThreadLocal 存储当前请求的 Sigma 公共请求头信息，
 * 供下游 Service 层按需读取。
 * </p>
 */
public class SpRequestContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    public static void set(String clientId, String requestId, String timestamp, String authorization) {
        HOLDER.set(new Context(clientId, requestId, timestamp, authorization));
    }

    public static String getClientId() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.clientId : null;
    }

    public static String getRequestId() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.requestId : null;
    }

    public static String getTimestamp() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.timestamp : null;
    }

    public static String getAuthorization() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.authorization : null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static class Context {
        final String clientId;
        final String requestId;
        final String timestamp;
        final String authorization;

        Context(String clientId, String requestId, String timestamp, String authorization) {
            this.clientId = clientId;
            this.requestId = requestId;
            this.timestamp = timestamp;
            this.authorization = authorization;
        }
    }
}
