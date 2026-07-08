package com.gateway.device.protocol.adapter.novastar.viplexcore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.api.DeviceDiscoveryProvider;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.base.novastar.viplexcore.*;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NovaStar ViplexCore SDK 设备发现提供者。
 *
 * <p>SDK 的 {@code nvSearchTerminalAsync} 通过 UDP 广播发现设备，返回 JSON 设备列表。
 * 与原始 Avon 协议广播发现（{@code NovaStandardDiscoveryProvider}）路径不同，
 * 本发现方式由 SDK 管理协议，无需构建/解析原始字节报文。</p>
 *
 * <p>发现流程：搜索全部设备 → 依次尝试账号登录 → 取固件信息 → 构建结果。</p>
 */
@Slf4j
public class NovaViplexCoreDiscoveryProvider implements DeviceDiscoveryProvider {

    private final ViplexCoreChannel channel;
    private final List<ViplexCoreAccount> accounts;

    public NovaViplexCoreDiscoveryProvider(ViplexCoreChannel channel,
                                           List<ViplexCoreAccount> accounts) {
        this.channel = channel;
        this.accounts = (CollectionUtils.isNotEmpty(accounts))
                ? accounts
                : Collections.singletonList(defaultAccount());
    }

    private static ViplexCoreAccount defaultAccount() {
        return ViplexCoreAccount.DEFAULT;
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_VIPLEX_CORE;
    }

    @Override
    public byte[] buildBroadcastRequest() {
        return new byte[0];
    }

    @Override
    public DiscoveredDevice parseReply(byte[] data, String senderIp, int senderPort) {
        return null;
    }

    @Override
    public List<String> identityKeys() {
        return Collections.singletonList("sn");
    }

    @Override
    public List<DiscoveredDevice> discover(int timeoutMs) {
        if (!channel.isInitialized()) {
            log.warn("ViplexCore SDK 未初始化，跳过发现");
            return Collections.emptyList();
        }

        Duration timeout = Duration.ofMillis(timeoutMs);

        // 1. 搜索所有设备
        ViplexResponse searchResp = channel.searchAllDevices(timeout);
        if (!searchResp.isSuccess()) {
            log.debug("ViplexCore 搜索无结果: {}", searchResp.getData());
            return Collections.emptyList();
        }

        JsonNode result = searchResp.dataAsJson();
        JsonNode devicesArray = result != null ? result.get("devices") : null;
        if (devicesArray == null || !devicesArray.isArray() || devicesArray.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("ViplexCore 搜索响应 devices={}", devicesArray);

        // 2. 遍历每个设备，依次尝试账号登录
        List<DiscoveredDevice> discovered = new ArrayList<>();
        for (JsonNode devJson : devicesArray) {
            String sn = devJson.has("sn") ? devJson.get("sn").asText() : null;
            if (StringUtils.isEmpty(sn)) continue;

            String ip = devJson.has("ip") ? devJson.get("ip").asText() : "unknown";
            // 登录：先用已知账号，失败再轮询
            boolean loggedIn = false;

            ViplexCoreAccount known = channel.getKnownAccount(sn);
            if (known != null) {
                ViplexResponse resp = channel.login(
                        sn, known.getAccountId(), known.getPassword(), Duration.ofMillis(GatewayTimeoutConstants.DEVICE_LOGIN_TIMEOUT_MS), LoginType.MANAGEMENT);
                if (resp.isSuccess()) {
                    loggedIn = true;
                    log.debug("ViplexCore 已知账号登录成功 SN={}", sn);
                } else {
                    log.warn("ViplexCore 已知账号失效 SN={}, 重新轮询", sn);
                }
            }

            if (!loggedIn) {
                log.debug("ViplexCore 轮询登录设备 SN={} IP={}", sn, ip);
                String triedUser = known != null ? known.getAccountId() : null;
                for (ViplexCoreAccount account : accounts) {
                    if (triedUser != null && triedUser.equals(account.getAccountId())) {
                        log.debug("ViplexCore 跳过已尝试账号 SN={} user={}", sn, account.getAccountId());
                        continue;
                    }
                    ViplexResponse resp = channel.login(
                            sn, account.getAccountId(), account.getPassword(), Duration.ofMillis(GatewayTimeoutConstants.DEVICE_LOGIN_TIMEOUT_MS), LoginType.MANAGEMENT);
                    if (resp.isSuccess()) {
                        loggedIn = true;
                        channel.rememberAccount(sn, account);
                        log.debug("ViplexCore 登录成功 SN={} user={}", sn, account.getAccountId());
                        break;
                    }
                }
            }

            if (!loggedIn) {
                log.warn("ViplexCore 所有账号尝试失败 SN={}", sn);
                continue;
            }

            // 3. 获取固件信息
            JsonNode infoJson = fetchDeviceInfo(sn);
            if (infoJson == null) {
                log.warn("ViplexCore 获取设备信息失败 SN={}", sn);
                continue;
            }
            log.debug("ViplexCore 固件信息 SN={} data={}", sn, infoJson);

            // 4. 获取显示信息（宽高）
            Integer width = null;
            Integer height = null;
            JsonNode dispJson = fetchDisplayInfo(sn);
            if (dispJson != null) {
                // 响应结构: {taskArray:[{data:{width, height}, type:"DISPLAY_INFO"}]}
                JsonNode taskArray = dispJson.get("taskArray");
                if (taskArray != null && taskArray.isArray() && !taskArray.isEmpty()) {
                    JsonNode data = taskArray.get(0).get("data");
                    if (data != null) {
                        width = data.has("width") ? data.get("width").asInt() : null;
                        height = data.has("height") ? data.get("height").asInt() : null;
                    }
                }
            }
            log.debug("ViplexCore 发现设备 SN={} width={} height={}", sn, width, height);

            discovered.add(ViplexCoreDiscoveredDevice.builder()
                    .ip(ip)
                    .sourcePort(devJson.has("tcpPort") ? devJson.get("tcpPort").asInt() : 0)
                    .tcpPort(devJson.has("tcpPort") ? devJson.get("tcpPort").asInt() : 0)
                    .ftpPort(devJson.has("ftpPort") ? devJson.get("ftpPort").asInt() : 16602)
                    .sn(sn)
                    .productName(infoJson.has("productName") ? infoJson.get("productName").asText() : null)
                    .model(infoJson.has("model") ? infoJson.get("model").asText() : null)
                    .mac(infoJson.has("mac") ? ProtocolConstant.formatMac(infoJson.get("mac").asText()) : null)
                    .fpga(infoJson.has("fpga") ? infoJson.get("fpga").asText() : null)
                    .mainVersion(infoJson.has("mainVersion") ? infoJson.get("mainVersion").asText() : null)
                    .aliasName(devJson.has("aliasName") ? devJson.get("aliasName").asText() : null)
                    .platform(devJson.has("platform") ? devJson.get("platform").asText() : null)
                    .hasPassWord(devJson.has("hasPassWord") ? devJson.get("hasPassWord").asBoolean() : null)
                    .width(width)
                    .height(height)
                    .build());
        }

        log.debug("ViplexCore 发现 {} 个设备", discovered.size());
        return discovered;
    }

    private JsonNode fetchDeviceInfo(String sn) {
        ObjectNode params = JsonCustomMapper.get().createObjectNode();
        params.put("sn", sn);

        ViplexResponse resp = channel.execute(
                SdkFunction.NV_GET_FIRMWARE_INFOS_ASYNC, params.toString(),
                Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_DEFAULT_MS));

        return resp.isSuccess() ? resp.dataAsJson() : null;
    }

    private JsonNode fetchDisplayInfo(String sn) {
        ObjectNode params = JsonCustomMapper.get().createObjectNode();
        params.put("sn", sn);

        try {
            ViplexResponse resp = channel.execute(
                    SdkFunction.NV_GET_DISPLAY_INFO_ASYNC, params.toString(),
                    Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_DEFAULT_MS));
            if (resp.isSuccess()) {
                JsonNode data = resp.dataAsJson();
                log.debug("ViplexCore 显示信息 SN={} data={}", sn, data);
                if (data != null) {
                    JsonNode taskArray = data.get("taskArray");
                    boolean hasWH = taskArray != null && taskArray.isArray() && !taskArray.isEmpty()
                            && taskArray.get(0).has("data")
                            && (taskArray.get(0).get("data").has("width")
                            || taskArray.get(0).get("data").has("height"));
                    if (!hasWH) {
                        log.warn("ViplexCore 显示信息中无 width/height 字段 SN={} JSON={}",
                                sn, data);
                    }
                }
                return data;
            }
            log.warn("ViplexCore 获取显示信息失败 SN={}: {}", sn, resp.getData());
            return null;
        } catch (Exception e) {
            log.warn("ViplexCore 获取显示信息异常 SN={}: {}", sn, e.getMessage());
            return null;
        }
    }
}
