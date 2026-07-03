package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.operate;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.*;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;

/**
 * 电源控制处理器 —— 立即重启终端。
 *
 * <p>SDK: {@code nvSetReBootTaskAsync}（需 loginType=1 系统设置登录）
 * <br>参数: {@code {"sn":"...","taskInfo":{"type":"REBOOT","source":{"type":0,"platform":2},"executionType":"IMMEDIATELY","reason":"gateway command"}}}</p>
 */
@Slf4j
public class NovaViplexCorePowerControlHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCorePowerControlHandler(ViplexCoreChannel channel,
                                             ViplexProgramPipeline pipeline,
                                             NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.POWER_CONTROL_REBOOT;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        long start = System.currentTimeMillis();
        String sn = device.getSn();

        // 重启需要系统设置级登录（loginType=1），常规发现登录（loginType=0）权限不足
        if (!channel().isLoggedIn(sn, LoginType.SYSTEM)) {
            CommandResult loginResult = ensureSystemLogin(sn);
            if (!loginResult.isSuccess()) {
                return loginResult;
            }
        }

        String jsonParams = ViplexCoreJsonBuilder.buildRebootJson(sn);

        ViplexResponse rebootResp = channel()
                .execute(SdkFunction.NV_SET_REBOOT_TASK_ASYNC, jsonParams, getTimeout());

        long cost = System.currentTimeMillis() - start;
        if (rebootResp.isTimeout()) {
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.TIMEOUT)
                    .message("重启超时").costMillis(cost).build();
        }
        if (!rebootResp.isSuccess()) {
            log.warn("重启失败 SN={} code={} data={}", sn, rebootResp.getCode(), rebootResp.getData());
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                    .message(String.format("重启失败: code=%s, %s", rebootResp.getCode(), rebootResp.getData()))
                    .costMillis(cost).build();
        }
        log.info("重启成功 SN={}", sn);
        return CommandResult.builder()
                .success(true).code(StandardErrorCode.SUCCESS)
                .message("重启指令已发送").costMillis(cost).build();
    }

    /**
     * 确保系统设置级登录（loginType=1），使用已知凭据。
     */
    private CommandResult ensureSystemLogin(String sn) {
        ViplexCoreAccount account = channel().getKnownAccount(sn);
        if (account == null) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "未找到设备登录凭据");
        }

        ViplexResponse resp = channel()
                .login(sn, account.getAccountId(), account.getPassword(),
                        getTimeout(), LoginType.SYSTEM);

        if (resp.isTimeout()) {
            return CommandResult.timeout();
        }
        if (!resp.isSuccess()) {
            log.warn("系统设置登录失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("系统设置登录失败: %s", resp.getData()));
        }
        log.info("系统设置登录成功 SN={}", sn);
        return CommandResult.success();
    }
}
