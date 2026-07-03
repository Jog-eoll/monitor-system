package com.gateway.device.protocol.adapter.colorlight.standard;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightAccount;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.DeviceInfo;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.DimensionInfo;
import com.gateway.device.protocol.base.colorlight.standard.model.api.response.IfStatus;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.DeviceAuthEntry;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ColorLight 设备注册信息提供者 —— 独立于 {@code DeviceInfoGetHandler}。
 *
 * <p>调用 /api/info.json + /api/ifstatus.json + /api/dimension.json，
 * 合并有效字段到 {@link ObjectNode} 返回，供 AutoDiscoveryService 注册使用。</p>
 */
@Slf4j
public class ColorLightDeviceRegistrationProvider implements DeviceRegistrationProvider {

    private final DeviceTransport transport;
    private final ColorLightCredentialStore credentialStore;
    private final ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec;
    private volatile DeviceAuthStore authStore;
    /**
     * 注册过程中验证通过的凭据，由 postRegister 消费后清理
     */
    private volatile ColorLightAccount validatedAccount;

    public ColorLightDeviceRegistrationProvider(DeviceTransport transport,
                                                ColorLightCredentialStore credentialStore,
                                                ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec) {
        this.transport = transport;
        this.credentialStore = credentialStore;
        this.codec = codec;
    }

    public void setAuthStore(DeviceAuthStore authStore) {
        this.authStore = authStore;
    }

    @Override
    public void postRegister(DeviceContext device) {
        ColorLightAccount account = this.validatedAccount;
        if (authStore != null && account != null) {
            authStore.update(device.getDeviceId(), DeviceAuthEntry.builder()
                    .accountId(account.getAccountId())
                    .password(account.getPassword())
                    .build());
            this.validatedAccount = null;
        }
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.COLOR_LIGHT_STANDARD;
    }

    @Override
    public Object fetchRegistrationInfo(DeviceContext device) {
        // 1. info.json — 凭据遍历认证
        ColorLightAccount firstAccount = lookupAccount(device);
        ColorLightHttpResponse resp = sendWithAccount(ColorLightApi.DEVICE_INFO, device, firstAccount);

        if (resp == null) {
            log.warn("[{}] 注册信息获取失败（网络错误）: /api/info.json", device.getIp());
            return null;
        }

        ColorLightAccount validAccount = firstAccount;
        if (resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
            log.debug("[{}] 凭据 {}:*** 返回 HTTP {}，尝试预配置列表其他凭据",
                    device.getIp(), firstAccount.getAccountId(), resp.getStatusCode());
            validAccount = null;
            for (ColorLightAccount account : credentialStore.getPreconfigured()) {
                if (firstAccount.credentialsMatch(account.getAccountId(), account.getPassword())) {
                    continue;
                }
                resp = sendWithAccount(ColorLightApi.DEVICE_INFO, device, account);
                if (resp == null) {
                    log.warn("[{}] 注册信息获取失败（网络错误）: /api/info.json", device.getIp());
                    return null;
                }
                if (resp.getStatusCode() == HttpURLConnection.HTTP_OK) {
                    validAccount = account;
                    break;
                }
                log.debug("[{}] 凭据 {}:*** 认证失败 (HTTP {})，尝试下一个",
                        device.getIp(), account.getAccountId(), resp.getStatusCode());
            }
        }

        if (validAccount == null) {
            log.warn("[{}] 注册信息获取失败: 所有凭据均未通过认证", device.getIp());
            return null;
        }

        // 2. 解析 info.json
        ObjectNode root = JsonCustomMapper.get().createObjectNode();
        DeviceInfo.Wrapper wrapper = parseJson(resp, DeviceInfo.Wrapper.class);
        DeviceInfo info = wrapper != null ? wrapper.getInfo() : null;
        if (info == null) {
            log.warn("[{}] 注册信息解析失败: /api/info.json", device.getIp());
            return null;
        }

        // 3. 暂存已验证凭据（待注册完成后由 postRegister 持久化）
        this.validatedAccount = validAccount;

        root.put("sn", info.getSerialno());
        root.put("model", info.getModel());
        root.put("vername", info.getVername());
        if (info.getUp() != null) root.put("up", info.getUp());
        if (info.getMem() != null) {
            root.put("memTotal", info.getMem().getTotal());
            root.put("memFree", info.getMem().getFree());
        }
        if (info.getStorage() != null) {
            root.put("storageTotal", info.getStorage().getTotal());
            root.put("storageFree", info.getStorage().getFree());
        }
        if (info.getPlaying() != null) {
            root.put("playingName", info.getPlaying().getName());
            root.put("playingSource", info.getPlaying().getSource());
        }

        // 4. ifstatus.json — MAC 地址
        resp = sendWithAccount(ColorLightApi.IF_STATUS, device, validAccount);
        if (resp != null && resp.getStatusCode() == 200) {
            IfStatus ifStatus = parseJson(resp, IfStatus.class);
            if (ifStatus != null) {
                String mac = ifStatus.findLanMac();
                if (mac != null) root.put("mac", mac);
            }
        }

        // 5. dimension.json — 屏幕宽高
        resp = sendWithAccount(ColorLightApi.DIMENSION, device, validAccount);
        if (resp != null && resp.getStatusCode() == 200) {
            DimensionInfo dim = parseJson(resp, DimensionInfo.class);
            if (dim != null) {
                if (dim.getWidth() != null) root.put("width", dim.getWidth());
                if (dim.getHeight() != null) root.put("height", dim.getHeight());
            }
        }

        return root;
    }

    // ── HTTP 执行（独立实现，不依赖 Handler 基类） ──

    /**
     * 使用指定凭据发送 HTTP 请求。
     */
    private ColorLightHttpResponse sendWithAccount(ColorLightApi api, DeviceContext device, ColorLightAccount account) {
        ColorLightHttpRequest request = ColorLightHttpRequest.builder()
                .method(api.method())
                .uri(api.path())
                .host(device.getIp())
                .authorization(account.toAuthorizationHeader())
                .build();
        return executeHttp(device, request);
    }

    private ColorLightHttpResponse executeHttp(DeviceContext device, ColorLightHttpRequest request) {
        String host = device.getIp();
        int port = device.getPort() > 0 ? device.getPort() : VendorDefaultPort.COLOR_LIGHT_STANDARD.getPort();
        String target = host + ":" + port;
        Duration timeout = Duration.ofSeconds(10);
        try {
            byte[] reqBytes = codec.encode(request);
            if (log.isDebugEnabled()) {
                log.debug("[{}] >>> transport request hex dump ({} bytes): {}",
                        device.getVendor(), reqBytes.length,
                        LittleEndianByteBufUtils.toHex(reqBytes));
            }
            byte[] respBytes = transport.sendAndReceive(device, reqBytes, timeout)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (log.isDebugEnabled()) {
                log.debug("[{}] >>> transport response hex dump ({} bytes): {}",
                        device.getVendor(), respBytes.length,
                        LittleEndianByteBufUtils.toHex(respBytes));
            }
            return codec.decode(respBytes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] HTTP 请求中断", target);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("[{}] HTTP 请求失败: {}", target, cause.getMessage());
            return null;
        } catch (TimeoutException e) {
            log.error("[{}] HTTP 请求超时", target);
            return null;
        }
    }

    private ColorLightAccount lookupAccount(DeviceContext device) {
        if (device == null) return ColorLightAccount.DEFAULT;
        return credentialStore.get(device.getDeviceId());
    }

    private <T> T parseJson(ColorLightHttpResponse response, Class<T> targetType) {
        if (response == null || response.getBody() == null || response.getBody().length == 0) return null;
        try {
            return JsonCustomMapper.get().readValue(response.getBody(), targetType);
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
