package com.gateway.device.protocol.adapter.jetfileii.standard.handler;

import com.gateway.device.protocol.api.CapabilityHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * JetFileII 能力处理器抽象基类 —— 纯基类，无模板方法。
 *
 * <p>提供 {@link JetFileIIMessaging}（编解码+传输门面）和 {@link DeviceTransport}（传输层），
 * 子类自行实现 {@link #execute(DeviceContext, CommandParams)}。
 * 简单单请求命令请继承 {@link AbstractSimpleJetFileIIHandler}。</p>
 *
 * @param <P> 本能力对应的参数类型
 */
@Slf4j
public abstract class AbstractJetFileIIHandler<P extends CommandParams> implements CapabilityHandler<P> {

    private final JetFileIIMessaging messaging;
    private final DeviceTransport transport;

    protected AbstractJetFileIIHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        this.messaging = messaging;
        this.transport = transport;
    }

    /**
     * 消息门面（编解码+传输）
     */
    protected JetFileIIMessaging messaging() {
        return messaging;
    }

    /**
     * 传输层
     */
    protected DeviceTransport transport() {
        return transport;
    }

    /**
     * 创建 FileTransfer 实例，自动从设备属性读取 gg/uu
     */
    protected FileTransfer createFileTransfer(DeviceContext device) {
        return messaging.createFileTransfer(transport, device);
    }

    /**
     * 命令超时，子类可按需覆盖。
     * 默认 5 秒（JetFileII 通用回复超时）。
     */
    protected Duration getTimeout() {
        return Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_QUICK_MS);
    }

    @Override
    public abstract CommandResult execute(DeviceContext device, P params);
}
