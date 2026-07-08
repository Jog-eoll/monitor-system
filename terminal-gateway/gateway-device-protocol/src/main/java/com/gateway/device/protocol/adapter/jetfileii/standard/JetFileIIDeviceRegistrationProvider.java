package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIAccount;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceAuthEntry;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd.LOGIN;
import static com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd.READ;
import static com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd.*;

/**
 * JetFileII 设备注册信息提供者 —— 独立于 {@code JetFileIIDeviceInfoGetHandler}。
 *
 * <p>发送 READ_SYSINFO 命令获取 CONFIG.SYS 全量数据，返回 {@code byte[]}，
 * 供 AutoDiscoveryService 注册 + DeviceInfoEnricher 解析使用。</p>
 */
@Slf4j
public class JetFileIIDeviceRegistrationProvider implements DeviceRegistrationProvider {

    private final JetFileIIMessaging messaging;
    private final DeviceTransport transport;
    private final JetFileIICredentialStore credentialStore;
    /**
     * -- SETTER --
     * 设置公共认证存储（由 Spring 配置注入）。
     */
    @Setter
    private volatile DeviceAuthStore authStore;

    public JetFileIIDeviceRegistrationProvider(JetFileIIMessaging messaging,
                                               DeviceTransport transport,
                                               JetFileIICredentialStore credentialStore) {
        this.messaging = messaging;
        this.transport = transport;
        this.credentialStore = credentialStore;
    }

    /**
     * 构建 20 字节登录 Arg: username + \0 + password，零填充。
     *
     * @param accountId 登录账号
     * @param password  登录密码
     * @return 20 字节定长 Arg
     */
    public static byte[] buildLoginArg(String accountId, String password) {
        byte[] buf = new byte[20];
        String user = accountId != null ? accountId : "";
        String pwd = password != null ? password : "";
        byte[] concat = (user + "\0" + pwd).getBytes(StandardCharsets.US_ASCII);
        int copyLen = Math.min(concat.length, 20);
        System.arraycopy(concat, 0, buf, 0, copyLen);
        return buf;
    }

    // ════════════════════════════════════════════════════
    // DeviceRegistrationProvider
    // ════════════════════════════════════════════════════

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.JET_FILE_II_STANDARD;
    }

    /**
     * 凭据遍历登录，尝试已知凭据和预配置列表。
     *
     * @return {@code true} 登录成功，{@code false} 所有凭据均失败
     */
    private boolean tryLoginWithCredentials(DeviceContext device, String deviceId,
                                            int gg, int uu, Duration timeout) {
        // 1. 已知凭据快速路径
        DeviceAuthEntry known = authStore.get(deviceId);
        if (known != null && known.getAccountId() != null) {
            if (tryLogin(device, known.getAccountId(), known.getPassword(), gg, uu, timeout)) {
                log.info("[{}] 已知凭据登录成功 user={}", device.getIp(), known.getAccountId());
                device.getAttributes().put(DeviceRegistrationProvider.ATTR_AUTH_ENTRY, known);
                return true;
            }
            log.debug("[{}] 已知凭据失效 user={}, 重新轮询预置账号", device.getIp(), known.getAccountId());
        }

        // 2. 预配置列表遍历
        for (JetFileIIAccount account : credentialStore.getPreconfigured()) {
            if (known != null && account.credentialsMatch(known.getAccountId(), null)) {
                continue; // 跳过已尝试失败的账号
            }
            if (tryLogin(device, account.getAccountId(), account.getPassword(), gg, uu, timeout)) {
                DeviceAuthEntry entry = DeviceAuthEntry.builder()
                        .accountId(account.getAccountId())
                        .password(account.getPassword())
                        .build();
                device.getAttributes().put(DeviceRegistrationProvider.ATTR_AUTH_ENTRY, entry);
                log.info("[{}] 预置账号登录成功 user={}", device.getIp(), account.getAccountId());
                return true;
            }
        }

        log.warn("[{}] 所有凭据登录失败", device.getIp());
        return false;
    }

    /**
     * 发送 LOGIN_LOGIN 命令并检查响应状态。
     */
    private boolean tryLogin(DeviceContext device, String accountId, String password,
                             int gg, int uu, Duration timeout) {
        byte[] arg = buildLoginArg(accountId, password);
        JetFileIIRequest request = JetFileIIRequest.builder()
                .mainCmd(LOGIN).subCmd(LOGIN_LOGIN)
                .arg(arg).destGg(gg).destUu(uu)
                .needReply(true).useCrc(true)
                .build();

        CommandResult result = messaging.executeSimple(transport, device, request, timeout);
        if (result.isSuccess()) {
            log.debug("[{}] 登录成功 user={}", device.getIp(), accountId);
            return true;
        }
        log.debug("[{}] 登录失败 user={} code={} msg={}",
                device.getIp(), accountId, result.getCode(), result.getMessage());
        return false;
    }

    /**
     * JetFileII 设备登出 —— 发送 LOGIN_LOGOUT (0x0A02)。
     *
     * @return {@code true} 登出成功，{@code false} 登出失败
     */
    @Override
    public boolean logout(DeviceContext device) {
        int gg = JetFileIIMessaging.resolveGg(device);
        int uu = JetFileIIMessaging.resolveUu(device);
        JetFileIIRequest request = JetFileIIRequest.builder()
                .mainCmd(LOGIN).subCmd(LOGIN_LOGOUT)
                .destGg(gg).destUu(uu)
                .needReply(true).useCrc(true)
                .build();
        CommandResult result = messaging.executeSimple(transport, device, request, Duration.ofMillis(GatewayTimeoutConstants.DEVICE_LOGOUT_TIMEOUT_MS));
        if (result.isSuccess()) {
            log.info("[{}] 登出成功", device.getIp());
            return true;
        }
        log.warn("[{}] 登出失败 code={} msg={}", device.getIp(), result.getCode(), result.getMessage());
        return false;
    }

    @Override
    public Object fetchRegistrationInfo(DeviceContext device) {
        // 1. 先执行登录认证（原 postRegister 逻辑，前置到注册流程内部）
        if (authStore != null && credentialStore != null) {
            String deviceId = device.getDeviceId();
            int gg = JetFileIIMessaging.resolveGg(device);
            int uu = JetFileIIMessaging.resolveUu(device);
            Duration timeout = Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS);
            if (!tryLoginWithCredentials(device, deviceId, gg, uu, timeout)) {
                log.warn("[{}] 注册信息获取失败: 登录认证未通过", device.getIp());
                return null;
            }
        }

        // 2. 发送 READ_SYSINFO 获取设备注册信息
        int gg = JetFileIIMessaging.resolveGg(device);
        int uu = JetFileIIMessaging.resolveUu(device);
        JetFileIIRequest request = JetFileIIRequest.builder()
                .mainCmd(READ).subCmd(READ_SYSINFO)
                .destGg(gg).destUu(uu)
                .needReply(true).build();

        byte[] payload = messaging.encode(request);
        Duration timeout = Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS);

        try {
            byte[] response = transport.sendAndReceive(device, payload, timeout)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            PacketMessage pkt = messaging.decode(response);
            if (pkt == null) {
                log.warn("[{}] 注册信息获取失败: 无法解析响应", device.getIp());
                return null;
            }
            if (pkt.isStatusReply()) {
                log.warn("[{}] 注册信息获取失败: 设备返回错误 0x{}",
                        device.getIp(), Integer.toHexString(pkt.getStatusCode() & 0xFFFF));
                return null;
            }
            return pkt.getData();
        } catch (Exception e) {
            log.error("[{}] 注册信息获取异常: {}", device.getIp(), e.getMessage());
            return null;
        }
    }
}
