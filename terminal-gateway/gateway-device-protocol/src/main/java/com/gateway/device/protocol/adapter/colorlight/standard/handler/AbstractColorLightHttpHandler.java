package com.gateway.device.protocol.adapter.colorlight.standard.handler;

import com.fasterxml.jackson.databind.JavaType;
import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.api.CapabilityHandler;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightAccount;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpMethod;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceAuthEntry;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ColorLight HTTP Handler 抽象基类。
 *
 * <p>封装 Netty HTTP 编解码 + 请求/响应发送，提供 GET/POST/PUT/DELETE 快捷方法。
 * 子类实现 {@link #execute(DeviceContext, CommandParams)}。
 *
 * <p>认证信息通过 {@link ColorLightCredentialStore} 按设备持久化复用，
 * 默认出厂凭据 admin:console。端口从 {@link DeviceContext#getPort()} 读取，默认 8989。</p>
 */
@Slf4j
public abstract class AbstractColorLightHttpHandler<P extends CommandParams> implements CapabilityHandler<P> {

    private final DeviceTransport transport;
    private final ColorLightCredentialStore credentialStore;
    private final ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec;
    /**
     * -- SETTER --
     * 设置认证存储（可选，由 Spring 配置注入）。
     * 设置后凭据查找优先使用
     * ，回退到
     * 预配置列表。
     */
    @Setter
    private volatile DeviceAuthStore authStore;

    protected AbstractColorLightHttpHandler(DeviceTransport transport,
                                            ColorLightCredentialStore credentialStore,
                                            ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        this.transport = transport;
        this.credentialStore = credentialStore;
        this.codec = codec;
    }

    /**
     * 将 POJO 序列化为 UTF-8 字节数组，用于 HTTP 请求体。
     */
    protected static byte[] serializeBody(Object pojo) {
        try {
            return JsonCustomMapper.get().writeValueAsBytes(pojo);
        } catch (Exception e) {
            log.error("Failed to serialize request body: {}", e.getMessage());
            return new byte[0];
        }
    }

    // ── JSON 序列化/反序列化辅助方法 ──

    /**
     * HTTP 状态码 → 可读文本。
     */
    private static String statusText(int code) {
        switch (code) {
            case HttpURLConnection.HTTP_OK:
                return "OK";
            case HttpURLConnection.HTTP_CREATED:
                return "Created";
            case HttpURLConnection.HTTP_ACCEPTED:
                return "Accepted";
            case HttpURLConnection.HTTP_NO_CONTENT:
                return "No Content";
            case HttpURLConnection.HTTP_BAD_REQUEST:
                return "Bad Request";
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                return "Unauthorized";
            case HttpURLConnection.HTTP_FORBIDDEN:
                return "Forbidden";
            case HttpURLConnection.HTTP_NOT_FOUND:
                return "Not Found";
            case HttpURLConnection.HTTP_CONFLICT:
                return "Conflict";
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                return "Internal Error";
            case HttpURLConnection.HTTP_UNAVAILABLE:
                return "Unavailable";
            default:
                return "";
        }
    }

    /**
     * 将响应体解析为指定类型的 POJO。
     */
    protected <T> T parseJson(ColorLightHttpResponse response, Class<T> targetType) {
        if (response == null || response.getBody() == null || response.getBody().length == 0) return null;
        try {
            return JsonCustomMapper.get().readValue(response.getBody(), targetType);
        } catch (Exception e) {
            log.error("Failed to parse JSON response as {}: {}", targetType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 将响应体解析为指定元素类型的列表（用于 JSON 数组响应）。
     */
    protected <T> List<T> parseJsonArray(ColorLightHttpResponse response, Class<T> elementType) {
        if (response == null || response.getBody() == null || response.getBody().length == 0) return null;
        try {
            JavaType listType = JsonCustomMapper.get().getTypeFactory()
                    .constructCollectionType(List.class, elementType);
            return JsonCustomMapper.get().readValue(response.getBody(), listType);
        } catch (Exception e) {
            log.error("Failed to parse JSON response as list of {}: {}", elementType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    protected ColorLightHttpResponse get(DeviceContext device, String uri) {
        return executeHttp(device, buildRequest(device, ColorLightHttpMethod.GET, uri, null));
    }

    // ── 枚举驱动的统一 send 方法 ──

    protected ColorLightHttpResponse put(DeviceContext device, String uri, byte[] body) {
        return executeHttp(device, buildRequest(device, ColorLightHttpMethod.PUT, uri, body));
    }

    protected ColorLightHttpResponse post(DeviceContext device, String uri, byte[] body) {
        return executeHttp(device, buildRequest(device, ColorLightHttpMethod.POST, uri, body));
    }

    /**
     * 发送 multipart/form-data POST 请求。
     */
    protected ColorLightHttpResponse postMultipart(DeviceContext device, String uri, byte[] body, String boundary) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        return executeHttp(device, buildRequest(device, ColorLightHttpMethod.POST, uri, body, headers));
    }

    // ── JSON 请求体辅助方法 ──

    protected ColorLightHttpResponse delete(DeviceContext device, String uri) {
        return executeHttp(device, buildRequest(device, ColorLightHttpMethod.DELETE, uri, null));
    }

    /**
     * 发送请求（无请求体：GET/DELETE）。
     */
    protected ColorLightHttpResponse send(DeviceContext device, ColorLightApi api) {
        return executeHttp(device, ColorLightHttpRequest.builder()
                .method(api.method())
                .uri(api.path())
                .host(device.getIp())
                .authorization(resolveAuthorization(device))
                .build());
    }

    // ── 内部方法 ──

    /**
     * 发送请求（带请求体：POST/PUT）。
     */
    protected ColorLightHttpResponse send(DeviceContext device, ColorLightApi api, byte[] body) {
        return executeHttp(device, ColorLightHttpRequest.builder()
                .method(api.method())
                .uri(api.path())
                .host(device.getIp())
                .body(body)
                .authorization(resolveAuthorization(device))
                .build());
    }

    private ColorLightHttpRequest buildRequest(DeviceContext device, ColorLightHttpMethod method, String uri, byte[] body) {
        return buildRequest(device, method, uri, body, null);
    }

    private ColorLightHttpRequest buildRequest(DeviceContext device, ColorLightHttpMethod method,
                                               String uri, byte[] body, Map<String, String> headers) {
        ColorLightHttpRequest.ColorLightHttpRequestBuilder builder = ColorLightHttpRequest.builder()
                .method(method)
                .uri(uri)
                .host(device.getIp())
                .body(body)
                .authorization(resolveAuthorization(device));
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }
        return builder.build();
    }

    // ── 凭据管理辅助（子类可用） ──

    /**
     * 解析设备 Authorization 头值。
     * 优先级: {@link DeviceAuthStore}（已持久化凭据/无需认证标记）→ {@link ColorLightCredentialStore}（预配置回退）
     *
     * <p>authStore 中存在 entry（含全 null 的无需认证标记）时不再回退预配置。
     * 返回 null 表示不发送 Authorization 头（无需认证设备）。</p>
     */
    private String resolveAuthorization(DeviceContext device) {
        if (device == null) {
            return null;
        }
        String deviceId = device.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            return null;
        }
        // 1. authStore 有 entry → 直接使用（含全 null = 无需认证）
        if (authStore != null) {
            DeviceAuthEntry entry = authStore.get(deviceId);
            if (entry != null) {
                return entry.toAuthorizationHeader(); // null = 无需认证
            }
        }
        // 2. 回退厂商预配置（仅注册前未持久化时）
        ColorLightAccount account = credentialStore.get(deviceId);
        return account != null ? account.toAuthorizationHeader() : null;
    }

    /**
     * 持久化设备凭据（认证通过后调用，委托给 {@link DeviceAuthStore#update}）。
     */
    protected void saveCredentials(DeviceContext device, String accountId, String password) {
        if (authStore == null) return;
        String deviceId = device.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) return;
        authStore.update(deviceId, DeviceAuthEntry.builder()
                .accountId(accountId).password(password).build());
    }

    private ColorLightHttpResponse executeHttp(DeviceContext device, ColorLightHttpRequest request) {
        String host = device.getIp();
        int port = device.getPort() > 0 ? device.getPort() : VendorDefaultPort.COLOR_LIGHT_STANDARD.getPort();
        String target = host + ":" + port;
        int bodySize = request.getBody() != null ? request.getBody().length : 0;
        String contentType = request.getHeaders() != null
                ? request.getHeaders().getOrDefault("Content-Type", "application/json")
                : "application/json";
        log.debug("[{}] > HTTP {} {}  Content-Type={}  body={}B  timeout={} body={}",
                target, request.getMethod(), request.getUri(), contentType, bodySize, getTimeout(),
                LittleEndianByteBufUtils.toString(request.getBody()));
        long startTime = System.currentTimeMillis();
        try {
            byte[] reqBytes = codec.encode(request);
            if (log.isDebugEnabled()) {
                log.debug("[{}] >>> transport request hex dump ({} bytes): {}",
                        device.getVendor(), reqBytes.length,
                        LittleEndianByteBufUtils.toHex(reqBytes));
            }
            byte[] respBytes = transport.sendAndReceive(device, reqBytes, getTimeout())
                    .get(getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (log.isDebugEnabled()) {
                log.debug("[{}] >>> transport response hex dump ({} bytes): {}",
                        device.getVendor(), respBytes.length,
                        LittleEndianByteBufUtils.toHex(respBytes));
            }
            ColorLightHttpResponse response = codec.decode(respBytes);
            long elapsed = System.currentTimeMillis() - startTime;
            if (response != null) {
                int respBodySize = response.getBody() != null ? response.getBody().length : 0;
                log.debug("[{}] < HTTP {} {} → {} {}  body={}B  wire={}B  elapsed={}ms body={}",
                        target, request.getMethod(), request.getUri(),
                        response.getStatusCode(), statusText(response.getStatusCode()),
                        respBodySize, respBytes != null ? respBytes.length : 0, elapsed,
                        LittleEndianByteBufUtils.toString(response.getBody()));
            } else {
                log.debug("[{}] < HTTP {} {} → FAILED  elapsed={}ms",
                        target, request.getMethod(), request.getUri(), elapsed);
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] HTTP {} {} interrupted", target, request.getMethod(), request.getUri());
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            log.error("[{}] HTTP {} {} failed: {}", target, request.getMethod(), request.getUri(), detail);
            return null;
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[{}] HTTP {} {} timeout after {} (elapsed={}ms)",
                    target, request.getMethod(), request.getUri(), getTimeout(), elapsed);
            return null;
        }
    }

    /**
     * 命令超时，子类可按需覆盖。默认 10 秒。
     */
    protected Duration getTimeout() {
        return Duration.ofSeconds(10);
    }

    // ── 结果辅助方法 ──

    protected CommandResult successResult() {
        return CommandResult.success();
    }

    protected CommandResult successResult(Object data) {
        return CommandResult.success(data);
    }

    protected CommandResult failureResult(String code, String message) {
        return CommandResult.failure(code, message);
    }

    protected DeviceTransport transport() {
        return transport;
    }

    protected ColorLightCredentialStore credentialStore() {
        return credentialStore;
    }
}
