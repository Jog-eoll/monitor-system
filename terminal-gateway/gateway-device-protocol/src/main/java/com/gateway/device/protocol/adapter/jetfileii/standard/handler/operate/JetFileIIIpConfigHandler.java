package com.gateway.device.protocol.adapter.jetfileii.standard.handler.operate;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.sys.ConfigSysLayout;
import com.gateway.device.protocol.base.jetfileii.standard.sys.IpConfigEditor;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.DeviceValidator;
import com.gateway.device.protocol.common.IpConfigDefaults;
import com.gateway.device.protocol.common.NetworkValidator;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.IpConfigParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * JetFileII 设备 IP 配置处理器 —— 在线推送模式。
 *
 * <p>从设备下载 CONFIG.SYS，在内存中应用 IP/掩码/网关/DNS 修改，
 * 将修改后的 CONFIG.SYS 写回设备。</p>
 *
 * <p>模式由 {@code IpConfigParams.ip} 决定：
 * <ul>
 *   <li>提供 {@code ip} → 静态 IP 模式，自动补全掩码/网关/DNS</li>
 *   <li>{@code ip=null} → DHCP 模式，仅切换 CONFIG.SYS 标志位</li>
 * </ul></p>
 */
@Slf4j
public class JetFileIIIpConfigHandler extends AbstractJetFileIIHandler<IpConfigParams> {

    private final IpConfigEditor editor;

    public JetFileIIIpConfigHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
        this.editor = new IpConfigEditor();
    }

    @Override
    public DeviceCapability<IpConfigParams> capability() {
        return CommonDeviceCapability.DEVICE_NETWORK_IP_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, IpConfigParams params) {
        ImmutablePair<Boolean, CommandResult> pair = DeviceValidator.requireSingleDevice(device, params.getDeviceId());
        if (!pair.getLeft()) {
            return pair.getRight();
        }

        final boolean useStaticIp = StringUtils.isNotBlank(params.getIp());

        IpConfigParams resolvedParams;
        if (useStaticIp) {
            try {
                resolvedParams = IpConfigDefaults.apply(params);
                validateIpFields(resolvedParams);
            } catch (IllegalArgumentException e) {
                return CommandResult.failure(StandardErrorCode.INVALID_PARAM, e.getMessage());
            }
        } else {
            resolvedParams = params;
        }

        try {
            FileTransfer ft = createFileTransfer(device);
            byte[] original = editor.readConfig(ft);

            String mac = device.getMacAddr() != null ? device.getMacAddr() : "?";
            String origIp = ConfigSysLayout.bytesToIpLE(original, ConfigSysLayout.OFF_IP);
            String origMask = ConfigSysLayout.bytesToIpLE(original, ConfigSysLayout.OFF_NETMASK);
            String origGw = ConfigSysLayout.bytesToIpLE(original, ConfigSysLayout.OFF_GATEWAY);
            String origDns = ConfigSysLayout.bytesToIpLE(original, ConfigSysLayout.OFF_DNS);
            boolean origDhcp = ConfigSysLayout.isDhcp(original);

            log.info("[{}] MAC={} 修改前: mode={} ip={} mask={} gateway={} dns={}",
                    device.getIp(), mac,
                    origDhcp ? "DHCP" : "静态", origIp, origMask, origGw, origDns);

            byte[] modified = editor.applyConfig(original, resolvedParams);
            editor.writeConfig(ft, modified);

            String newIp = ConfigSysLayout.bytesToIpLE(modified, ConfigSysLayout.OFF_IP);
            String newMask = ConfigSysLayout.bytesToIpLE(modified, ConfigSysLayout.OFF_NETMASK);
            String newGw = ConfigSysLayout.bytesToIpLE(modified, ConfigSysLayout.OFF_GATEWAY);
            String newDns = ConfigSysLayout.bytesToIpLE(modified, ConfigSysLayout.OFF_DNS);
            boolean newDhcp = ConfigSysLayout.isDhcp(modified);

            log.info("[{}] MAC={} 修改后: mode={} ip={} mask={} gateway={} dns={}",
                    device.getIp(), mac,
                    newDhcp ? "DHCP" : "静态", newIp, newMask, newGw, newDns);

            return CommandResult.success("IP 配置已生效");

        } catch (Exception e) {
            log.error("[{}] IP 配置失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.TRANSPORT_ERROR, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // 校验
    // ════════════════════════════════════════════════════════════

    private void validateIpFields(IpConfigParams params) {
        if (params.getIp() != null) NetworkValidator.requireIpv4(params.getIp(), "IP");
        if (params.getMask() != null) NetworkValidator.requireIpv4(params.getMask(), "子网掩码");
        if (params.getGateway() != null) NetworkValidator.requireIpv4(params.getGateway(), "网关");
        if (params.getDns1() != null) NetworkValidator.requireIpv4(params.getDns1(), "DNS1");
        if (params.getDns2() != null) NetworkValidator.requireIpv4(params.getDns2(), "DNS2");
    }

}
