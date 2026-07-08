package com.gateway.device.protocol.adapter.jetfileii.standard.handler;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;

import java.time.Duration;

/**
 * 简单命令模板 —— 适用于 构建请求→发送→接收响应→映射结果 的单请求命令。
 *
 * <p>子类仅需实现 {@link #buildRequest(DeviceContext, CommandParams)}。</p>
 *
 * @param <P> 本能力对应的参数类型
 */
public abstract class AbstractSimpleJetFileIIHandler<P extends CommandParams>
        extends AbstractJetFileIIHandler<P> {

    protected AbstractSimpleJetFileIIHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public CommandResult execute(DeviceContext device, P params) {
        JetFileIIRequest req = buildRequest(device, params);
        Duration timeout = req.isNeedReply() ? getTimeout() : Duration.ofMillis(GatewayTimeoutConstants.DEVICE_OPERATION_NO_REPLY_MS);
        return messaging().executeSimple(transport(), device, req, timeout);
    }

    /**
     * 子类实现：根据 params 构建 JetFileII 协议请求对象
     */
    protected abstract JetFileIIRequest buildRequest(DeviceContext device, P params);
}
